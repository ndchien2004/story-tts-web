package com.storytts.backend.controller;

import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đọc nội dung chương.
 * Nếu chương bị khóa và người dùng không đủ quyền, service ném lỗi 403 —
 * controller không tự kiểm tra quyền để tránh lặp code (mục 10 đề bài).
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
@Tag(name = "Chương truyện", description = "Đọc nội dung chương")
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping("/{id}")
    @Operation(summary = "Nội dung chương. Trả 403 kèm requiredAccessLevel nếu chương bị khóa.")
    public ChapterDetailDto detail(@PathVariable Long id) {
        return chapterService.getDetail(id);
    }
}
