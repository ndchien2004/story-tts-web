package com.storytts.backend.controller;

import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.interaction.CommentDto;
import com.storytts.backend.dto.interaction.CommentRequest;
import com.storytts.backend.dto.interaction.FavoriteStatusDto;
import com.storytts.backend.dto.interaction.ProgressDto;
import com.storytts.backend.dto.interaction.ProgressRequest;
import com.storytts.backend.dto.interaction.ShelfEntryDto;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.service.FavoriteService;
import com.storytts.backend.service.RatingCommentService;
import com.storytts.backend.service.ReadingProgressService;
import com.storytts.backend.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tương tác của người dùng với truyện: yêu thích, tiến độ đọc, đánh giá và bình luận
 * (mục 4.3 và 4.6 của đề bài).
 *
 * <p>Phân quyền theo URL nằm ở {@code SecurityConfig}: chỉ hai endpoint đọc dữ liệu công khai
 * (danh sách bình luận, số lượt yêu thích) là mở cho Khách, phần còn lại yêu cầu đăng nhập.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Tương tác", description = "Yêu thích, tiến độ đọc, đánh giá và bình luận")
public class InteractionController {

    private final FavoriteService favoriteService;
    private final ReadingProgressService readingProgressService;
    private final RatingCommentService ratingCommentService;
    private final StoryService storyService;

    // ==================== Yêu thích ====================

    @GetMapping("/api/stories/{storyId}/favorite")
    @Operation(summary = "Trạng thái yêu thích của truyện với người dùng hiện tại")
    public FavoriteStatusDto favoriteStatus(@PathVariable Long storyId) {
        return favoriteService.status(storyId);
    }

    @PostMapping("/api/stories/{storyId}/favorite")
    @Operation(summary = "Bật/tắt đánh dấu yêu thích cho một truyện")
    public FavoriteStatusDto toggleFavorite(@PathVariable Long storyId) {
        return favoriteService.toggle(storyId);
    }

    @GetMapping("/api/me/favorites")
    @Operation(summary = "Danh sách truyện đã đánh dấu yêu thích")
    public PageResponse<StorySummaryDto> myFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return favoriteService.listMine(page, size);
    }

    // ==================== Tiến độ đọc/nghe ====================

    @PutMapping("/api/chapters/{chapterId}/progress")
    @Operation(summary = "Ghi nhận vị trí đang đọc/nghe của một chương")
    public ProgressDto saveProgress(@PathVariable Long chapterId,
                                    @Valid @RequestBody ProgressRequest request) {
        return readingProgressService.save(chapterId, request);
    }

    @PostMapping("/api/chapters/{chapterId}/read")
    @Operation(summary = "Đánh dấu đã đọc xong một chương")
    public ProgressDto markRead(@PathVariable Long chapterId) {
        return readingProgressService.markCompleted(chapterId);
    }

    @GetMapping("/api/me/reading")
    @Operation(summary = "Lịch sử đọc/nghe gần đây")
    public List<ProgressDto> recentReading(@RequestParam(defaultValue = "10") int limit) {
        return readingProgressService.recent(limit);
    }

    /**
     * Gợi ý dựa trên lịch sử đọc của chính người gọi, nên nằm cùng nhóm {@code /api/me}
     * chứ không phải trong {@code StoryController} — cùng một truyện, hai người đọc khác
     * nhau sẽ nhận hai danh sách khác nhau.
     */
    @GetMapping("/api/me/recommendations")
    @Operation(summary = "Gợi ý truyện cùng thể loại với những truyện đã đọc")
    public List<StorySummaryDto> recommendations(@RequestParam(defaultValue = "6") int limit) {
        return storyService.recommendations(limit);
    }

    @GetMapping("/api/me/shelf")
    @Operation(summary = "Tủ truyện: mỗi truyện một dòng, kèm số chương đã đọc xong")
    public List<ShelfEntryDto> shelf() {
        return readingProgressService.shelf();
    }

    // ==================== Đánh giá và bình luận ====================

    @GetMapping("/api/stories/{storyId}/comments")
    @Operation(summary = "Bình luận và đánh giá của một truyện, mới nhất trước")
    public PageResponse<CommentDto> comments(@PathVariable Long storyId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return ratingCommentService.list(storyId, page, size);
    }

    @PostMapping("/api/stories/{storyId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi đánh giá sao và/hoặc bình luận cho một truyện")
    public CommentDto addComment(@PathVariable Long storyId,
                                 @Valid @RequestBody CommentRequest request) {
        return ratingCommentService.create(storyId, request);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa bình luận của chính mình (Admin xóa được của mọi người)")
    public void deleteComment(@PathVariable Long commentId) {
        ratingCommentService.delete(commentId);
    }
}
