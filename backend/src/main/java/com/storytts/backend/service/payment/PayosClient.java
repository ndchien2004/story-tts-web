package com.storytts.backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storytts.backend.config.PayosProperties;
import com.storytts.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Lớp gọi REST API của PayOS.
 *
 * <p>Gọi thẳng bằng {@link HttpClient} thay vì thêm SDK, giống cách phần TTS và
 * Cloudinary đang làm: chỉ có hai lệnh gọi, còn phần khó là chữ ký thì SDK cũng
 * không giấu đi được.
 *
 * <p>Ở đây không có logic nghiệp vụ nào — không đụng tới đơn hàng, không cấp
 * VIP. Lớp này chỉ biết nói chuyện với PayOS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayosClient {

    /** PayOS cắt phần mô tả dài hơn mức này, nên cắt sẵn cho khớp chữ ký. */
    public static final int MAX_DESCRIPTION_LENGTH = 25;

    private final PayosProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Tạo một link thanh toán và trả về nơi cần đưa người dùng tới.
     *
     * @param orderCode   mã đơn, phải là số dương chưa từng dùng
     * @param amount      số tiền, đơn vị đồng
     * @param description hiển thị trong nội dung chuyển khoản, tối đa 25 ký tự
     */
    public PayosPaymentLink createPaymentLink(long orderCode, long amount, String description,
                                              String buyerName, String buyerEmail) {
        requireConfigured();

        String safeDescription = truncate(description);
        String signature = PayosSignature.forPaymentRequest(
                properties.checksumKey(), orderCode, amount, safeDescription,
                properties.returnUrl(), properties.cancelUrl());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", safeDescription);
        body.put("returnUrl", properties.returnUrl());
        body.put("cancelUrl", properties.cancelUrl());
        body.put("signature", signature);
        if (buyerName != null && !buyerName.isBlank()) {
            body.put("buyerName", buyerName);
        }
        if (buyerEmail != null && !buyerEmail.isBlank()) {
            body.put("buyerEmail", buyerEmail);
        }
        if (properties.orderTimeoutMinutes() > 0) {
            // PayOS nhận epoch giây, không phải mili giây.
            body.put("expiredAt", java.time.Instant.now()
                    .plusSeconds(properties.orderTimeoutMinutes() * 60L).getEpochSecond());
        }

        JsonNode data = post("/v2/payment-requests", body);
        return new PayosPaymentLink(
                data.path("paymentLinkId").asText(null),
                data.path("checkoutUrl").asText(null),
                data.path("status").asText("PENDING"));
    }

    /**
     * Hỏi lại PayOS xem một đơn đã được trả tiền chưa.
     *
     * <p>Cần đến khi webhook không tới được máy chủ — chạy dưới localhost là
     * trường hợp thường gặp nhất — nên trang kết quả vẫn xác nhận được đơn.
     */
    public String fetchStatus(long orderCode) {
        requireConfigured();
        JsonNode data = get("/v2/payment-requests/" + orderCode);
        return data.path("status").asText("PENDING");
    }

    /** Hủy một link thanh toán chưa dùng tới. */
    public void cancelPaymentLink(long orderCode, String reason) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cancellationReason", reason == null ? "Người dùng hủy đơn" : reason);
        post("/v2/payment-requests/" + orderCode + "/cancel", body);
    }

    /* ------------------------------------------------------------------ */
    /* Gọi HTTP                                                            */
    /* ------------------------------------------------------------------ */

    private JsonNode post(String path, ObjectNode body) {
        try {
            HttpRequest request = baseRequest(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return unwrap(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Không kết nối được tới cổng thanh toán. Vui lòng thử lại.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Yêu cầu thanh toán bị gián đoạn. Vui lòng thử lại.");
        }
    }

    private JsonNode get(String path) {
        try {
            HttpRequest request = baseRequest(path).GET().build();
            return unwrap(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Không kết nối được tới cổng thanh toán. Vui lòng thử lại.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Yêu cầu thanh toán bị gián đoạn. Vui lòng thử lại.");
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.endpoint() + path))
                .timeout(Duration.ofSeconds(20))
                .header("x-client-id", properties.clientId())
                .header("x-api-key", properties.apiKey());
    }

    /**
     * PayOS bọc mọi câu trả lời trong {@code {code, desc, data}} và trả HTTP 200
     * kể cả khi thất bại, nên việc kiểm tra thật sự nằm ở trường {@code code}.
     */
    private JsonNode unwrap(HttpResponse<String> response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            log.warn("PayOS trả về nội dung không phải JSON, HTTP {}", response.statusCode());
            throw new BadRequestException("Cổng thanh toán trả về dữ liệu không đọc được.");
        }

        String code = root.path("code").asText("");
        if (!"00".equals(code)) {
            String desc = root.path("desc").asText("không rõ nguyên nhân");
            log.warn("PayOS từ chối yêu cầu: code={} desc={}", code, desc);
            throw new BadRequestException("Cổng thanh toán từ chối yêu cầu: " + desc);
        }
        return root.path("data");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "Cổng thanh toán chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        }
    }

    private static String truncate(String description) {
        if (description == null) {
            return "";
        }
        return description.length() <= MAX_DESCRIPTION_LENGTH
                ? description
                : description.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    /** Kết quả tạo link thanh toán. */
    public record PayosPaymentLink(String paymentLinkId, String checkoutUrl, String status) {
    }
}
