package com.storytts.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.storytts.backend.service.VipOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nơi PayOS gọi về khi tiền đã vào tài khoản.
 *
 * <p>Endpoint này để mở ở tầng URL vì PayOS không mang theo JWT nào cả. Thứ
 * chặn người lạ là chữ ký HMAC trong body, được đối chiếu trong
 * {@code VipOrderService#handleWebhook} trước khi bất cứ điều gì được ghi.
 *
 * <p>Luôn trả về HTTP 200, kể cả khi bỏ qua: PayOS coi mã lỗi là tín hiệu để
 * gửi lại, và gửi lại mãi một payload đã bị từ chối thì chẳng ích gì. Kết quả
 * thật nằm ở trường {@code success} của body.
 */
@RestController
@RequestMapping("/api/payments/payos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Thanh toán", description = "Webhook PayOS")
public class PaymentWebhookController {

    private final VipOrderService orderService;

    @PostMapping("/webhook")
    @Operation(summary = "PayOS gọi về khi một đơn đổi trạng thái")
    public Map<String, Object> webhook(@RequestBody JsonNode payload) {
        boolean handled;
        try {
            handled = orderService.handleWebhook(payload);
        } catch (RuntimeException ex) {
            // Nuốt lỗi có chủ đích: một đơn hỏng không được phép làm PayOS gửi
            // lại vô hạn. Bản ghi log là nơi để lần lại.
            log.error("Lỗi khi xử lý webhook PayOS", ex);
            handled = false;
        }
        return Map.of("success", handled);
    }
}
