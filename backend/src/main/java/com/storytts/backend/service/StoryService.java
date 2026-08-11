package com.storytts.backend.service;

import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.story.StoryDetailDto;
import com.storytts.backend.dto.story.StoryRequest;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Nghiệp vụ truyện: danh sách có lọc/tìm kiếm/phân trang và CRUD phía Admin (mục 4.2, 4.3). */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    // Repository chứ không phải ReadingProgressService: chỗ này chỉ cần hai truy vấn
    // đọc thuần, mà service kia lại gọi ngược sang đây khi dựng trang chi tiết truyện.
    private final ReadingProgressRepository progressRepository;
    private final CurrentUserService currentUserService;
    private final ChapterService chapterService;
    private final GenreService genreService;
    private final AuthorService authorService;
    private final RatingCommentService ratingCommentService;
    private final FavoriteService favoriteService;
    private final ReadingProgressService readingProgressService;

    /**
     * Danh sách truyện: tìm theo tên/tác giả, lọc thể loại, sắp xếp.
     *
     * @param sort một trong: {@code newest} (mặc định), {@code popular}, {@code oldest}, {@code title}
     */
    @Transactional(readOnly = true)
    public PageResponse<StorySummaryDto> search(String keyword, Long genreId, StoryStatus status,
                                                String sort, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                resolveSort(sort));

        Page<Story> result = storyRepository.search(
                keyword == null ? null : keyword.trim(), genreId, status, pageable);

        Map<Long, Long> chapterCounts = countChapters(result.getContent());
        return PageResponse.from(result,
                story -> StorySummaryDto.from(story, chapterCounts.getOrDefault(story.getId(), 0L)));
    }

    /**
     * Gợi ý truyện tương tự dựa trên thể loại người dùng đã đọc/nghe (mục 4.3 của đề bài).
     *
     * <p>Ba bước: lấy thể loại của những truyện có trong lịch sử đọc gần đây → loại bỏ
     * chính những truyện đã đọc → còn lại sắp theo lượt xem. Ai chưa đọc gì thì không có
     * căn cứ nào để gợi ý, trả về danh sách rỗng và giao diện tự ẩn khu vực đó đi — bịa ra
     * gợi ý bằng truyện phổ biến chung sẽ trùng luôn với mục "Được xem nhiều" ngay bên cạnh.
     */
    @Transactional(readOnly = true)
    public List<StorySummaryDto> recommendations(int limit) {
        Long userId = currentUserService.currentUserId().orElse(null);
        if (userId == null) {
            return List.of();
        }

        List<Long> genreIds = progressRepository.findRecentGenreIds(userId);
        if (genreIds.isEmpty()) {
            return List.of();
        }

        // Truy vấn dùng NOT IN, mà NOT IN () rỗng là lỗi cú pháp SQL. Người mới đọc
        // truyện chưa có thể loại nào thì đã thoát ở trên, nhưng danh sách truyện đã
        // đọc vẫn có thể rỗng về lý thuyết, nên chèn một id không bao giờ tồn tại.
        List<Long> readStoryIds = new ArrayList<>(progressRepository.findReadStoryIds(userId));
        if (readStoryIds.isEmpty()) {
            readStoryIds.add(-1L);
        }

        List<Story> stories = storyRepository.findRecommendations(
                genreIds, readStoryIds, PageRequest.of(0, Math.min(Math.max(limit, 1), 12)));

        Map<Long, Long> chapterCounts = countChapters(stories);
        return stories.stream()
                .map(story -> StorySummaryDto.from(story, chapterCounts.getOrDefault(story.getId(), 0L)))
                .toList();
    }

    /**
     * Trang chi tiết truyện: thông tin + danh sách chương kèm cờ khóa cho người dùng hiện tại,
     * kèm đánh giá, trạng thái yêu thích và tiến độ đọc.
     * Nội dung chương KHÔNG nằm trong kết quả này.
     *
     * <p>Gom tất cả vào một request để trang chi tiết không phải gọi bốn API rồi ghép ở
     * frontend; phần tương tác đều rỗng một cách vô hại với Khách chưa đăng nhập.
     */
    @Transactional
    public StoryDetailDto getDetail(Long storyId) {
        Story story = storyRepository.findDetailById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        storyRepository.incrementViewCount(storyId);

        List<ChapterSummaryDto> chapters = chapterService.listByStory(storyId);
        long chapterCount = chapters.size();

        return new StoryDetailDto(
                StorySummaryDto.from(story, chapterCount),
                chapters,
                ratingCommentService.summary(storyId),
                favoriteService.status(storyId),
                List.copyOf(readingProgressService.completedChapterIds(storyId)),
                readingProgressService.resumeChapterId(storyId).orElse(null));
    }

    @Transactional(readOnly = true)
    public StorySummaryDto getSummary(Long storyId) {
        Story story = storyRepository.findDetailById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));
        return StorySummaryDto.from(story, chapterRepository.countByStoryId(storyId));
    }

    // ==================== Phía Admin ====================

    @Transactional
    public StorySummaryDto create(StoryRequest request) {
        Story story = Story.builder()
                .title(request.title().trim())
                .coverImage(blankToNull(request.coverImage()))
                .description(request.description())
                .status(request.status() == null ? StoryStatus.ONGOING : request.status())
                .build();
        applyAuthorAndGenre(story, request);
        return StorySummaryDto.from(storyRepository.save(story), 0L);
    }

    @Transactional
    public StorySummaryDto update(Long storyId, StoryRequest request) {
        Story story = findEntity(storyId);
        story.setTitle(request.title().trim());
        story.setCoverImage(blankToNull(request.coverImage()));
        story.setDescription(request.description());
        if (request.status() != null) {
            story.setStatus(request.status());
        }
        applyAuthorAndGenre(story, request);
        Story saved = storyRepository.save(story);
        return StorySummaryDto.from(saved, chapterRepository.countByStoryId(storyId));
    }

    /** Xóa truyện kéo theo toàn bộ chương và bản ghi audio của nó (cascade ở entity). */
    @Transactional
    public void delete(Long storyId) {
        Story story = findEntity(storyId);
        log.info("Admin xóa truyện id={} title='{}'", storyId, story.getTitle());
        storyRepository.delete(story);
    }

    @Transactional(readOnly = true)
    public Story findEntity(Long storyId) {
        return storyRepository.findById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));
    }

    // ==================== Hàm hỗ trợ ====================

    private void applyAuthorAndGenre(Story story, StoryRequest request) {
        if (request.authorId() != null) {
            story.setAuthor(authorService.findEntity(request.authorId()));
        } else if (request.authorName() != null && !request.authorName().isBlank()) {
            story.setAuthor(authorService.findOrCreateByName(request.authorName()));
        }

        if (request.genreId() != null) {
            story.setGenre(genreService.findEntity(request.genreId()));
        } else if (request.genreName() != null && !request.genreName().isBlank()) {
            story.setGenre(genreService.findOrCreateByName(request.genreName()));
        }

        if (story.getAuthor() == null) {
            throw new BadRequestException("Vui lòng chọn hoặc nhập tên tác giả.");
        }
        if (story.getGenre() == null) {
            throw new BadRequestException("Vui lòng chọn hoặc nhập thể loại.");
        }
    }

    private Map<Long, Long> countChapters(List<Story> stories) {
        if (stories.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = stories.stream().map(Story::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : chapterRepository.countGroupedByStoryIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Sort resolveSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sort.trim().toLowerCase()) {
            case "popular" -> Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
