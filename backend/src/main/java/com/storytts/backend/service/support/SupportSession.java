package com.storytts.backend.service.support;

import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.security.ratelimit.TokenBucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

/**
 * Một kết nối WebSocket đang mở, và những gì máy chủ biết về nó.
 *
 * <h3>Danh tính kết nối ≠ danh tính người dùng</h3>
 * Đây là phân biệt mà cả tính năng dựa vào. Một người có thể có bốn kết nối —
 * hai tab, một điện thoại, một máy tính khác — và cả bốn mang cùng
 * {@link #userId()}. Hệ quả cụ thể:
 *
 * <pre>
 *   gửi một tin       → một bản ghi trong cơ sở dữ liệu (danh tính người)
 *   nhận một khung tin → bốn lượt gửi đi (danh tính kết nối)
 * </pre>
 *
 * Nhầm hai thứ này theo chiều thứ nhất là tạo ra tin nhắn trùng; theo chiều thứ
 * hai là một tab không bao giờ thấy thứ tab kia vừa gửi.
 *
 * <h3>{@link #role} là ảnh chụp lúc bắt tay, không phải nguồn sự thật</h3>
 * Nó dùng để <i>định tuyến</i> — khung tin nào đi tới kết nối nào — chứ không
 * bao giờ dùng để <i>cho phép</i>. Quyền được kiểm lại từ cơ sở dữ liệu ở mỗi
 * lệnh (xem {@code SupportService.resolveActor}), nên một quản trị viên bị hạ
 * quyền giữa chừng không dùng được kết nối cũ để làm việc của quản trị viên.
 * Khi phát hiện lệch, kết nối bị đóng — xem {@code SupportWebSocketHandler}.
 *
 * <h3>Vì sao mọi lượt gửi đi qua một khóa</h3>
 * {@code WebSocketSession} của Spring <b>không</b> an toàn khi nhiều luồng cùng
 * ghi: hai lượt gửi song song có thể đan xen byte của nhau và làm hỏng khung
 * tin trên đường dây. Ở đây điều đó không phải giả thuyết — một tin nhắn mới,
 * một lời báo đã đọc và một nhịp tim hoàn toàn có thể đi ra cùng lúc từ ba
 * luồng khác nhau.
 *
 * <p>Khóa được giữ đúng trong lúc ghi, và không có lời gọi cơ sở dữ liệu nào
 * bên trong nó.
 */
@Slf4j
public final class SupportSession {

    private final WebSocketSession raw;
    private final Long userId;
    private final SupportSenderRole role;
    private final Instant connectedAt;

    /**
     * Lần cuối nghe thấy gì từ đầu bên kia — một khung tin, hoặc một pong.
     *
     * <p>{@code volatile} vì nó được ghi trên luồng nhận khung tin và đọc trên
     * luồng nhịp tim. Một {@code long} thường thì luồng nhịp tim có thể mãi
     * không thấy giá trị mới và đi đóng một kết nối còn sống.
     *
     * <p>Đo bằng {@code System.nanoTime()} chứ không phải đồng hồ tường: một lần
     * đồng bộ giờ nhảy lùi sẽ khiến mọi kết nối trông như vừa mới hoạt động, và
     * nhảy tới sẽ giết sạch chúng.
     */
    private volatile long lastSeenNanos;

    private final Object sendLock = new Object();

    /**
     * Trần số khung tin một kết nối được gửi lên trong một phút.
     *
     * <h3>Vì sao cần hàng rào này bên cạnh hàng rào theo tài khoản</h3>
     * {@code SupportRateLimiter} đếm số <i>tin nhắn</i> theo <i>người</i>, và nó
     * chỉ chạy sau khi khung tin đã được phân tích. Hàng rào ở đây đếm số
     * <i>khung tin</i> theo <i>kết nối</i>, và nó chạy <b>trước</b> khi phân
     * tích. Hai chỗ khác nhau, hai thứ được bảo vệ khác nhau:
     *
     * <pre>
     *   theo người, sau phân tích  → sức ép lên cơ sở dữ liệu
     *   theo kết nối, trước phân tích → sức ép lên CPU và luồng của máy chủ
     * </pre>
     *
     * Không có hàng rào thứ hai thì một kẻ gửi mười nghìn khung tin rác mỗi giây
     * vẫn bị từ chối <i>từng cái một</i> — nhưng máy chủ đã phải đọc và phân
     * tích cả mười nghìn, trên một hộp có hai mươi luồng.
     *
     * <p>240 lượt mỗi phút là bốn lượt mỗi giây liên tục, và cho phép dùng liền
     * cả gáo. Một người gõ nhanh nhất cũng không tới một tin mỗi giây; con số
     * này rộng gấp nhiều lần nhịp thật để nó không bao giờ chạm vào ai đang dùng
     * bình thường.
     */
    private static final int FRAMES_PER_MINUTE = 240;

    private final TokenBucket frameBudget =
            new TokenBucket(FRAMES_PER_MINUTE, Duration.ofMinutes(1));

    SupportSession(WebSocketSession raw, Long userId, SupportSenderRole role) {
        this.raw = raw;
        this.userId = userId;
        this.role = role;
        this.connectedAt = Instant.now();
        this.lastSeenNanos = System.nanoTime();
    }

    /** Kết nối này còn được phép gửi thêm một khung tin không. */
    boolean allowFrame() {
        return frameBudget.tryConsume() == 0;
    }

    public String id() {
        return raw.getId();
    }

    public Long userId() {
        return userId;
    }

    public SupportSenderRole role() {
        return role;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    /** Ghi nhận vừa nghe thấy gì đó từ đầu bên kia. */
    void touch() {
        lastSeenNanos = System.nanoTime();
    }

    /**
     * Gửi một khung tin đã tuần tự hóa sẵn.
     *
     * <p>Nhận vào {@code String} chứ không nhận đối tượng, và đó là chủ ý: một
     * khung tin đi tới hai mươi kết nối được dựng <i>một</i> lần ở tầng trên
     * thay vì hai mươi lần ở đây. Xem {@code SupportSocketRegistry}.
     *
     * @return false khi kết nối đã chết — bên gọi dọn sổ, không ghi log lỗi.
     *         Trình duyệt đã đi khỏi mà máy chủ chưa kịp biết là chuyện thường,
     *         không phải sự cố.
     */
    boolean send(String payload) {
        synchronized (sendLock) {
            if (!raw.isOpen()) {
                return false;
            }
            try {
                raw.sendMessage(new TextMessage(payload));
                return true;
            } catch (IOException | IllegalStateException ex) {
                log.debug("Không gửi được khung tin hỗ trợ tới kết nối {}: {}",
                        raw.getId(), ex.getMessage());
                return false;
            }
        }
    }

    /**
     * Nhịp tim ở tầng giao thức, không phải một khung tin của ứng dụng.
     *
     * <p>Ping/pong là một phần của chính WebSocket, nên trình duyệt trả lời tự
     * động và không cần một dòng JavaScript nào. Một khung tin ứng dụng
     * ({@code {"type":"ping"}}) thì cần, và nó sẽ im lặng ngừng hoạt động ở bất
     * kỳ máy khách nào quên viết phần trả lời.
     */
    boolean ping() {
        synchronized (sendLock) {
            if (!raw.isOpen()) {
                return false;
            }
            try {
                raw.sendMessage(new PingMessage(ByteBuffer.allocate(0)));
                return true;
            } catch (IOException | IllegalStateException ex) {
                return false;
            }
        }
    }

    void close(CloseStatus status) {
        try {
            // Không giữ sendLock ở đây: close() có thể chờ đầu bên kia, và giữ
            // khóa trong lúc chờ mạng sẽ chặn luôn luồng đang muốn gửi khung
            // tin cuối cùng.
            raw.close(status);
        } catch (IOException | IllegalStateException ex) {
            log.debug("Đóng kết nối {} không sạch: {}", raw.getId(), ex.getMessage());
        }
    }

    boolean isOpen() {
        return raw.isOpen();
    }

    /**
     * Đã quá lâu không nghe thấy gì.
     *
     * <p>Đây là cách duy nhất phát hiện một kết nối đã chết mà TCP chưa báo:
     * điện thoại vào vùng không sóng, laptop gập lại, một proxy lặng lẽ bỏ kết
     * nối ở giữa. Không có phép này thì sổ kết nối tích dần những mục không bao
     * giờ nhận được gì và trần kết nối chạm tới vì toàn kết nối ma.
     */
    boolean isIdle(Duration timeout) {
        return System.nanoTime() - lastSeenNanos > timeout.toNanos();
    }

    /** Đã sống quá hạn cho phép của một lần bắt tay. Xem {@code SupportProperties}. */
    boolean isExpired(Duration maxLifetime) {
        return connectedAt.plus(maxLifetime).isBefore(Instant.now());
    }
}
