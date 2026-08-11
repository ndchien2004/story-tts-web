package com.storytts.backend.service;

import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.dto.admin.BatchTtsResultDto;
import com.storytts.backend.dto.admin.ChapterAudioDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.service.tts.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nhìn toàn bộ kho audio từ phía quản trị, và tạo audio hàng loạt.
 *
 * <p>Trước đây muốn biết chương nào còn thiếu audio thì phải mở từng truyện ra đếm
 * bằng mắt. Ở đây câu hỏi đó là một bộ lọc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudioAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Trần cho một lệnh chạy hàng loạt.
     * <p>
     * Mỗi chương là một lần gọi tới nhà cung cấp TTS, mà nhà cung cấp nào cũng có
     * giới hạn tần suất. Chặn ở đây để một cú bấm "chọn tất cả" trên truyện 200
     * chương không nướng sạch hạn mức trong vài giây.
     */
    private static final int MAX_BATCH = 20;

    private final ChapterRepository chapterRepository;
    private final AudioFileRepository audioFileRepository;
    private final TtsService ttsService;

    /** Giọng đọc của nhà cung cấp đang được cấu hình; rỗng nghĩa là chưa cấu hình cái nào. */
    public List<VoiceOptionDto> voices() {
        return ttsService.availableVoices();
    }

    /**
     * Danh sách chương kèm tình trạng audio.
     *
     * @param withAudio null = tất cả, true = đã có audio, false = còn thiếu
     */
    @Transactional(readOnly = true)
    public PageResponse<ChapterAudioDto> chapters(Long storyId, Boolean withAudio, int page, int size) {
        Page<Chapter> result = chapterRepository.searchForAudioAdmin(
                storyId,
                withAudio,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));

        // Ba trạng thái audio hỏi gộp một lần cho cả trang, thay vì ba câu truy vấn
        // nhân với mỗi dòng.
        List<Long> chapterIds = result.getContent().stream().map(Chapter::getId).toList();
        Set<Long> ready = new HashSet<>(audioFileRepository.findChapterIdsWithReadyAudio(idsOrEmpty(chapterIds)));
        Set<Long> processing = statusSet(chapterIds, AudioStatus.PROCESSING);
        Set<Long> failed = statusSet(chapterIds, AudioStatus.FAILED);

        return PageResponse.from(result, chapter -> ChapterAudioDto.from(
                chapter,
                ready.contains(chapter.getId()),
                processing.contains(chapter.getId()),
                failed.contains(chapter.getId())));
    }

    /**
     * Xếp hàng tạo audio cho nhiều chương.
     *
     * <p>Từng chương một, và lỗi của chương này không dừng chương kia: kết quả trả về
     * một dòng cho mỗi chương để Admin biết chính xác cái nào đã nhận, cái nào không
     * và vì sao. Việc tạo thật diễn ra ở luồng nền, đúng luồng mà nút "Nghe bằng AI"
     * bên trang đọc vẫn dùng — không có đường đi thứ hai nào cho việc tạo audio.
     *
     * <p><b>Cố ý không có {@code @Transactional}.</b> Mỗi lần gọi
     * {@link TtsService#requestForChapter} tự mở giao dịch riêng của nó. Nếu bọc cả lô
     * trong một giao dịch chung thì một chương hỏng sẽ đánh dấu giao dịch đó là
     * rollback-only — bắt được ngoại lệ rồi chạy tiếp cũng vô ích, vì tới lúc commit
     * Spring vẫn ném {@code UnexpectedRollbackException} và cả lô cùng chết theo đúng
     * cái chương hỏng ấy.
     */
    public List<BatchTtsResultDto> generateBatch(List<Long> chapterIds, String voice, Integer speed) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            throw new BadRequestException("Chưa chọn chương nào.");
        }
        if (chapterIds.size() > MAX_BATCH) {
            throw new BadRequestException(
                    "Mỗi lượt chỉ tạo được tối đa %d chương, tránh vượt giới hạn của nhà cung cấp giọng đọc."
                            .formatted(MAX_BATCH));
        }

        return chapterIds.stream().map(chapterId -> {
            String title = chapterRepository.findById(chapterId)
                    .map(Chapter::getTitle)
                    .orElse("Chương #" + chapterId);
            try {
                var audio = ttsService.requestForChapter(chapterId, new TtsRequest(voice, speed));
                return new BatchTtsResultDto(chapterId, title, true,
                        "Trạng thái: " + audio.status());
            } catch (ResourceNotFoundException ex) {
                return new BatchTtsResultDto(chapterId, title, false, "Không tìm thấy chương.");
            } catch (RuntimeException ex) {
                log.warn("Tạo audio hàng loạt hỏng ở chương {}: {}", chapterId, ex.getMessage());
                return new BatchTtsResultDto(chapterId, title, false, ex.getMessage());
            }
        }).toList();
    }

    private Set<Long> statusSet(List<Long> chapterIds, AudioStatus status) {
        if (chapterIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(audioFileRepository.findChapterIdsByStatus(chapterIds, status));
    }

    /** JPA không nhận danh sách rỗng trong mệnh đề IN trên mọi phương ngữ. */
    private static List<Long> idsOrEmpty(List<Long> ids) {
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}
