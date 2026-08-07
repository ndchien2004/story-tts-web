package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public record AccessLevelRequest(
            @NotNull(message = "Vui lòng chọn mức truy cập") AccessLevel accessLevel) {
    }
}
