package com.storytts.backend.controller;

import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.ChapterTranscriptDto;
import com.storytts.backend.service.AudioService;
import com.storytts.backend.service.storage.ByteRange;
import com.storytts.backend.service.storage.MediaSlice;
import com.storytts.backend.service.storage.MediaStreamResource;
import com.storytts.backend.service.tts.ReaderTtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * Mốc thời gian từng chữ của một bản audio.
     *
     * <p>Trang đọc lấy một lần khi mở chương rồi tự dò tại chỗ trong lúc phát —
     * không có lời gọi nào theo nhịp phát, vì một yêu cầu mỗi giây cho một việc
     * hiển thị là cách chắc chắn nhất để phần tô sáng giật theo đường truyền.
     *
     * <p>Chỉ nên gọi khi {@code hasTranscript} của bản ấy là true; bản không có
     * mốc trả về mảng rỗng chứ không báo lỗi.
     */
    @GetMapping("/audio/{audioId}/transcript")
    @Operation(summary = "Mốc thời gian từng chữ của một bản audio",
            description = "Dùng cho phần tô sáng chữ theo giọng đọc. Bản audio không có "
                    + "mốc thời gian (admin tải lên, hoặc dựng trước khi có tính năng này) "
                    + "trả về mảng rỗng. Chương bị khóa trả 403 y như đường phát.")
    public ChapterTranscriptDto transcript(@PathVariable Long chapterId, @PathVariable Long audioId) {
        return audioService.transcript(chapterId, audioId);
    }

    /**
     * Streams audio with HTTP range support so the player can seek without
     * downloading the whole file first.
     *
     * The browser's audio element cannot set an Authorization header, so the
     * token may also arrive as an `access_token` query parameter.
     *
     * <p>Khoảng byte được đọc từ header rồi giới hạn ở {@link #CHUNK_SIZE} <b>trước
     * khi</b> chạm tới nơi lưu trữ, chứ không cắt sau khi đã mở trọn file. Khác
     * biệt ấy không thấy được khi audio nằm trên đĩa ngay cạnh, nhưng khi nó nằm
     * ở Cloudinary thì đây là chỗ quyết định máy chủ tải về một megabyte hay tải
     * về cả chương chỉ để trả lại một megabyte.
     */
    @GetMapping("/audio/{audioId}")
    @Operation(summary = "Phát audio (hỗ trợ tua bằng HTTP Range)")
    public ResponseEntity<Resource> stream(@PathVariable Long chapterId,
                                           @PathVariable Long audioId,
                                           @RequestHeader HttpHeaders headers) {
        ByteRange requested = ByteRange.parseFirst(headers.getFirst(HttpHeaders.RANGE));

        // Hai bước, và thứ tự này là một ràng buộc chứ không phải một cách viết:
        // bước đầu là giao dịch kiểm quyền, bước sau là lời gọi mạng ra ngoài.
        // Xem AudioService.resolveForStreaming về lý do chúng không được gộp.
        AudioService.StreamTarget target = audioService.resolveForStreaming(chapterId, audioId);
        MediaSlice slice = audioService.openStream(
                target, requested == null ? null : requested.cap(CHUNK_SIZE));

        MediaType mediaType = slice.contentType() == null
                ? MediaType.parseMediaType("audio/mpeg")
                : MediaType.parseMediaType(slice.contentType());

        ResponseEntity.BodyBuilder response = slice.partial()
                ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_RANGE, slice.contentRangeHeader())
                : ResponseEntity.ok();

        return response
                .contentType(mediaType)
                .contentLength(slice.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new MediaStreamResource(slice));
    }

}
