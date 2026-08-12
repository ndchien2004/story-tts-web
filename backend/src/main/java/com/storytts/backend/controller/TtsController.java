package com.storytts.backend.controller;

import com.storytts.backend.dto.audio.TtsReaderStatusDto;
import com.storytts.backend.service.tts.ReaderTtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tình trạng chức năng tạo audio bằng AI, không gắn với chương nào.
 *
 * <p>Trang đọc gọi một lần khi mở để biết nên hiện nút "Nghe bằng AI", hiện lời
 * mời đăng nhập, hay không hiện gì cả — thay vì đoán rồi để người dùng bấm vào
 * một nút chắc chắn sẽ lỗi.
 */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@Tag(name = "Giọng đọc AI", description = "Tình trạng và hạn mức tạo audio")
public class TtsController {

    private final ReaderTtsService readerTtsService;

    @GetMapping("/status")
    @Operation(summary = "Tính năng có bật không, và người đang gọi còn bao nhiêu lượt hôm nay")
    public ResponseEntity<TtsReaderStatusDto> status() {
        // Số lượt còn lại là của riêng người đang gọi, không được nằm trong cache
        // dùng chung nào.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(readerTtsService.status());
    }
}
