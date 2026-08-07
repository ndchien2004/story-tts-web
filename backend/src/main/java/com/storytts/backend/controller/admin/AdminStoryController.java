package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.story.StoryRequest;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** CRUD truyện phía Admin (mục 4.2 đề bài). Toàn bộ /api/admin/** đã bị chặn hasRole('ADMIN'). */
@RestController
@RequestMapping("/api/admin/stories")
@RequiredArgsConstructor
@Tag(name = "Admin - Truyện", description = "Quản lý truyện và chương")
public class AdminStoryController {

    private final StoryService storyService;
    private final ChapterService chapterService;

    @PostMapping
    @Operation(summary = "Thêm truyện mới")
    public ResponseEntity<StorySummaryDto> create(@Valid @RequestBody StoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storyService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin truyện")
    public StorySummaryDto update(@PathVariable Long id, @Valid @RequestBody StoryRequest request) {
        return storyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa truyện (kéo theo toàn bộ chương và audio)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{storyId}/chapters")
    @Operation(summary = "Thêm chương mới, kèm chọn mức khóa PUBLIC/MEMBER/VIP")
    public ResponseEntity<ChapterSummaryDto> createChapter(@PathVariable Long storyId,
                                                           @Valid @RequestBody ChapterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chapterService.create(storyId, request));
    }
}
