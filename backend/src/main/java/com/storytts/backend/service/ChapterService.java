package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ReadingProgress;
import com.storytts.backend.domain.Story;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ chương truyện.
 * Mọi đường vào nội dung chương đều gọi {@link AccessControlService} trước.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final StoryRepository storyRepository;
    private final AudioFileRepository audioFileRepository;
    private final AccessControlService accessControlService;

    // Đọc thẳng repository chứ không qua ReadingProgressService: lớp đó đã phụ
    // thuộc vào ChapterService, gọi ngược lại sẽ thành vòng phụ thuộc bean.
    private final ReadingProgressRepository progressRepository;
    private final CurrentUserService currentUserService;

    /**
     * Danh sách chương của một truyện.
     * Ai cũng xem được danh sách, nhưng chương không đủ quyền sẽ có {@code locked = true}
     * để frontend hiển thị icon 🔒 kèm mức yêu cầu.
     */
    @Transactional(readOnly = true)
    public List<ChapterSummaryDto> listByStory(Long storyId) {
        if (!storyRepository.existsById(storyId)) {
            throw ResourceNotFoundException.of("truyện", storyId);
        }
        List<Chapter> chapters = chapterRepository.findByStoryIdOrderByChapterNumberAsc(storyId);
        if (chapters.isEmpty()) {
            return List.of();
        }

        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();
        Set<Long> withAudio = new HashSet<>(audioFileRepository.findChapterIdsWithReadyAudio(chapterIds));

        return chapters.stream().map(chapter -> toSummary(chapter, storyId, withAudio.contains(chapter.getId()))).toList();
    }

    /**
     * Lấy nội dung đầy đủ của một chương.
     * Không đủ quyền → ném {@link com.storytts.backend.exception.ChapterLockedException} (HTTP 403)
     * và nội dung chương không bao giờ được đưa vào response.
     */
    @Transactional
    public ChapterDetailDto getDetail(Long chapterId) {
        Chapter chapter = findDetailEntity(chapterId);

        // >>> Chốt chặn quyền truy cập <<<
        accessControlService.requireAccess(chapter);

        Story story = chapter.getStory();

        // Mở chương ra chưa phải là đã đọc nó. Lượt đọc được tính khi người đọc
        // đọc hết chương và chuyển sang chương sau — xem
        // ReadingProgressService.markCompleted. Nhờ vậy F5 không còn cộng lượt,
        // và mỗi người chỉ được tính một lượt cho mỗi chương.
        Long previousId = chapterRepository
                .findPrevious(story.getId(), chapter.getChapterNumber())
                .map(Chapter::getId).orElse(null);
        Long nextId = chapterRepository
                .findNext(story.getId(), chapter.getChapterNumber())
                .map(Chapter::getId).orElse(null);

        boolean hasUpload = audioFileRepository
                .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.UPLOAD, AudioStatus.READY)
                .isPresent();
        boolean hasTts = audioFileRepository
                .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.TTS, AudioStatus.READY)
                .isPresent();

        // Khách không sinh câu truy vấn nào — Optional rỗng là dừng ngay tại đây.
        Integer audioPosition = currentUserService.currentUserId()
                .flatMap(userId -> progressRepository.findByUserIdAndChapterId(userId, chapterId))
                .map(ReadingProgress::getAudioPositionSeconds)
                .orElse(null);

        return new ChapterDetailDto(
                chapter.getId(),
                story.getId(),
                story.getTitle(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                chapter.getViewCount(),
                chapter.getCreatedAt(),
                previousId,
                nextId,
                hasUpload,
                hasTts,
                audioPosition);
    }

    // ==================== Phía Admin ====================

    @Transactional
    public ChapterSummaryDto create(Long storyId, ChapterRequest request) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        int number = request.chapterNumber() != null
                ? request.chapterNumber()
                : chapterRepository.findMaxChapterNumber(storyId) + 1;

        if (chapterRepository.existsByStoryIdAndChapterNumber(storyId, number)) {
            throw new BadRequestException("Truyện này đã có chương số %d.".formatted(number));
        }

        Chapter chapter = Chapter.builder()
                .story(story)
                .title(request.title().trim())
                .content(request.content())
                .chapterNumber(number)
                // Mức khóa chương do Admin quyết định.
                .accessLevel(request.accessLevel())
                .build();

        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin tạo chương {} của truyện {} với mức truy cập {}",
                number, storyId, request.accessLevel());
        return toSummary(saved, storyId, false);
    }

    @Transactional
    public ChapterSummaryDto update(Long chapterId, ChapterRequest request) {
        Chapter chapter = findDetailEntity(chapterId);
        Long storyId = chapter.getStory().getId();

        if (request.chapterNumber() != null
                && !request.chapterNumber().equals(chapter.getChapterNumber())) {
            if (chapterRepository.existsByStoryIdAndChapterNumber(storyId, request.chapterNumber())) {
                throw new BadRequestException(
                        "Truyện này đã có chương số %d.".formatted(request.chapterNumber()));
            }
            chapter.setChapterNumber(request.chapterNumber());
        }

        chapter.setTitle(request.title().trim());
        chapter.setContent(request.content());
        chapter.setAccessLevel(request.accessLevel());

        Chapter saved = chapterRepository.save(chapter);
        boolean hasAudio = audioFileRepository.existsByChapterIdAndStatus(chapterId, AudioStatus.READY);
        return toSummary(saved, storyId, hasAudio);
    }

    @Transactional
    public void delete(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> ResourceNotFoundException.of("chương", chapterId));
        chapterRepository.delete(chapter);
    }

    /** Đổi riêng mức khóa của một chương — thao tác nhanh trong trang quản trị. */
    @Transactional
    public ChapterSummaryDto changeAccessLevel(Long chapterId, AccessLevel accessLevel) {
        Chapter chapter = findDetailEntity(chapterId);
        chapter.setAccessLevel(accessLevel);
        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin đổi mức truy cập chương {} thành {}", chapterId, accessLevel);
        boolean hasAudio = audioFileRepository.existsByChapterIdAndStatus(chapterId, AudioStatus.READY);
        return toSummary(saved, chapter.getStory().getId(), hasAudio);
    }

    /**
     * Đổi mức khóa cho nhiều chương cùng lúc.
     *
     * <p>Một giao dịch duy nhất: đặt mức khóa cho nửa danh sách rồi hỏng giữa chừng sẽ
     * để lại một truyện có chương khóa lẫn lộn, mà nhìn vào không đoán được là cố ý
     * hay do lỗi.
     *
     * @return số chương đã đổi
     */
    @Transactional
    public int changeAccessLevelBulk(List<Long> chapterIds, AccessLevel accessLevel) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            throw new BadRequestException("Chưa chọn chương nào.");
        }

        List<Chapter> chapters = chapterRepository.findAllById(chapterIds);
        if (chapters.size() != chapterIds.size()) {
            throw new ResourceNotFoundException("Có chương trong danh sách không còn tồn tại.");
        }

        chapters.forEach(chapter -> chapter.setAccessLevel(accessLevel));
        chapterRepository.saveAll(chapters);

        log.info("Admin đổi mức truy cập {} chương thành {}", chapters.size(), accessLevel);
        return chapters.size();
    }

    // ==================== Hàm hỗ trợ ====================

    @Transactional(readOnly = true)
    public Chapter findDetailEntity(Long chapterId) {
        return chapterRepository.findDetailById(chapterId)
                .orElseThrow(() -> ResourceNotFoundException.of("chương", chapterId));
    }

    private ChapterSummaryDto toSummary(Chapter chapter, Long storyId, boolean hasAudio) {
        boolean locked = !accessControlService.canAccess(chapter);
        return new ChapterSummaryDto(
                chapter.getId(),
                storyId,
                chapter.getTitle(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                locked,
                hasAudio,
                chapter.getViewCount(),
                chapter.getCreatedAt());
    }
}
