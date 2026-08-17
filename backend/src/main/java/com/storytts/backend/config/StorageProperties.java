package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nơi lưu audio chương và nhạc nền.
 *
 * @param driver   nơi lưu được dùng khi chạy — xem {@link StorageDriver}
 * @param audioDir thư mục audio chương, chỉ có nghĩa với {@link StorageDriver#LOCAL}
 * @param bgmDir   thư mục nhạc nền, chỉ có nghĩa với {@link StorageDriver#LOCAL}
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        StorageDriver driver,
        String audioDir,
        String bgmDir
) {

    /** Nơi lưu file media. */
    public enum StorageDriver {

        /**
         * Hệ tệp của chính máy đang chạy ứng dụng.
         *
         * <p>Đúng khi lập trình ở máy cá nhân, và đúng trên máy chủ có đĩa riêng.
         * <b>Sai</b> trên hạ tầng có hệ tệp tạm thời: ở đó file biến mất sau mỗi
         * lần triển khai lại, khởi động lại, hay ngủ vì vắng người truy cập.
         */
        LOCAL,

        /**
         * Cloudinary, cùng tài khoản đang dùng cho ảnh đại diện.
         *
         * <p>Nơi lưu dành cho lúc chạy thật trên Render: hệ tệp ở đó không giữ
         * được gì, còn audio dựng bằng ElevenLabs thì tốn tiền cho từng bản.
         */
        CLOUDINARY
    }
}
