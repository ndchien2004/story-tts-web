package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.bgm.BgmTrackDto;
import com.storytts.backend.dto.bgm.BgmTrackRequest;
import com.storytts.backend.service.BgmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Kho nhạc nền: tải lên, đặt tên, tạm ẩn và xóa. */
@RestController
@RequestMapping("/api/admin/bgm")
@RequiredArgsConstructor
@Tag(name = "Admin - Nhạc nền", description = "Tải lên nhạc nền cho người nghe chọn")
public class AdminBgmController {

    private final BgmService bgmService;

    @GetMapping
    @Operation(summary = "Mọi bản nhạc nền, kể cả bản đang tắt")
    public List<BgmTrackDto> list() {
        return bgmService.listAll();
    }

    /**
     * Tải lên một bản nhạc.
     *
     * <p>Tên và dòng ghi công đi kèm file trong cùng một multipart chứ không
     * phải một lời gọi thứ hai: một bản nhạc chỉ có nghĩa khi đã có tên, và
     * tách làm hai bước thì lần nào hỏng bước sau cũng để lại một bản vô danh.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên một bản nhạc nền")
    public ResponseEntity<BgmTrackDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "title", required = false) String title,
            @RequestPart(value = "credit", required = false) String credit) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bgmService.upload(file, title, credit));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa tên, dòng ghi công hoặc thứ tự của một bản nhạc")
    public BgmTrackDto update(@PathVariable Long id, @Valid @RequestBody BgmTrackRequest request) {
        return bgmService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Bật/tắt một bản nhạc trong danh sách người nghe chọn")
    public BgmTrackDto setActive(@PathVariable Long id,
                                 @Valid @RequestBody FlagRequest request) {
        return bgmService.setActive(id, request.value());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa hẳn một bản nhạc nền (file cũng bị xóa)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bgmService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record FlagRequest(@NotNull(message = "Thiếu giá trị") Boolean value) {
    }
}
