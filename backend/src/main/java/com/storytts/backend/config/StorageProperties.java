package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nơi lưu audio chương và nhạc nền.
 *
 * @param driver   nơi lưu được dùng khi chạy — xem {@link StorageDriver}
 * @param audioDir thư mục audio chương, chỉ có nghĩa với {@link StorageDriver#LOCAL}
 * @param bgmDir   thư mục nhạc nền, chỉ có nghĩa với {@link StorageDriver#LOCAL}
 * @param staleRetentionHours xem {@link #staleRetentionHours()}
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        StorageDriver driver,
        String audioDir,
        String bgmDir,

        /**
         * Giữ một bản audio đã lỗi thời bao lâu trước khi xóa file của nó.
         *
         * <p>Không xóa ngay lúc nó thành lỗi thời, vì lúc ấy rất có thể đang có
         * người nghe dở đúng file đó — trang đọc cố ý không cắt ngang họ, nên
         * đường phát vẫn phục vụ bản lỗi thời cho tới khi họ tự chuyển sang nội
         * dung mới. Xóa file dưới chân một request đang stream sẽ biến một thay
         * đổi họ chưa kịp biết thành một lỗi phát giữa chừng.
         *
         * <p>Ba ngày là khoảng đủ rộng cho việc đó, và cũng đủ để một lần sửa
         * nhầm còn kịp hoàn tác trước khi file biến mất. Đặt 0 là tắt hẳn việc
         * dọn — file lỗi thời ở lại vĩnh viễn, đôi khi là điều muốn khi đang dò
         * lỗi.
         *
         * <p>Kiểu bọc chứ không phải {@code int}, và đó là chủ ý: khóa cấu hình
         * vắng mặt sẽ bind thành 0, mà 0 ở đây đã mang nghĩa "đừng dọn gì cả".
         * Nếu hai trường hợp ấy trùng giá trị thì một dòng properties bị xóa
         * nhầm sẽ lặng lẽ tắt việc dọn dẹp — kiểu hỏng chỉ lộ ra sau vài tháng,
         * lúc đĩa đã đầy. Null phân biệt được "chưa cấu hình" với "cố ý tắt".
         */
        Integer staleRetentionHours
) {

    /** Ba ngày, dùng khi cấu hình không nói gì. */
    private static final int DEFAULT_STALE_RETENTION_HOURS = 72;

    public StorageProperties {
        if (staleRetentionHours == null || staleRetentionHours < 0) {
            staleRetentionHours = DEFAULT_STALE_RETENTION_HOURS;
        }
    }

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
