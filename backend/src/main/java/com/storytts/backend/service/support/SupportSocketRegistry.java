package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.SupportSenderRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sổ kết nối WebSocket của hộp thư hỗ trợ.
 *
 * <h3>Vì sao không dùng lại {@code SseHub}</h3>
 * {@code SseHub} là sổ cho một luồng <b>một chiều</b>, đánh theo một khóa duy
 * nhất, và đó là toàn bộ hình dạng của nó. Ở đây có ba thứ nó không có chỗ để
 * chứa: khung tin phải đi tới <i>hai</i> nhóm khác nhau (chủ luồng và cả phía
 * hỗ trợ) theo hai quy tắc khác nhau, mỗi kết nối phải nhớ vai trò và mốc hoạt
 * động của nó để nhịp tim biết đóng cái nào, và phải có đường đá một tài khoản
 * ra ngay lập tức khi quản trị viên khóa nó.
 *
 * <p>Nhồi ba thứ ấy vào {@code SseHub} sẽ làm hỏng thứ khiến nó đáng tồn tại —
 * nó dùng chung được giữa hai luồng SSE <i>vì</i> nó chỉ biết đúng một việc.
 * Nên phần dùng lại ở đây là <i>lối viết</i>, không phải lớp: cùng cách dọn trên
 * mọi đường kết thúc, cùng bộ đếm có trần, cùng việc bỏ khóa rỗng để một máy chủ
 * chạy lâu không tích dần hàng nghìn {@code Set} trống.
 *
 * <h3>Hai quy tắc định tuyến</h3>
 * <pre>
 *   tới chủ luồng  → đúng những kết nối của userId ấy, và chỉ kết nối vai USER
 *   tới phía hỗ trợ → mọi kết nối vai ADMIN đang mở
 * </pre>
 *
 * Vế thứ hai cố ý rộng: quản trị viên nhìn thấy <i>mọi</i> luồng trong hộp thư
 * của họ, nên một tin mới ở bất kỳ luồng nào cũng là thứ họ được phép biết và
 * cần biết để cập nhật danh sách. Với quy mô của trang này — vài quản trị viên —
 * lọc theo "đang mở luồng nào" sẽ là một sổ đăng ký thứ hai phải giữ đồng bộ,
 * đổi lấy việc tiết kiệm vài khung tin.
 *
 * <p>Điều kiện "chỉ kết nối vai USER" ở vế thứ nhất không thừa: một tài khoản
 * từng là thành viên rồi được nâng lên quản trị viên vẫn còn luồng cũ của mình.
 * Không lọc thì kết nối của họ nhận cả hai bản của cùng một tin nhắn — một bản
 * ẩn danh phía hỗ trợ và một bản đầy đủ — và giao diện vẽ nó hai lần.
 *
 * <h3>Kết nối không giữ kết nối cơ sở dữ liệu</h3>
 * Cả lớp này không có một câu truy vấn nào, và đó là điểm được kiểm lại có chủ
 * đích: một WebSocket sống hàng giờ, còn pool ở đây chỉ có mười kết nối. Việc
 * đọc ghi cơ sở dữ liệu chỉ xảy ra trong lúc xử lý <i>một</i> khung tin, đúng
 * như một request HTTP, rồi trả kết nối lại ngay.
 *
 * <h3>Giới hạn khi chạy nhiều bản ứng dụng</h3>
 * Sổ này nằm trong bộ nhớ của một tiến trình. Người dùng nối vào bản A và quản
 * trị viên nối vào bản B thì tin nhắn <b>vẫn được ghi và vẫn tới nơi</b> — nó
 * nằm trong cơ sở dữ liệu, và mỗi lần nối lại hay quay lại tab đều kéo về phần
 * bỏ lỡ — nhưng nó không tới <i>ngay lập tức</i>. Trang này hiện chạy một bản
 * (xem {@code render.yaml}), nên đó là một giới hạn đã biết chứ không phải một
 * lỗi đang có. Cách vá khi cần: một lớp chuyển tiếp Redis pub/sub đứng giữa
 * {@code SupportRealtime} và lớp này — thêm một lớp, không sửa lớp nào.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportSocketRegistry {

    private final SupportProperties properties;

    /** Mọi kết nối của một tài khoản, kể cả kết nối vai quản trị viên. */
    private final Map<Long, Set<SupportSession>> byUser = new ConcurrentHashMap<>();

    /** Tập con: những kết nối vai quản trị viên. Giữ riêng để khỏi quét cả bản đồ. */
    private final Set<SupportSession> adminSessions = new CopyOnWriteArraySet<>();

    private final AtomicInteger openCount = new AtomicInteger();

    /* ------------------------------------------------------------------ */
    /* Vào sổ, ra sổ                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Ghi một kết nối vừa bắt tay xong vào sổ.
     *
     * <h3>Hai cái trần, và vì sao cả hai đều cần</h3>
     * Trần của cả máy chủ chặn một đợt nối lại dồn dập từ nhiều người biến thành
     * cạn bộ nhớ. Trần theo tài khoản chặn <i>một</i> người làm đúng việc ấy một
     * mình — và nó cũng là thứ giữ cho trần kia không bao giờ bị một tài khoản
     * chiếm hết, tức là chặn kiểu từ chối dịch vụ nhắm vào người khác.
     *
     * <p>Chạm trần thì từ chối kết nối <i>mới</i> chứ không đá kết nối cũ ra:
     * người đang trao đổi dở không đáng bị mất đường vì có người mở thêm tab.
     * Cùng lựa chọn đã ghi ở {@code SseHub}.
     *
     * @return null khi đã chạm một trong hai trần — bên gọi đóng kết nối kèm
     *         {@link SupportCloseCodes#TOO_MANY_CONNECTIONS}
     */
    SupportSession register(WebSocketSession raw, Long userId, SupportSenderRole role) {
        if (openCount.get() >= properties.maxSessions()) {
            log.warn("Từ chối kết nối hỗ trợ của người {}: máy chủ đã có {} kết nối",
                    userId, openCount.get());
            return null;
        }

        Set<SupportSession> mine = byUser.computeIfAbsent(userId,
                ignored -> new CopyOnWriteArraySet<>());
        if (mine.size() >= properties.maxSessionsPerUser()) {
            log.warn("Từ chối kết nối hỗ trợ của người {}: đã mở {} kết nối",
                    userId, mine.size());
            dropIfEmpty(userId, mine);
            return null;
        }

        SupportSession session = new SupportSession(raw, userId, role);
        mine.add(session);
        openCount.incrementAndGet();
        if (role == SupportSenderRole.ADMIN) {
            adminSessions.add(session);
        }

        log.info("WEBSOCKET_CONNECTED ho-tro: kết nối {} của người {} vai {}; đang mở {}",
                session.id(), userId, role, openCount.get());
        return session;
    }

    /**
     * Bỏ một kết nối khỏi sổ.
     *
     * <p>Bỏ qua lần gọi thứ hai, và điều đó cần thiết: đóng kết nối và callback
     * {@code afterConnectionClosed} hoàn toàn có thể cùng dẫn tới đây, và trừ bộ
     * đếm hai lần cho một kết nối sẽ làm trần kết nối trôi dần thành vô nghĩa.
     * Cùng lý lẽ với {@code SseHub.remove}.
     */
    void unregister(SupportSession session) {
        if (session == null) {
            return;
        }
        Set<SupportSession> mine = byUser.get(session.userId());
        if (mine == null || !mine.remove(session)) {
            return;
        }
        openCount.decrementAndGet();
        adminSessions.remove(session);
        dropIfEmpty(session.userId(), mine);

        log.info("WEBSOCKET_DISCONNECTED ho-tro: kết nối {} của người {}; còn {} kết nối",
                session.id(), session.userId(), openCount.get());
    }

    /* ------------------------------------------------------------------ */
    /* Gửi                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Gửi tới mọi cửa sổ vai người đọc của một tài khoản.
     *
     * <p>Nhận vào chuỗi đã tuần tự hóa sẵn: một khung tin đi tới bốn cửa sổ được
     * dựng một lần, không phải bốn lần.
     *
     * @return số kết nối đã nhận được khung tin
     */
    int sendToUser(Long userId, String payload) {
        Set<SupportSession> mine = byUser.get(userId);
        if (mine == null || mine.isEmpty()) {
            // Trường hợp thường gặp nhất: không ai đang mở. Đây cũng chính là
            // trường hợp mà việc ghi xuống cơ sở dữ liệu sinh ra để lo.
            return 0;
        }
        int delivered = 0;
        for (SupportSession session : mine) {
            if (session.role() != SupportSenderRole.USER) {
                continue;
            }
            if (session.send(payload)) {
                delivered++;
            } else {
                unregister(session);
            }
        }
        return delivered;
    }

    /** Gửi tới mọi cửa sổ của mọi quản trị viên đang mở. */
    int sendToAdmins(String payload) {
        int delivered = 0;
        for (SupportSession session : adminSessions) {
            if (session.send(payload)) {
                delivered++;
            } else {
                unregister(session);
            }
        }
        return delivered;
    }

    /** Gửi tới đúng một kết nối — dùng cho lời báo nhận, thứ chỉ người gửi cần. */
    boolean sendTo(SupportSession session, String payload) {
        if (session.send(payload)) {
            return true;
        }
        unregister(session);
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Thu hồi                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Đá mọi kết nối của một tài khoản ra ngay lập tức.
     *
     * <h3>Vì sao việc này phải tồn tại</h3>
     * Vì không có nó thì một kết nối đã mở là một lỗ hổng có thật: quản trị viên
     * khóa tài khoản, mọi request HTTP của người ấy bị {@code JwtAuthenticationFilter}
     * chặn ngay — nhưng cái socket vẫn nằm đó và vẫn nhận được tin nhắn mới của
     * luồng hỗ trợ.
     *
     * <p>Cần nói rõ đây là hàng rào <i>thứ hai</i>. Hàng rào thứ nhất là mỗi
     * lệnh đều đọc lại tài khoản từ cơ sở dữ liệu, nên một tài khoản bị khóa
     * không gửi được gì kể cả khi lời gọi này không bao giờ chạy. Cái mà lời gọi
     * này thêm vào là chặn đường <i>nhận</i>, và làm việc ấy ngay lập tức thay
     * vì ở lệnh kế tiếp.
     *
     * <p>Gọi từ {@code AFTER_COMMIT} của lệnh khóa tài khoản — xem
     * {@code SupportRealtime}. Trước khi commit thì một giao dịch cuộn ngược sẽ
     * đá người ta ra vì một lệnh khóa chưa từng xảy ra.
     *
     * @return số kết nối đã bị đóng
     */
    public int revoke(Long userId, CloseStatus status) {
        Set<SupportSession> mine = byUser.get(userId);
        if (mine == null || mine.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (SupportSession session : mine) {
            session.close(status);
            unregister(session);
            closed++;
        }
        if (closed > 0) {
            log.info("Đóng {} kết nối hỗ trợ của người {} vì {}",
                    closed, userId, status.getReason());
        }
        return closed;
    }

    /* ------------------------------------------------------------------ */
    /* Nhịp tim                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Giữ nhịp, và dọn những gì đã chết.
     *
     * <h3>Ba việc, theo thứ tự này</h3>
     * <ol>
     *   <li><b>Hết hạn sống</b> → đóng kèm {@link SupportCloseCodes#RECONNECT}.
     *       Đây là thứ giữ cho "đã xác thực một lần" không thành "được xác thực
     *       mãi mãi": nối lại đòi một cái vé mới, và vé mới đòi một phiên đăng
     *       nhập còn hiệu lực.</li>
     *   <li><b>Im lặng quá lâu</b> → đóng và dọn. Một điện thoại vào vùng không
     *       sóng không gửi tín hiệu nào báo rằng nó đã đi; không có phép này thì
     *       sổ tích dần những mục ma cho tới lúc chạm trần.</li>
     *   <li><b>Còn sống</b> → ping. Nền tảng triển khai đóng một kết nối im lặng
     *       quá lâu, và một kết nối bị đóng sau lưng thì cả hai đầu đều không
     *       biết. Ping ở tầng giao thức nên trình duyệt trả lời tự động.</li>
     * </ol>
     *
     * <p>Chạy trên luồng lập lịch, không đụng cơ sở dữ liệu, và mỗi lượt gửi tự
     * bọc lấy lỗi của nó — một kết nối đã chết không được kéo theo những kết nối
     * còn sống.
     */
    @Scheduled(fixedRateString = "${app.support.heartbeat-interval:25s}")
    void heartbeat() {
        for (Set<SupportSession> sessions : byUser.values()) {
            for (SupportSession session : sessions) {
                if (!session.isOpen()) {
                    unregister(session);
                    continue;
                }
                if (session.isExpired(properties.sessionMaxLifetime())) {
                    session.close(SupportCloseCodes.RECONNECT);
                    unregister(session);
                    continue;
                }
                if (session.isIdle(properties.idleTimeout())) {
                    log.info("Đóng kết nối hỗ trợ {} của người {}: im lặng quá lâu",
                            session.id(), session.userId());
                    session.close(SupportCloseCodes.RECONNECT);
                    unregister(session);
                    continue;
                }
                if (!session.ping()) {
                    unregister(session);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Quan sát                                                            */
    /* ------------------------------------------------------------------ */

    /** Số kết nối đang mở — dùng cho kiểm thử và cho log lúc chạm trần. */
    public int openConnections() {
        return openCount.get();
    }

    /** Số kết nối vai quản trị viên đang mở. */
    public int openAdminConnections() {
        return adminSessions.size();
    }

    /** Số kết nối của một tài khoản — dùng cho kiểm thử trần theo người. */
    public int connectionsOf(Long userId) {
        Set<SupportSession> mine = byUser.get(userId);
        return mine == null ? 0 : mine.size();
    }

    /**
     * Bỏ luôn khóa rỗng.
     *
     * <p>Một máy chủ chạy lâu đi qua hàng nghìn tài khoản, và giữ lại một
     * {@code Set} rỗng cho mỗi tài khoản là một đường rò chậm. {@code remove} có
     * kiểm giá trị để không xóa mất một {@code Set} mà luồng khác vừa thêm kết
     * nối vào.
     */
    private void dropIfEmpty(Long userId, Set<SupportSession> sessions) {
        if (sessions.isEmpty()) {
            byUser.remove(userId, sessions);
        }
    }
}
