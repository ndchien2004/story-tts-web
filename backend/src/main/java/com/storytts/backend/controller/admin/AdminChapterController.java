package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.AccessLevel;
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

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chương")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chapterService.delete(id);
        return ResponseEntity.noContent().build();
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

    public record AccessLevelRequest(
            @NotNull(message = "Vui lòng chọn mức truy cập") AccessLevel accessLevel) {
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
