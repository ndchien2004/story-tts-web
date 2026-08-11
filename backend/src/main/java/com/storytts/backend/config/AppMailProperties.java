package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phần email của riêng ứng dụng: người gửi và liên kết đặt lại mật khẩu.
 *
 * <p>Máy chủ SMTP vẫn khai báo bằng {@code spring.mail.*} để Spring Boot tự
 * dựng {@code JavaMailSender}, nên ở đây không lặp lại host/cổng/mật khẩu.
 *
 * @param from                  địa chỉ đứng tên người gửi
 * @param fromName              tên hiển thị cạnh địa chỉ đó
 * @param resetUrl              trang đặt lại mật khẩu ở frontend, token nối vào sau
 * @param resetTokenTtlMinutes  liên kết sống được bao lâu
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        String from,
        String fromName,
        String resetUrl,
        int resetTokenTtlMinutes
) {
}
