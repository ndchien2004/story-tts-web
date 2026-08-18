package com.storytts.backend.service.ai;

import com.storytts.backend.config.AiAssistantProperties;
import com.storytts.backend.exception.AiQuotaExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Đếm lượt hỏi trong ngày — hàng rào chi phí của trợ lý AI.
 *
 * <h3>Vì sao đếm trong bộ nhớ chứ không trong cơ sở dữ liệu</h3>
 * Đường tạo audio đếm được bằng cách {@code count(*)} bảng {@code audio_files},
 * vì mỗi lượt ở đó để lại một hàng có thật mà nó vốn phải ghi. Trợ lý AI không
 * để lại gì cả — hội thoại không được lưu, và đề bài nói rõ là chưa cần lưu.
 * Dựng một bảng chỉ để đếm nghĩa là dựng một bảng, một migration, một entity và
 * một repository cho một con số reset mỗi nửa đêm.
 *
 * <p><b>Cái giá của lựa chọn ấy, nói thẳng:</b> con số này thuộc về một tiến
 * trình. Chạy hai bản ứng dụng song song thì mỗi bản có một bộ đếm riêng, và
 * hạn mức thật thành ra gấp đôi. Với một bản triển khai một tiến trình — đúng
 * hình dạng hiện tại của dự án — thì nó chính xác. Khi nào phải chạy nhiều bản,
 * đây là chỗ cần đổi, và đổi bằng cách thay đúng lớp này.
 *
 * <h3>Trần chung là cầu dao cuối</h3>
 * Hạn mức từng người ngăn một người tiêu hết phần của cả ngày. Trần chung ngăn
 * một trăm người mới đăng ký làm đúng việc ấy.
 */
@Component
@Slf4j
public class AssistantQuota {

    /** Hạn mức tính theo ngày ở Việt Nam, không theo UTC — như mọi hạn mức khác. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Trần số người được nhớ cùng lúc.
     *
     * <p>Bộ đếm tự dọn theo ngày, nhưng chỉ dọn <i>khi có người hỏi lại</i> —
     * một người hỏi một câu rồi biến mất thì hàng của họ nằm lại. Trần này là
     * chỗ chặn đường ấy tích thành rò rỉ bộ nhớ trên một máy chủ chạy nhiều
     * tháng: chạm trần thì dọn sạch những hàng của ngày cũ.
     */
    private static final int MAX_TRACKED_USERS = 10_000;

    private final AiAssistantProperties properties;

    private final Map<Long, DayCounter> perUser = new ConcurrentHashMap<>();
    private final DayCounter global = new DayCounter();

    public AssistantQuota(AiAssistantProperties properties) {
        this.properties = properties;
    }

    /**
     * Còn bao nhiêu lượt hôm nay, không tiêu lượt nào.
     *
     * @return {@code null} khi hạn mức là không giới hạn
     */
    public Integer remainingFor(long userId, boolean vip) {
        int limit = properties.dailyQuotaFor(vip);
        if (limit < 0) {
            return null;
        }
        int mine = Math.max(limit - counterFor(userId).valueToday(), 0);

        // Kẹp bởi trần chung: hứa 5 lượt trong khi cả hệ thống chỉ còn 1 là một
        // lời hứa sai, và người đọc sẽ phát hiện ra đúng vào lúc bấm.
        return Math.min(mine, globalRemaining());
    }

    /**
     * Xin một lượt, và tiêu nó nếu còn.
     *
     * <p>Gọi <i>trước</i> khi gọi Gemini. Một lượt bị tính cho một lời gọi hỏng
     * là chuyện chấp nhận được; một lời gọi tính tiền mà không ai đếm thì không.
     *
     * @throws AiQuotaExceededException hết phần của người này, hoặc của cả ngày
     */
    public void consume(long userId, boolean vip) {
        int globalLimit = properties.dailyQuotaGlobal();
        if (globalLimit >= 0 && global.valueToday() >= globalLimit) {
            throw new AiQuotaExceededException(AiQuotaExceededException.Scope.GLOBAL, globalLimit);
        }

        int limit = properties.dailyQuotaFor(vip);
        DayCounter mine = counterFor(userId);
        if (limit >= 0 && mine.valueToday() >= limit) {
            throw new AiQuotaExceededException(AiQuotaExceededException.Scope.USER, limit);
        }

        mine.increment();
        global.increment();
    }

    private int globalRemaining() {
        int limit = properties.dailyQuotaGlobal();
        return limit < 0 ? Integer.MAX_VALUE : Math.max(limit - global.valueToday(), 0);
    }

    private DayCounter counterFor(long userId) {
        if (perUser.size() >= MAX_TRACKED_USERS) {
            pruneStale();
        }
        return perUser.computeIfAbsent(userId, ignored -> new DayCounter());
    }

    /** Bỏ những hàng thuộc về một ngày đã qua; chúng đằng nào cũng đếm lại từ 0. */
    private void pruneStale() {
        LocalDate today = LocalDate.now(ZONE);
        int before = perUser.size();
        perUser.values().removeIf(counter -> !counter.isOn(today));
        log.debug("Dọn bộ đếm lượt hỏi AI: {} → {}", before, perUser.size());
    }

    /**
     * Một con số kèm cái ngày nó thuộc về.
     *
     * <p>Không có tác vụ hẹn giờ nào reset lúc nửa đêm. Ngày được xét ngay lúc
     * đọc: sang ngày mới thì con số cũ bị bỏ đi tại chỗ. Một bộ hẹn giờ ở đây
     * sẽ là một luồng nữa phải nuôi, để làm đúng việc mà một phép so sánh ngày
     * đã làm xong.
     */
    private static final class DayCounter {

        private final AtomicInteger count = new AtomicInteger();
        private volatile LocalDate day = LocalDate.now(ZONE);

        synchronized int valueToday() {
            rollIfNeeded();
            return count.get();
        }

        synchronized void increment() {
            rollIfNeeded();
            count.incrementAndGet();
        }

        boolean isOn(LocalDate date) {
            return day.equals(date);
        }

        private void rollIfNeeded() {
            LocalDate today = LocalDate.now(ZONE);
            if (!day.equals(today)) {
                day = today;
                count.set(0);
            }
        }
    }
}
