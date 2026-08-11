package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thông tin xác thực kênh thanh toán PayOS.
 *
 * @param endpoint            gốc REST API của PayOS
 * @param clientId            gửi kèm header {@code x-client-id}
 * @param apiKey              gửi kèm header {@code x-api-key}
 * @param checksumKey         khóa ký HMAC-SHA256; không bao giờ rời khỏi máy chủ
 * @param returnUrl           PayOS đưa người dùng về đây sau khi trả tiền
 * @param cancelUrl           PayOS đưa người dùng về đây khi bấm hủy
 * @param orderTimeoutMinutes link thanh toán hết hiệu lực sau bấy nhiêu phút
 */
@ConfigurationProperties(prefix = "app.payos")
public record PayosProperties(
        String endpoint,
        String clientId,
        String apiKey,
        String checksumKey,
        String returnUrl,
        String cancelUrl,
        int orderTimeoutMinutes
) {

    /** Thiếu bất kỳ khóa nào thì coi như chưa cấu hình — tính năng tự tắt thay vì lỗi 500. */
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
