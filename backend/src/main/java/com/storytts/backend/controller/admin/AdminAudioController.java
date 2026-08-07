package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.service.AudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload and removal of pre-recorded chapter audio. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Audio", description = "Tải lên file audio thu sẵn cho chương")
public class AdminAudioController {

    private final AudioService audioService;

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
