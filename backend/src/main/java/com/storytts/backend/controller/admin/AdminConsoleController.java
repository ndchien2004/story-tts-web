package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.admin.AdminChartsDto;
import com.storytts.backend.dto.admin.AdminCommentDto;
import com.storytts.backend.dto.admin.AdminStatsDto;
import com.storytts.backend.dto.admin.BatchTtsResultDto;
import com.storytts.backend.dto.admin.ChapterAudioDto;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.service.AdminStatsService;
import com.storytts.backend.service.AudioAdminService;
import com.storytts.backend.service.RatingCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ba màn hình quản trị đứng ngoài phần CRUD nội dung: tổng quan, kiểm duyệt bình
 * luận và quản lý audio.
 *
 * <p>Việc xóa bình luận vẫn dùng chung {@code DELETE /api/comments/{id}} bên
 * {@code InteractionController} — quyền xóa của Admin đã nằm sẵn trong service đó,
 * thêm một endpoint nữa chỉ là hai đường đi tới cùng một chỗ.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Bảng điều khiển", description = "Tổng quan, kiểm duyệt bình luận, quản lý audio")
public class AdminConsoleController {

    private final AdminStatsService adminStatsService;
    private final RatingCommentService ratingCommentService;
    private final AudioAdminService audioAdminService;

    // ==================== Tổng quan ====================

    @GetMapping("/stats")
    @Operation(summary = "Số liệu tổng quan của toàn hệ thống")
    public AdminStatsDto stats() {
        return adminStatsService.overview();
    }

    @GetMapping("/stats/charts")
    @Operation(summary = "Dữ liệu ba biểu đồ: lượt đọc/nghe theo ngày, top truyện, tỷ lệ mức khóa")
    public AdminChartsDto charts(@RequestParam(defaultValue = "14") int days) {
        return adminStatsService.charts(days);
    }

    // ==================== Kiểm duyệt bình luận ====================

    @GetMapping("/comments")
    @Operation(summary = "Toàn bộ bình luận của mọi truyện, mới nhất trước")
    public PageResponse<AdminCommentDto> comments(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long storyId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ratingCommentService.listAll(keyword, storyId, page, size);
    }

    // ==================== Audio và TTS ====================

    @GetMapping("/audio/chapters")
    @Operation(summary = "Chương kèm tình trạng audio, lọc được theo truyện và theo việc đã có audio hay chưa")
    public PageResponse<ChapterAudioDto> audioChapters(@RequestParam(required = false) Long storyId,
                                                       @RequestParam(required = false) Boolean withAudio,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return audioAdminService.chapters(storyId, withAudio, page, size);
    }

    /**
     * Giọng đọc có sẵn, không gắn với chương nào.
     * Endpoint cũ nằm dưới một chương cụ thể, mà màn hình này chọn giọng cho cả lô.
     */
    @GetMapping("/audio/voices")
    @Operation(summary = "Danh sách giọng đọc của nhà cung cấp đang cấu hình")
    public List<VoiceOptionDto> voices() {
        return audioAdminService.voices();
    }

    @PostMapping("/audio/batch-tts")
    @Operation(summary = "Xếp hàng tạo audio bằng AI cho nhiều chương")
    public List<BatchTtsResultDto> batchTts(@Valid @RequestBody BatchTtsRequest request) {
        return audioAdminService.generateBatch(request.chapterIds(), request.voice(), request.speed());
    }

    public record BatchTtsRequest(
            @NotEmpty(message = "Chưa chọn chương nào") List<Long> chapterIds,
            String voice,
            @Min(value = -3, message = "Tốc độ đọc phải từ -3 đến 3")
            @Max(value = 3, message = "Tốc độ đọc phải từ -3 đến 3")
            Integer speed
    ) {
    }
}
