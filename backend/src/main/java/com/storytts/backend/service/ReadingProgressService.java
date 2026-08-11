package com.storytts.backend.service;

import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ReadingProgress;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.interaction.ProgressDto;
import com.storytts.backend.dto.interaction.ProgressRequest;
import com.storytts.backend.dto.interaction.ShelfEntryDto;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tiến độ đọc/nghe từng chương (mục 4.3 và 4.4 của đề bài).
 *
 * <p>Bảng chỉ có cột {@code last_position} nên "đã đọc xong" được biểu diễn bằng
 * {@link #COMPLETE_POSITION}: mở chương ra là có bản ghi với vị trí 0 (phục vụ nút "Đọc tiếp"),
 * đọc hết thì vị trí lên 100 (phục vụ dấu tích "đã đọc"). Không cần thêm cột nào.
 */
@Service
@RequiredArgsConstructor
public class ReadingProgressService {

    /** Vị trí đánh dấu một chương đã đọc xong. */
    public static final int COMPLETE_POSITION = 100;

    private static final int MAX_RECENT = 20;

    private final ReadingProgressRepository progressRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterService chapterService;
    private final AccessControlService accessControlService;
    private final CurrentUserService currentUserService;

    /**
     * Ghi nhận vị trí đọc/nghe hiện tại, tạo bản ghi nếu chưa có.
     *
     * <p>Chương bị khóa không ghi được tiến độ: nếu không thì tiến độ trở thành một kênh
     * xác nhận sự tồn tại của nội dung mà người dùng chưa được phép chạm tới.
     */
    @Transactional
    public ProgressDto save(Long chapterId, ProgressRequest request) {
        User user = currentUserService.requireCurrentUser();
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        ReadingProgress progress = progressRepository
                .findByUserIdAndChapterId(user.getId(), chapterId)
                .orElseGet(() -> ReadingProgress.builder().user(user).chapter(chapter).build());

        if (request.lastPosition() != null) {
            // Tiến độ chỉ đi tới: quay lại đầu chương để xem lại không xóa dấu "đã đọc".
            progress.setLastPosition(Math.max(progress.getLastPosition(), request.lastPosition()));
        }
        if (request.audioPositionSeconds() != null) {
            progress.setAudioPositionSeconds(request.audioPositionSeconds());
        }

        return toDto(progressRepository.save(progress));
    }

    /** Đánh dấu chương đã đọc xong — gọi khi người dùng chuyển chương hoặc đọc/nghe hết. */
    @Transactional
    public ProgressDto markCompleted(Long chapterId) {
        return save(chapterId, new ProgressRequest(COMPLETE_POSITION, null));
    }

    /** Id các chương của một truyện mà người dùng hiện tại đã đọc xong. */
    @Transactional(readOnly = true)
    public Set<Long> completedChapterIds(Long storyId) {
        return currentUserService.currentUserId()
                .map(userId -> progressRepository.findByUserAndStory(userId, storyId).stream()
                        .filter(ReadingProgressService::isCompleted)
                        .map(progress -> progress.getChapter().getId())
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    /** Chương nên mở khi bấm "Đọc tiếp", hoặc rỗng nếu người dùng chưa đọc truyện này. */
    @Transactional(readOnly = true)
    public Optional<Long> resumeChapterId(Long storyId) {
        return currentUserService.currentUserId()
                .flatMap(userId -> progressRepository.findLatestByUserAndStory(userId, storyId))
                .map(progress -> progress.getChapter().getId());
    }

    /**
     * Tủ truyện: mỗi truyện một dòng, mới đọc nhất trước.
     *
     * <p>Bản ghi tiến độ đầu tiên gặp của mỗi truyện chính là chỗ đang đọc dở, vì
     * truy vấn đã sắp xếp mới nhất trước. Số chương đã đọc xong đếm ngay trên cùng
     * một lượt duyệt, chỉ còn tổng số chương là phải hỏi thêm — mỗi truyện một câu
     * đếm, giống cách trang yêu thích đang làm.
     */
    @Transactional(readOnly = true)
    public List<ShelfEntryDto> shelf() {
        Long userId = currentUserService.requireCurrentUser().getId();

        Map<Long, ReadingProgress> latestPerStory = new LinkedHashMap<>();
        Map<Long, Long> completedPerStory = new HashMap<>();

        for (ReadingProgress progress : progressRepository.findAllByUser(userId)) {
            Long storyId = progress.getChapter().getStory().getId();
            latestPerStory.putIfAbsent(storyId, progress);
            if (isCompleted(progress)) {
                completedPerStory.merge(storyId, 1L, Long::sum);
            }
        }

        return latestPerStory.entrySet().stream()
                .map(entry -> ShelfEntryDto.from(
                        entry.getValue(),
                        completedPerStory.getOrDefault(entry.getKey(), 0L),
                        chapterRepository.countByStoryId(entry.getKey())))
                .toList();
    }

    /** Lịch sử đọc/nghe gần đây, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<ProgressDto> recent(int limit) {
        Long userId = currentUserService.requireCurrentUser().getId();
        int size = Math.min(Math.max(limit, 1), MAX_RECENT);
        return progressRepository.findRecentByUser(userId, PageRequest.of(0, size)).stream()
                .map(this::toDto)
                .toList();
    }

    private static boolean isCompleted(ReadingProgress progress) {
        return progress.getLastPosition() >= COMPLETE_POSITION;
    }

    private ProgressDto toDto(ReadingProgress progress) {
        return ProgressDto.from(progress, isCompleted(progress));
    }
}
