package com.storytts.backend.controller;

import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.story.StoryDetailDto;
import com.storytts.backend.dto.story.StoryRankDto;
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

    @GetMapping("/top-listened")
    @Operation(summary = "Xếp hạng truyện nghe nhiều nhất theo ngày/tuần/tháng",
            description = "period nhận day (mặc định), week hoặc month. "
                    + "Đếm trên lượt nghe thực tế trong khoảng đó, không phải tổng cộng dồn.")
    public List<StoryRankDto> topListened(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "10") int limit) {
        return storyService.topListened(period, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết truyện kèm danh sách chương (có cờ khóa cho từng chương)")
    public StoryDetailDto detail(@PathVariable Long id) {
        return storyService.getDetail(id);
    }

    /**
     * Danh sách chương của một truyện, có phân trang và tìm kiếm.
     *
     * <p>Trang chi tiết truyện đã kèm sẵn trang đầu; đường này phục vụ những
     * trang sau, ô tìm chương, và nút đảo thứ tự. Truyện dài nghìn chương là lý
     * do nó tồn tại — trước đây cả nghìn dòng ấy về trong một lần gọi.
     */
    @GetMapping("/{id}/chapters")
    @Operation(summary = "Danh sách chương của một truyện (phân trang, tìm theo tên hoặc số chương)",
            description = "q nhận tên chương hoặc một con số để nhảy tới đúng chương ấy. "
                    + "order=asc (mặc định) hoặc desc. Chương chưa đăng chỉ hiện với quản trị viên.")
    public PageResponse<ChapterSummaryDto> chapters(
            @PathVariable Long id,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return chapterService.listByStory(id, q, !"desc".equalsIgnoreCase(order), page, size);
    }
}
