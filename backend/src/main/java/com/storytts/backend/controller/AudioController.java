package com.storytts.backend.controller;

import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.service.AudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Nghe audio của một chương.
 *
 * <p>Chỉ có phát, không có tạo. Việc tạo audio thuộc về khu quản trị: người đọc
 * bấm một nút là sinh ra một file trên đĩa và một lần gọi API tính tiền, nên đó
 * là thứ phải nằm dưới quyền Admin chứ không phải dưới tay mọi khách ghé qua.
 *
 * <p>Quyền truy cập quyết định ở tầng service, nên chương bị khóa trả 403 ở đây
 * y như khi đọc chữ.
 */
@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
@Tag(name = "Audio", description = "Nghe audio của chương")
public class AudioController {

    /** Bytes served per ranged response; large enough to seek smoothly. */
    private static final long CHUNK_SIZE = 1024L * 1024;

    private final AudioService audioService;

    @GetMapping("/audio")
    @Operation(summary = "Danh sách bản audio của chương")
    public List<AudioInfoDto> list(@PathVariable Long chapterId) {
        return audioService.listForChapter(chapterId);
    }

    /**
     * Streams audio with HTTP range support so the player can seek without
     * downloading the whole file first.
     *
     * The browser's audio element cannot set an Authorization header, so the
     * token may also arrive as an `access_token` query parameter.
     */
    @GetMapping("/audio/{audioId}")
    @Operation(summary = "Phát audio (hỗ trợ tua bằng HTTP Range)")
    public ResponseEntity<ResourceRegion> stream(@PathVariable Long chapterId,
                                                 @PathVariable Long audioId,
                                                 @RequestHeader HttpHeaders headers) throws IOException {
        AudioService.StreamHandle handle = audioService.openForStreaming(chapterId, audioId);
        Resource resource = handle.resource();
        long length = resource.contentLength();

        MediaType mediaType = handle.contentType() == null
                ? MediaType.parseMediaType("audio/mpeg")
                : MediaType.parseMediaType(handle.contentType());

        List<HttpRange> ranges = headers.getRange();
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .body(new ResourceRegion(resource, 0, length));
        }

        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        long count = Math.min(CHUNK_SIZE, end - start + 1);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new ResourceRegion(resource, start, count));
    }

}
