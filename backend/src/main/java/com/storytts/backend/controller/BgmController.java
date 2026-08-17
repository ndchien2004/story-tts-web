package com.storytts.backend.controller;

import com.storytts.backend.dto.bgm.BgmTrackDto;
import com.storytts.backend.service.BgmService;
import com.storytts.backend.service.storage.MediaSlice;
import com.storytts.backend.service.storage.MediaStreamResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kho nhạc nền cho trang đọc.
 *
 * <p>Công khai, kể cả với khách chưa đăng nhập: nhạc nền không phải nội dung
 * bị khóa, và trang đọc gọi đường này ngay khi mở chương công khai đầu tiên.
 * Không có gì riêng tư ở đây để mà chặn.
 */
@RestController
@RequestMapping("/api/bgm")
@RequiredArgsConstructor
@Tag(name = "Nhạc nền", description = "Danh sách nhạc nền quản trị viên đã tải lên")
public class BgmController {

    private final BgmService bgmService;

    @GetMapping
    @Operation(summary = "Những bản nhạc nền đang mở cho người nghe chọn")
    public List<BgmTrackDto> list() {
        return bgmService.listActive();
    }

    /**
     * Phát một bản nhạc nền.
     *
     * <p>Trả trọn file chứ không cắt theo Range như audio chương, và đó là điều
     * đúng ở đây: trình duyệt tải trọn bản nhạc rồi giải mã vào bộ nhớ để lặp
     * cho liền mạch, nên nó không bao giờ hỏi một khúc giữa. File cũng chỉ vài
     * MB, trong khi một chương audio là hàng chục.
     *
     * <p>Đặt cache dài: cùng một bản nhạc được mọi người nghe tải, và nội dung
     * sau một id không bao giờ đổi — sửa bản nhạc là tải lên file mới, ra một
     * id mới.
     */
    @GetMapping("/{id}/stream")
    @Operation(summary = "Phát một bản nhạc nền")
    public ResponseEntity<Resource> stream(@PathVariable Long id) {
        MediaSlice slice = bgmService.openForStreaming(id);

        MediaType mediaType = slice.contentType() == null
                ? MediaType.parseMediaType("audio/mpeg")
                : MediaType.parseMediaType(slice.contentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(slice.contentLength())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new MediaStreamResource(slice));
    }
}
