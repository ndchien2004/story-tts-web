package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ứng dụng OAuth đăng ký trên Google Cloud Console, dùng cho nút "Đăng nhập
 * bằng Google".
 *
 * <p>Chỉ cần Client ID: trình duyệt nhận ID token trực tiếp từ Google rồi gửi
 * về đây, máy chủ chỉ việc kiểm chữ ký và đối chiếu {@code aud}. Client secret
 * là thứ của luồng đổi authorization code, luồng này không dùng tới.
 *
 * @param certsUrl bộ khóa công khai JWKS của Google, dùng để kiểm chữ ký ID token
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(String clientId, String certsUrl) {

    /** Chưa có Client ID thì nút đăng nhập Google tự ẩn thay vì hiện rồi báo lỗi. */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}
