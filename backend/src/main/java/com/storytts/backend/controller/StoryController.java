package com.storytts.backend.controller;

import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.story.StoryDetailDto;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API đọc truyện cho mọi đối tượng, kể cả Khách chưa đăng nhập. */
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Truyện", description = "Danh sách và chi tiết truyện")
public class StoryController {

    private final StoryService storyService;
    private final ChapterService chapterService;

    @GetMapping
    @Operation(summary = "Danh sách truyện có tìm kiếm, lọc thể loại, sắp xếp và phân trang")
    public PageResponse<StorySummaryDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) StoryStatus status,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return storyService.search(keyword, genreId, status, sort, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết truyện kèm danh sách chương (có cờ khóa cho từng chương)")
    public StoryDetailDto detail(@PathVariable Long id) {
        return storyService.getDetail(id);
    }

    @GetMapping("/{id}/chapters")
    @Operation(summary = "Chỉ lấy danh sách chương của một truyện")
    public List<ChapterSummaryDto> chapters(@PathVariable Long id) {
        return chapterService.listByStory(id);
    }
}
