package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.service.AudioAdminService;
import com.storytts.backend.service.AudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Xem, tải lên và xóa audio của chương — toàn bộ việc quản lý audio nằm ở đây. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Audio", description = "Tải lên file audio thu sẵn cho chương")
public class AdminAudioController {

    private final AudioService audioService;
    private final AudioAdminService audioAdminService;

    @GetMapping("/chapters/{chapterId}/audio")
    @Operation(summary = "Mọi bản audio của một chương, kể cả bản hỏng")
    public List<AudioInfoDto> list(@PathVariable Long chapterId) {
        return audioAdminService.chapterAudio(chapterId);
    }

    @PostMapping(value = "/chapters/{chapterId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên file audio thu sẵn cho một chương")
    public ResponseEntity<AudioInfoDto> upload(@PathVariable Long chapterId,
                                               @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(audioService.uploadForChapter(chapterId, file));
    }

    @DeleteMapping("/audio/{audioId}")
    @Operation(summary = "Xóa một bản audio")
    public ResponseEntity<Void> delete(@PathVariable Long audioId) {
        audioService.delete(audioId);
        return ResponseEntity.noContent().build();
    }
}
