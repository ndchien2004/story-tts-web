package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.dto.admin.ContentDeletionDto;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chapters")
@RequiredArgsConstructor
@Tag(name = "Admin - Chương", description = "Sửa/xóa chương và đặt mức khóa")
public class AdminChapterController {

    private final ChapterService chapterService;

    /** Admin luôn đủ quyền nên vẫn đọc được nội dung mọi chương để chỉnh sửa. */
    @GetMapping("/{id}")
    @Operation(summary = "Lấy nội dung chương để sửa")
    public ChapterDetailDto detail(@PathVariable Long id) {
        return chapterService.getDetail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa chương (tiêu đề, nội dung, số thứ tự, mức khóa)")
    public ChapterSummaryDto update(@PathVariable Long id, @Valid @RequestBody ChapterRequest request) {
        return chapterService.update(id, request);
    }

    /** Thao tác nhanh: chỉ đổi mức khóa mà không phải gửi lại toàn bộ nội dung chương. */
    @PatchMapping("/{id}/access-level")
    @Operation(summary = "Đổi riêng mức khóa của chương")
    public ChapterSummaryDto changeAccessLevel(@PathVariable Long id,
                                               @Valid @RequestBody AccessLevelRequest request) {
        return chapterService.changeAccessLevel(id, request.accessLevel());
    }

    /**
     * Xóa hẳn một chương.
     *
     * <p>Trả về thân response thay vì 204 vì thao tác này có thể trả lại Xu cho
     * người đã mua chương — một việc đụng tới tiền, xảy ra như hệ quả phụ của
     * một cú bấm nút. 204 nghĩa là nó diễn ra hoàn toàn im lặng.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chương",
            description = "Người đã mua chương bằng Xu được hoàn lại đúng số đã trả, "
                    + "trong cùng giao dịch. Kết quả trả về nói rõ hoàn bao nhiêu cho mấy người.")
    public ContentDeletionDto delete(@PathVariable Long id) {
        return chapterService.delete(id);
    }

    /** Đặt cùng một mức khóa cho nhiều chương — thay vì mở từng dropdown một. */
    @PatchMapping("/access-level")
    @Operation(summary = "Đổi mức khóa cho nhiều chương cùng lúc")
    public Map<String, Integer> changeAccessLevelBulk(@Valid @RequestBody BulkAccessLevelRequest request) {
        int updated = chapterService.changeAccessLevelBulk(request.chapterIds(), request.accessLevel());
        return Map.of("updated", updated);
    }

    /**
     * Đặt giá Xu cho một chương.
     *
     * <p>Tách khỏi mức khóa vì hai thứ trả lời hai câu khác nhau — ai được nhìn
     * tới chương, và mở nó tốn bao nhiêu. Đặt giá 0 là ngừng bán lẻ chương ấy;
     * chương quay về đúng cách nó hoạt động trước khi có Xu.
     */
    @PatchMapping("/{id}/pricing")
    @Operation(summary = "Đặt giá Xu cho một chương. 0 nghĩa là không bán lẻ.")
    public ChapterSummaryDto changePricing(@PathVariable Long id,
                                           @Valid @RequestBody PricingRequest request) {
        return chapterService.changeCoinPrice(id, request.coinPrice());
    }

    /** Đặt cùng một giá cho nhiều chương — khóa nửa sau của một truyện dài chẳng hạn. */
    @PatchMapping("/pricing")
    @Operation(summary = "Đặt giá Xu cho nhiều chương cùng lúc")
    public Map<String, Integer> changePricingBulk(@Valid @RequestBody BulkPricingRequest request) {
        int updated = chapterService.changeCoinPriceBulk(request.chapterIds(), request.coinPrice());
        return Map.of("updated", updated);
    }

    /**
     * Đăng, gỡ xuống, hoặc hẹn giờ đăng một chương.
     *
     * <p>Tách khỏi {@link #update}: dời lịch không nên bắt gửi lại toàn bộ nội
     * dung chương — và với một chương dài thì việc gửi lại ấy còn có nguy cơ ghi
     * đè bằng một bản đã cũ đang mở trong form của một tab khác.
     */
    @PatchMapping("/{id}/publication")
    @Operation(summary = "Đăng / gỡ xuống / hẹn giờ đăng một chương",
            description = "draft=true là gỡ về bản nháp. Còn lại: publishedAt bỏ trống là "
                    + "đăng ngay, mốc ở tương lai là hẹn giờ — tới giờ chương tự hiện, "
                    + "không có tác vụ nền nào phải chạy.")
    public ChapterSummaryDto changePublication(@PathVariable Long id,
                                               @Valid @RequestBody PublicationRequest request) {
        return chapterService.changePublication(id, request.draft(), request.publishedAt());
    }

    public record AccessLevelRequest(
            @NotNull(message = "Vui lòng chọn mức truy cập") AccessLevel accessLevel) {
    }

    /**
     * @param draft       true là gỡ về bản nháp; khi ấy {@code publishedAt} bị bỏ qua
     * @param publishedAt bỏ trống là đăng ngay; mốc ở tương lai là hẹn giờ
     */
    public record PublicationRequest(boolean draft, Instant publishedAt) {
    }

    public record PricingRequest(
            @NotNull(message = "Vui lòng nhập giá")
            @PositiveOrZero(message = "Giá không được âm") Long coinPrice) {
    }

    public record BulkPricingRequest(
            @NotEmpty(message = "Chưa chọn chương nào") List<Long> chapterIds,
            @NotNull(message = "Vui lòng nhập giá")
            @PositiveOrZero(message = "Giá không được âm") Long coinPrice) {
    }

    public record BulkAccessLevelRequest(
            @NotEmpty(message = "Chưa chọn chương nào") List<Long> chapterIds,
            @NotNull(message = "Vui lòng chọn mức truy cập") AccessLevel accessLevel) {
    }
}
