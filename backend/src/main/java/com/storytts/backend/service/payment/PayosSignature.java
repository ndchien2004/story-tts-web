package com.storytts.backend.service.payment;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Chữ ký HMAC-SHA256 mà PayOS dùng ở cả hai chiều.
 *
 * <p>Chiều đi (tạo link thanh toán) ký đúng năm trường, theo thứ tự bảng chữ
 * cái, không phải toàn bộ body. Chiều về (webhook) ký toàn bộ khối {@code data}
 * với các khóa sắp xếp theo bảng chữ cái. Hai quy tắc khác nhau nên chúng là
 * hai hàm riêng chứ không phải một hàm dùng chung.
 *
 * <p>Việc đối chiếu chữ ký webhook là thứ duy nhất ngăn người ngoài gọi thẳng
 * vào endpoint đó và tự cấp VIP cho mình, nên nó phải chạy trước mọi thứ khác.
 */
public final class PayosSignature {

    private PayosSignature() {
    }

    /**
     * Chữ ký cho yêu cầu tạo link thanh toán.
     *
     * <p>PayOS quy định đúng chuỗi này, đúng thứ tự này — thêm trường khác vào
     * hay đổi thứ tự đều làm chữ ký sai.
     */
    public static String forPaymentRequest(String checksumKey, long orderCode, long amount,
                                           String description, String returnUrl, String cancelUrl) {
        String raw = "amount=" + amount
                + "&cancelUrl=" + nullToEmpty(cancelUrl)
                + "&description=" + nullToEmpty(description)
                + "&orderCode=" + orderCode
                + "&returnUrl=" + nullToEmpty(returnUrl);
        return hmacSha256(checksumKey, raw);
    }

    /**
     * Chữ ký của khối {@code data} trong webhook.
     *
     * <p>Mọi khóa của {@code data} được sắp theo bảng chữ cái rồi nối thành
     * {@code key=value&…}; giá trị null trở thành chuỗi rỗng.
     */
    public static String forWebhookData(String checksumKey, JsonNode data) {
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = data.fieldNames(); it.hasNext(); ) {
            keys.add(it.next());
        }
        keys.sort(String::compareTo);

        StringBuilder raw = new StringBuilder();
        for (String key : keys) {
            if (!raw.isEmpty()) {
                raw.append('&');
            }
            JsonNode value = data.get(key);
            String text = value == null || value.isNull() ? "" : value.asText();
            raw.append(key).append('=').append(text);
        }
        return hmacSha256(checksumKey, raw.toString());
    }

    /** So sánh không phụ thuộc thời gian, để không rò rỉ thông tin qua độ trễ. */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }

    private static String hmacSha256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            // Thuật toán này có sẵn trong mọi JVM; hỏng ở đây là lỗi cấu hình máy chủ.
            throw new IllegalStateException("Không ký được yêu cầu PayOS", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
