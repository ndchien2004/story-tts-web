package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.security.ratelimit.TokenBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hàng rào tần suất của việc gửi tin hỗ trợ, đếm theo <b>tài khoản</b>.
 *
 * <h3>Vì sao cần một hàng rào thứ hai</h3>
 * {@code RateLimitFilter} đã có sẵn và vẫn có hiệu lực với đường REST, nhưng nó
 * không đủ ở đây vì hai lý do độc lập:
 *
 * <ol>
 *   <li><b>Nó không nhìn thấy WebSocket.</b> Chuỗi filter chạy đúng một lần cho
 *       lần bắt tay; sau đó hàng nghìn khung tin đi qua cùng một kết nối ấy mà
 *       không có filter nào chạy lại. Một hàng rào chỉ đếm request là một hàng
 *       rào không đếm gì cả với giao thức này.</li>
 *   <li><b>Nó đếm theo địa chỉ mạng.</b> Cách ấy đúng cho cửa đăng nhập, nơi
 *       chưa biết ai đang gọi. Ở đây thì đã biết — và đếm theo IP có hai chỗ
 *       sai ngược nhau: nhiều người sau cùng một NAT dùng chung một gáo, còn
 *       một người có sẵn vài IP thì lách được.</li>
 * </ol>
 *
 * <h3>Khóa là tài khoản, không phải kết nối</h3>
 * Đây là điểm chính. Đếm theo kết nối nghĩa là mở thêm một tab là nhân đôi hạn
 * mức — tức là hàng rào tự vô hiệu hóa chính nó bằng đúng thứ mà nó phải chịu
 * đựng (nhiều tab là chuyện bình thường). Khóa theo tài khoản giữ cho mười tab
 * và một tab có cùng một mức.
 *
 * <h3>Bộ đếm nằm trong bộ nhớ, và điều đó chấp nhận được</h3>
 * Cùng đánh đổi mà {@code RateLimitFilter} đã ghi rõ: chạy nhiều bản ứng dụng
 * song song thì mỗi bản có hàng rào riêng và mức thật rộng gấp số bản. Khác với
 * hạn mức gọi AI — thứ quyết định một hóa đơn nên phải bền trong cơ sở dữ liệu —
 * con số ở đây chỉ quyết định <i>nhịp</i>, và mất nhịp trong một khoảnh khắc
 * khởi động lại không để lại hậu quả nào tích lũy.
 *
 * <h3>Tin hệ thống không đi qua đây</h3>
 * "Quản trị viên đã đóng cuộc trò chuyện" do máy chủ sinh ra, nên đếm nó vào hạn
 * mức của người vừa bấm nút là phạt họ vì một việc họ không làm.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportRateLimiter {

    /**
     * Trần số gáo giữ cùng lúc.
     *
     * <p>Mỗi tài khoản từng gửi một tin là một gáo, và gáo chỉ bị bỏ khi đã đầy
     * trở lại. Con số này là chỗ chặn đường một máy chủ chạy nhiều tháng tích
     * dần hàng chục nghìn mục — cùng lý do và cùng cách xử lý với
     * {@code RateLimitFilter.MAX_BUCKETS}.
     */
    private static final int MAX_BUCKETS = 5_000;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final SupportProperties properties;

    private final Map<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Xin phép gửi một tin.
     *
     * @return true nếu được phép; false nghĩa là bên gọi phải từ chối bằng
     *         {@code SUPPORT_RATE_LIMITED} và <b>không</b> ghi gì xuống cơ sở dữ
     *         liệu — một tin bị chặn không được để lại dấu vết nào, nếu không
     *         thì hàng rào đã không chặn được thứ nó sinh ra để chặn
     */
    public boolean tryAcquire(Long userId) {
        if (properties.sendPerMinute() <= 0) {
            // 0 hoặc âm = tắt hàng rào. Cùng quy ước với app.ratelimit.enabled.
            return true;
        }
        if (buckets.size() >= MAX_BUCKETS) {
            prune();
        }
        TokenBucket bucket = buckets.computeIfAbsent(userId,
                ignored -> new TokenBucket(properties.sendPerMinute(), WINDOW));

        boolean allowed = bucket.tryConsume() == 0;
        if (!allowed) {
            // Ghi id chứ không ghi nội dung: một dòng nhật ký về việc chặn không
            // có lý do gì để chở theo câu người ta đang gõ.
            log.warn("RATE_LIMITED ho-tro: người {} gửi quá nhanh", userId);
        }
        return allowed;
    }

    /**
     * Bỏ những gáo đã đầy trở lại.
     *
     * <p>Một gáo đầy y hệt một gáo chưa từng tồn tại, nên đây là phép dọn không
     * làm mất thông tin nào: người đang bị chặn thì gáo còn vơi, và gáo ấy ở lại.
     */
    private void prune() {
        int before = buckets.size();
        buckets.values().removeIf(TokenBucket::isFull);
        log.info("Dọn bộ đếm tần suất hộp thư hỗ trợ: {} → {}", before, buckets.size());
    }

    /** Số gáo đang giữ — dùng cho kiểm thử. */
    public int trackedUsers() {
        return buckets.size();
    }
}
