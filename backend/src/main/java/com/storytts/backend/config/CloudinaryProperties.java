package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tài khoản Cloudinary dùng để lưu ảnh đại diện người dùng.
 *
 * @param folder          thư mục gốc trên Cloudinary, mọi ảnh đều nằm dưới thư mục này
 * @param maxAvatarBytes  giới hạn dung lượng một ảnh đại diện
 */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder,
        long maxAvatarBytes
) {

    /** Thiếu bất kỳ khóa nào thì coi như chưa cấu hình — tính năng tự tắt thay vì lỗi 500. */
    public boolean isConfigured() {
        return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
