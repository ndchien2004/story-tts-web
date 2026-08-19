package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Quãng nghỉ áp lên một tài khoản bị gõ sai mật khẩu quá nhiều lần.
 *
 * <p>Hàng rào thứ hai của cửa đăng nhập. Cái thứ nhất —
 * {@code RateLimitFilter} — đếm theo địa chỉ mạng, thứ duy nhất biết được khi
 * chưa ai đăng nhập; nhưng kẻ có sẵn một dải địa chỉ chỉ cần đổi nguồn sau mỗi
 * mười lần thử. Bộ đếm ở đây gắn với chính tài khoản đang bị nhắm tới, thứ
 * không đổi được.
 *
 * @param maxFailures số lần sai <b>liên tiếp</b> trước khi nghỉ; một lần đúng
 *                    đưa bộ đếm về 0
 * @param lockFor     quãng nghỉ, tự hết chứ không cần ai mở
 */
@ConfigurationProperties(prefix = "app.login-throttle")
public record LoginThrottleProperties(int maxFailures, Duration lockFor) {

    public LoginThrottleProperties {
        // Thiếu cả khối cấu hình vẫn phải ra một bộ ngưỡng dùng được: một bản
        // clone chưa điền .env không được phép chạy với cửa đăng nhập mở toang.
        maxFailures = maxFailures <= 0 ? 10 : maxFailures;
        lockFor = lockFor == null || lockFor.isZero() ? Duration.ofMinutes(15) : lockFor;
    }
}
