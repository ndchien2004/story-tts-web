package com.storytts.backend.controller;

import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.service.AudioService;
import com.storytts.backend.service.tts.ReaderTtsService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Nghe audio của một chương, và tự tạo audio nếu chương chưa có (mục 4.4 và 4.5).
 *
 * <p>Việc tạo audio tốn tiền thật, nên nó không mở trần: {@link ReaderTtsService}
 * giữ hạn mức mỗi ngày, trần chung của hệ thống và trần độ dài chương, còn khu
 * quản trị vẫn có đường riêng không bị hạn mức. Bản đã dựng rồi thì lần nghe sau
 * không tốn lượt nào.
 *
 * <p>Quyền truy cập quyết định ở tầng service, nên chương bị khóa trả 403 ở đây
 * y như khi đọc chữ — kể cả khi bên gọi biết đúng id của file audio.
 */
@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
@Tag(name = "Audio", description = "Nghe audio của chương và tạo audio bằng AI")
public class AudioController {

    /** Bytes served per ranged response; large enough to seek smoothly. */
    private static final long CHUNK_SIZE = 1024L * 1024;

    private final AudioService audioService;
    private final ReaderTtsService readerTtsService;

    @GetMapping("/audio")
    @Operation(summary = "Danh sách bản audio của chương")
    public List<AudioInfoDto> list(@PathVariable Long chapterId) {
        return audioService.listForChapter(chapterId);
    }

    /**
     * Nút "Nghe bằng AI" của trang đọc.
     *
     * <p>Trả về ngay chứ không chờ dựng xong: trúng bản đã có thì READY, còn lại
     * là PROCESSING và trang đọc hỏi lại qua {@link #ttsStatus} cho tới khi xong.
     */
    @PostMapping("/tts")
    @Operation(summary = "Tạo audio bằng AI cho chương",
            description = "Dùng lại bản đã tạo nếu có (không tốn lượt). "
                    + "Hết lượt trong ngày trả 429, chương bị khóa trả 403.")
    public AudioInfoDto requestTts(@PathVariable Long chapterId) {
        return readerTtsService.request(chapterId);
    }

    /**
     * Trạng thái một bản audio đang dựng.
     *
     * <p>Tách khỏi {@link #list}: đường kia ghi một lượt nghe mỗi lần gọi, mà chỗ
     * này bị hỏi vài giây một lần trong lúc chờ.
     */
    @GetMapping("/tts/{audioId}")
    @Operation(summary = "Trạng thái một bản audio (dùng để chờ dựng xong, không tính lượt nghe)")
    public AudioInfoDto ttsStatus(@PathVariable Long chapterId, @PathVariable Long audioId) {
        return audioService.trackStatus(chapterId, audioId);
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
