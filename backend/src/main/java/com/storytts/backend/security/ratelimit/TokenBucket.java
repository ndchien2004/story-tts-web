package com.storytts.backend.security.ratelimit;

import java.time.Duration;

/**
 * Một gáo token cho một khóa: cho phép {@code capacity} lượt, rồi rót lại đều
 * đặn trong {@code window}.
 *
 * <h3>Vì sao là gáo token chứ không phải đếm theo cửa sổ</h3>
 * Đếm theo cửa sổ cố định ("10 lượt mỗi phút, reset lúc đầu phút") dễ viết hơn
 * nhưng cho qua gấp đôi ở ranh giới: mười lượt lúc 12:00:59 và mười lượt nữa
 * lúc 12:01:00 là hai mươi lượt trong hai giây. Với một hàng rào dựng để chặn
 * việc dò mật khẩu thì đó đúng là chỗ nó cần chặt nhất.
 *
 * <p>Gáo rót đều: mỗi token quay lại sau {@code window / capacity}, nên không
 * có thời điểm nào trong ngày lỏng hơn thời điểm khác. Bên gọi vẫn được phép
 * dùng liền một lúc cả gáo — một lần tải trang gọi vài API cùng lúc là bình
 * thường, và đó chính là thứ "burst" mà gáo cho phép còn cửa sổ trượt thì không.
 *
 * <p>Số học chạy trên {@code System.nanoTime()} chứ không phải đồng hồ tường:
 * đồng hồ tường nhảy khi máy chủ đồng bộ giờ, và một cú nhảy lùi sẽ đóng băng
 * hàng rào cho tới khi thời gian đuổi kịp.
 */
final class TokenBucket {

    private final int capacity;

    /** Số nano giây để rót lại đúng một token. */
    private final double nanosPerToken;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(int capacity, Duration window) {
        this.capacity = capacity;
        this.nanosPerToken = (double) window.toNanos() / capacity;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Lấy một token nếu còn.
     *
     * @return số nano giây phải chờ tới token tiếp theo, hoặc 0 nếu lấy được
     */
    synchronized long tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return (long) Math.ceil((1 - tokens) * nanosPerToken);
    }

    /**
     * Gáo đã đầy trở lại — không còn gì để nhớ về khóa này.
     *
     * <p>Đây là điều kiện để dọn: một gáo đầy y hệt một gáo chưa từng tồn tại,
     * nên bỏ nó đi không làm mất thông tin nào.
     */
    synchronized boolean isFull() {
        refill();
        return tokens >= capacity;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + elapsed / nanosPerToken);
        lastRefillNanos = now;
    }
}
