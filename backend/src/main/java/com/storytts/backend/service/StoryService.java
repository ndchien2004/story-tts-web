package com.storytts.backend.service;

import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.domain.ViewType;
import com.storytts.backend.dto.admin.ContentDeletionDto;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.story.StoryDetailDto;
import com.storytts.backend.dto.story.StoryRankDto;
import com.storytts.backend.dto.story.StoryRequest;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.repository.ViewEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nghiệp vụ truyện: danh sách có lọc/tìm kiếm/phân trang và CRUD phía Admin (mục 4.2, 4.3). */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    private static final int MAX_PAGE_SIZE = 50;

    /** Trần cứng cho bảng xếp hạng: nó là một cột hẹp, không phải một trang danh sách. */
    private static final int MAX_RANK_SIZE = 20;

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final AudioFileRepository audioFileRepository;
    private final ViewEventRepository viewEventRepository;
    // Repository chứ không phải ReadingProgressService: chỗ này chỉ cần hai truy vấn
    // đọc thuần, mà service kia lại gọi ngược sang đây khi dựng trang chi tiết truyện.
    private final ReadingProgressRepository progressRepository;
    private final CurrentUserService currentUserService;
    private final ChapterService chapterService;
    private final PublicationService publicationService;
    private final StoredAudioCleanup storedAudioCleanup;
    private final ChapterRefundService chapterRefundService;
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
                keyword == null ? null : keyword.trim(), genreId, status,
                publicationService.canSeeUnpublished(), Instant.now(), pageable);

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

        // Gợi ý không bao giờ kèm bản nháp, kể cả cho quản trị viên: đây là một
        // dải trên trang chủ, không phải một màn hình quản trị.
        List<Story> stories = storyRepository.findRecommendations(
                genreIds, readStoryIds, Instant.now(),
                PageRequest.of(0, Math.min(Math.max(limit, 1), 12)));

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
        publicationService.requireVisible(story, "truyện", storyId);

        // Mở trang truyện không còn tính là một lượt xem. Bấm F5 mười lần từng
        // cộng mười lượt, nên con số đó không nói được truyện nào thật sự có
        // người đọc — mà "top truyện xem nhiều nhất" ở trang thống kê lại dựng
        // trên đúng nó. Lượt xem giờ được tính ở chỗ có bằng chứng là người ta
        // đã đọc: khi họ đọc xong một chương và chuyển sang chương tiếp theo.
        // Xem ReadingProgressService.markCompleted.

        // Trang đầu của danh sách chương, không phải toàn bộ. Một truyện dịch
        // 1.200 chương từng về trong một lần gọi ở đây; các trang sau lấy qua
        // GET /api/stories/{id}/chapters, đường vốn đã có sẵn.
        PageResponse<ChapterSummaryDto> chapters = chapterService.listByStory(
                storyId, null, true, 0, ChapterService.DEFAULT_PAGE_SIZE);

        return new StoryDetailDto(
                StorySummaryDto.from(story, chapters.totalElements()),
                chapters,
                ratingCommentService.summary(storyId),
                favoriteService.status(storyId),
                List.copyOf(readingProgressService.completedChapterIds(storyId)),
                audioFileRepository.countChaptersWithReadyAudio(storyId),
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
                .publishedAt(request.resolvePublishedAt(null))
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
        story.setPublishedAt(request.resolvePublishedAt(story.getPublishedAt()));
        applyAuthorAndGenre(story, request);
        Story saved = storyRepository.save(story);
        return StorySummaryDto.from(saved, chapterRepository.countByStoryId(storyId));
    }

    /**
     * Đổi riêng tình trạng xuất bản của một truyện.
     *
     * <p>Gỡ cả truyện xuống là một thao tác nặng — mọi chương của nó biến mất
     * theo, kể cả chương đã đăng — nên nó có đường riêng thay vì nấp trong form
     * sửa truyện, nơi người ta dễ bấm nhầm khi đang định sửa cái mô tả.
     */
    @Transactional
    public StorySummaryDto changePublication(Long storyId, boolean draft, Instant publishedAt) {
        Story story = findEntity(storyId);
        story.setPublishedAt(draft ? null : (publishedAt != null ? publishedAt : Instant.now()));

        Story saved = storyRepository.save(story);
        log.info("Admin đặt truyện {} sang trạng thái {} (mốc {})",
                storyId, saved.publishState(), saved.getPublishedAt());

        return StorySummaryDto.from(saved, chapterRepository.countByStoryId(storyId));
    }

    /** Xóa truyện kéo theo toàn bộ chương và bản ghi audio của nó (cascade ở entity). */
    /**
     * Xóa hẳn một truyện, kéo theo toàn bộ chương và audio của nó.
     *
     * <p>Cùng một câu chuyện với {@code ChapterService.delete}: trước migration
     * V12, một lượt bấm yêu thích hay một bình luận là đủ để lệnh xóa hỏng với
     * lỗi ràng buộc. Đường này còn dễ hỏng hơn vì nó đi qua ba khóa ngoại khác
     * nhau, và mỗi lần sửa một cái thì cái tiếp theo mới lộ ra.
     *
     * <p>File audio được dọn theo cả truyện trong một lần đọc, chứ không lặp qua
     * từng chương: một truyện nghìn chương sẽ là nghìn câu truy vấn cho một thao
     * tác vốn chỉ cần một.
     */
    @Transactional
    public ContentDeletionDto delete(Long storyId) {
        Story story = findEntity(storyId);
        log.info("Admin xóa truyện id={} title='{}'", storyId, story.getTitle());

        // Hoàn Xu trước khi có gì bị xóa: sau lệnh xóa thì không còn dòng quyền
        // nào để biết ai đã trả bao nhiêu cho chương nào.
        ChapterRefundService.Refunds refunds = chapterRefundService.refundStory(storyId);
        storedAudioCleanup.purgeStoryAfterCommit(storyId);
        storyRepository.delete(story);

        return new ContentDeletionDto(refunds.coins(), refunds.readers());
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

    /**
     * Bảng xếp hạng "nghe nhiều nhất" theo ngày / tuần / tháng, cho cột bên phải trang chủ.
     *
     * <p>Đếm trên {@code view_events} chứ không dùng {@code stories.view_count}: cột kia
     * là tổng cộng dồn từ ngày truyện được đăng, nên ba khoảng thời gian sẽ cho ra đúng
     * một thứ tự giống hệt nhau — mà cả điểm thú vị của bảng này nằm ở chỗ nó đổi.
     *
     * <p>Mốc bắt đầu cắt theo múi giờ máy chủ, giống biểu đồ ở trang quản trị: "hôm nay"
     * là từ 0 giờ sáng nay, không phải 24 tiếng gần nhất, vì đó mới là điều người xem
     * hiểu khi đọc chữ "ngày".
     *
     * @param period {@code day}, {@code week} hoặc {@code month}; giá trị lạ coi như {@code day}
     */
    @Transactional(readOnly = true)
    public List<StoryRankDto> topListened(String period, int limit) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        LocalDate from = switch (period == null ? "" : period.trim().toLowerCase()) {
            case "week" -> today.minusDays(6);
            case "month" -> today.minusDays(29);
            default -> today;
        };

        Instant since = from.atStartOfDay(zone).toInstant();
        int size = Math.min(Math.max(limit, 1), MAX_RANK_SIZE);

        // [storyId, số lượt] — thứ tự do truy vấn quyết định và phải giữ nguyên tới cuối.
        List<Object[]> ranked = viewEventRepository.rankStoriesSince(
                since, ViewType.LISTEN, PageRequest.of(0, size));
        if (ranked.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> listenCounts = new LinkedHashMap<>();
        for (Object[] row : ranked) {
            listenCounts.put((Long) row[0], (Long) row[1]);
        }

        // Một lượt truy vấn cho cả trang xếp hạng. findAllById trả về theo thứ tự của cơ sở
        // dữ liệu, nên phải xếp lại theo listenCounts — nó mới là thứ tự đúng.
        // Truyện vừa bị gỡ xuống thì rơi khỏi bảng xếp hạng, dù lượt nghe cũ vẫn
        // nằm trong lịch sử. Lọc ở đây chứ không trong truy vấn đếm: bảng
        // view_events không biết gì về việc xuất bản, và ràng buộc hai thứ ấy vào
        // nhau sẽ khiến mọi câu thống kê phải mang theo một phép nối chỉ để bỏ
        // vài dòng.
        Map<Long, Story> stories = new HashMap<>();
        for (Story story : storyRepository.findAllWithRelationsByIds(listenCounts.keySet())) {
            if (story.isPublished()) {
                stories.put(story.getId(), story);
            }
        }

        Map<Long, Long> chapterCounts = countChapters(List.copyOf(stories.values()));

        List<StoryRankDto> result = new ArrayList<>(listenCounts.size());
        listenCounts.forEach((storyId, count) -> {
            Story story = stories.get(storyId);
            // Truyện đã bị xóa vẫn còn lượt nghe trong lịch sử: bỏ qua dòng đó thay vì
            // để bảng xếp hạng vỡ.
            if (story != null) {
                result.add(StoryRankDto.from(story, chapterCounts.getOrDefault(storyId, 0L), count));
            }
        });
        return result;
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
