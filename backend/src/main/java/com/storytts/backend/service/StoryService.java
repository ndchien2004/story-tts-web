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
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChapterService chapterService;
    private final GenreService genreService;
    private final AuthorService authorService;

    /**
     * Danh sách truyện: tìm theo tên/tác giả, lọc thể loại, sắp xếp (mục 4.3 [BB] đề bài).
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
     * Trang chi tiết truyện: thông tin + danh sách chương kèm cờ khóa cho người dùng hiện tại.
     * Nội dung chương KHÔNG nằm trong kết quả này.
     */
    @Transactional
    public StoryDetailDto getDetail(Long storyId) {
        Story story = storyRepository.findDetailById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        storyRepository.incrementViewCount(storyId);

        List<ChapterSummaryDto> chapters = chapterService.listByStory(storyId);
        long chapterCount = chapters.size();
        return new StoryDetailDto(StorySummaryDto.from(story, chapterCount), chapters);
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
