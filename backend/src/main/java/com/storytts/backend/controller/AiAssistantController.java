package com.storytts.backend.controller;

import com.storytts.backend.dto.ai.AssistantAskRequest;
import com.storytts.backend.dto.ai.AssistantReplyDto;
import com.storytts.backend.dto.ai.AssistantStatusDto;
import com.storytts.backend.service.ai.StoryAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trợ lý AI của trang đọc: hỏi về chương đang mở.
 *
 * <p>Controller mỏng đúng như những controller khác ở đây — không có một dòng
 * nào của Gemini, không một lần xét quyền. Cả hai nằm trong
 * {@link StoryAssistantService}, và đó là chỗ duy nhất chúng được viết.
 *
 * <p>Hai đường, hai tầng bảo vệ khác nhau:
 * <ul>
 *   <li>{@code GET /status} mở cho cả khách, vì trang đọc phải biết có nên vẽ
 *       cái nút hay không <i>trước</i> khi ai kịp đăng nhập;</li>
 *   <li>{@code POST /story-assistant} không nằm trong danh sách mở, nên nó rơi
 *       vào {@code anyRequest().authenticated()} — hỏi là tiêu tiền, phải có
 *       danh tính mới tính hạn mức được.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "Trợ lý AI", description = "Tóm tắt và giải đáp về chương đang đọc")
public class AiAssistantController {

    private final StoryAssistantService assistantService;

    @GetMapping("/status")
    @Operation(summary = "Trợ lý có dùng được không, và người đang gọi còn bao nhiêu lượt hôm nay")
    public ResponseEntity<AssistantStatusDto> status() {
        // Số lượt còn lại là của riêng người đang gọi, không được nằm trong một
        // cache dùng chung nào — cùng lý do với /api/tts/status.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(assistantService.status());
    }

    @PostMapping("/story-assistant")
    @Operation(summary = "Hỏi trợ lý về chương đang đọc. Chỉ nhận chapterId, không nhận nội dung chương.")
    public AssistantReplyDto ask(@Valid @RequestBody AssistantAskRequest request) {
        return assistantService.ask(request);
    }
}
