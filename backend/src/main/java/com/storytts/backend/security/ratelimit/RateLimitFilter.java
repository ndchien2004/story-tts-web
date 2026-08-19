package com.storytts.backend.security.ratelimit;

import com.storytts.backend.security.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <h2>Chặn kẻ bắn request dồn dập, trước khi bất cứ gì đắt tiền chạy</h2>
 *
 * Trước lớp này, toàn bộ backend không có một giới hạn tần suất nào. Ba hậu quả
 * cụ thể, không phải giả thuyết:
 *
 * <ol>
 *   <li>Cửa đăng nhập cho dò mật khẩu không giới hạn. Và vì mỗi lần thử là một
 *       phép BCrypt — cố ý chậm — nó đồng thời là cách rẻ nhất để bào hết hai
 *       mươi luồng Tomcat: kẻ gọi trả một request, máy chủ trả một trăm mili
 *       giây CPU.</li>
 *   <li>Form đăng ký và form quên mật khẩu gửi thư thật. Trần 60 giây sẵn có
 *       tính theo địa chỉ email, nên đổi email là gửi tiếp — đủ để đốt sạch hạn
 *       500 lá một ngày của hộp Gmail trong vài phút, và biến trang web thành
 *       công cụ dội thư vào hòm thư người khác.</li>
 *   <li>Ô bình luận không có gì cản việc gửi liên tục.</li>
 * </ol>
 *
 * <h3>Vị trí trong chuỗi filter</h3>
 * Đứng <b>trước</b> {@code JwtAuthenticationFilter}, và đó là điểm chính: mỗi
 * request mang token đều tốn một câu SELECT để nạp lại người dùng. Chặn sau lớp
 * ấy nghĩa là kẻ tấn công vẫn bắt được cơ sở dữ liệu làm việc cho mình.
 *
 * <h3>Khóa là địa chỉ IP, và điều đó có giới hạn</h3>
 * Chưa xác thực thì chưa biết ai đang gọi, nên thứ duy nhất còn lại là địa chỉ
 * mạng. Nói thẳng hai chỗ yếu: nhiều người sau cùng một NAT dùng chung một gáo,
 * và một kẻ có sẵn dải IP thì lách được. Cái thứ nhất được bù bằng việc đặt mức
 * rộng hơn hẳn nhịp của người dùng thật; cái thứ hai được bù bằng một hàng rào
 * khác hẳn — bộ đếm đăng nhập sai theo <i>tài khoản</i> trong
 * {@code AuthService}, thứ không quan tâm request đến từ đâu.
 *
 * <h3>Bộ đếm nằm trong bộ nhớ</h3>
 * Một tiến trình, một bảng băm. Chạy nhiều bản ứng dụng song song thì mỗi bản
 * có hàng rào riêng và mức thật rộng gấp số bản — cùng đánh đổi mà
 * {@code AssistantQuota} từng chọn rồi phải bỏ. Khác biệt: ở đó con số quyết
 * định một hóa đơn nên nó phải bền, còn ở đây nó chỉ quyết định nhịp, và mất
 * nhịp trong một khoảnh khắc khởi động lại không để lại hậu quả nào tích lũy.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Trần số gáo giữ cùng lúc.
     *
     * <p>Mỗi địa chỉ IP lạ là một gáo mới, nên đây là chỗ chặn đường một đợt tấn
     * công từ nhiều nguồn biến thành cạn bộ nhớ — thứ mà chính lớp này sinh ra
     * để ngăn.
     */
    private static final int MAX_BUCKETS = 20_000;

    private final RateLimitRules rules;
    private final ApiErrorWriter errorWriter;
    private final boolean enabled;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitRules rules, ApiErrorWriter errorWriter,
                           @Value("${app.ratelimit.enabled:true}") boolean enabled) {
        this.rules = rules;
        this.errorWriter = errorWriter;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Preflight của CORS không bao giờ được chặn: trình duyệt coi một lời từ
        // chối ở đây là "máy chủ này không cho phép trang của bạn gọi tới", và cả
        // giao diện ngừng hoạt động chứ không riêng lời gọi bị chặn.
        if (!enabled || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = rules.ruleFor(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.name() + '|' + clientAddress(request);
        long waitNanos = bucketFor(key, rule).tryConsume();

        if (waitNanos > 0) {
            long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos));
            log.warn("Chặn vì quá tần suất: nhóm={} ip={} đường={} chờ={}s",
                    rule.name(), clientAddress(request), request.getRequestURI(), retryAfter);

            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMITED", rule.message());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private TokenBucket bucketFor(String key, RateLimitRule rule) {
        if (buckets.size() >= MAX_BUCKETS) {
            prune();
        }
        return buckets.computeIfAbsent(key,
                ignored -> new TokenBucket(rule.capacity(), rule.window()));
    }

    /**
     * Bỏ những gáo đã đầy trở lại.
     *
     * <p>Một gáo đầy y hệt một gáo chưa từng tồn tại, nên đây là phép dọn không
     * làm mất thông tin nào — kẻ đang bị chặn thì gáo còn vơi, và gáo ấy ở lại.
     */
    private void prune() {
        int before = buckets.size();
        buckets.values().removeIf(TokenBucket::isFull);
        log.info("Dọn bộ đếm tần suất: {} → {}", before, buckets.size());
    }

    /**
     * Địa chỉ của người gọi, đọc qua lớp proxy đứng trước.
     *
     * <p>Trên Render (và mọi nền tảng tương tự) ứng dụng không nhận kết nối trực
     * tiếp: {@code getRemoteAddr()} luôn trả về địa chỉ của bộ cân bằng tải, nên
     * dùng thẳng nó là gộp toàn bộ Internet vào một gáo duy nhất — hàng rào sẽ
     * chặn tất cả mọi người cùng lúc thay vì chặn một ai.
     *
     * <p>Nhịp đầu tiên của {@code X-Forwarded-For} là địa chỉ người gọi. Header
     * này giả được, và điều đó được chấp nhận có ý thức: nó chỉ tin được vì mọi
     * lưu lượng đều buộc phải đi qua proxy của nhà cung cấp, thứ ghi đè lại
     * header trước khi chuyển tiếp. Chạy sau một proxy không làm việc ấy thì
     * dòng này phải đổi — nên nó nằm ở một chỗ duy nhất.
     */
    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma < 0 ? forwarded : forwarded.substring(0, comma);
            String trimmed = first.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "khong-ro" : remote;
    }

}
