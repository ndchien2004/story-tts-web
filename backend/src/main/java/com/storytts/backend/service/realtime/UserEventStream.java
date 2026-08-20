package com.storytts.backend.service.realtime;

import com.storytts.backend.service.notification.NotificationCreated;
import com.storytts.backend.service.notification.NotificationsRead;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.storytts.backend.dto.notification.NotificationDto;

import java.time.Duration;
import java.util.List;

/**
 * Đường một chiều từ máy chủ tới các cửa sổ đang mở của <b>một tài khoản</b>.
 *
 * <h3>Vì sao khóa là người chứ không phải là phiên hay là tab</h3>
 * Một thông báo thuộc về người nhận, không thuộc về cái tab đang mở nó. Đánh
 * khóa theo người là thứ khiến ba yêu cầu khác nhau trở thành một hành vi duy
 * nhất, không cần thêm mã nào:
 *
 * <pre>
 *   hai tab cùng tài khoản   → cả hai đều nằm dưới một khóa → cả hai cùng nhận
 *   điện thoại và máy tính   → như trên
 *   một tab đánh dấu đã đọc  → tin ấy tới mọi tab còn lại   → cái chuông khớp nhau
 * </pre>
 *
 * <h3>Cùng cơ chế với luồng theo chương</h3>
 * Phần sổ sách kết nối là {@link SseHub}, dùng chung với
 * {@link ChapterEventStream}. Đây không phải "hệ thống thời gian thực thứ hai":
 * cùng SSE, cùng {@code AFTER_COMMIT}, cùng cách dọn kết nối chết — chỉ khác
 * khóa và khác loại khung tin. Gộp cả hai vào một khóa duy nhất thì không được:
 * luồng theo chương là đường công khai không đòi đăng nhập, còn đường này chở
 * dữ liệu riêng của một người.
 *
 * <h3>Luồng này không phải nguồn sự thật</h3>
 * Mất kết nối, tab ngủ, máy chủ khởi động lại — hộp thư vẫn nguyên trong cơ sở
 * dữ liệu, và trình duyệt lấy lại phần nó bỏ lỡ bằng REST ở mỗi lần nối lại.
 * Nên ở đây không có hàng đợi tin chưa gửi, không có số thứ tự sự kiện, và
 * không có cơ chế gửi lại: khung tin nào không đi được thì thôi.
 */
@Component
@Slf4j
public class UserEventStream {

    /**
     * Dài hơn hạn của luồng theo chương, vì nó phục vụ một thứ khác.
     *
     * <p>Kết nối theo chương sống đúng một lượt đọc; kết nối này sống suốt phiên
     * đăng nhập, kể cả khi người dùng đang ở trang chủ và không làm gì. Mười lăm
     * phút một lần mở lại là hợp lý cho cái thứ nhất và là phiền cho cái thứ
     * hai — mỗi lần mở lại ở đây còn tốn thêm một lượt xin vé, xem
     * {@code NotificationStreamTickets}.
     */
    private static final Duration EMITTER_TTL = Duration.ofMinutes(30);

    /**
     * Thấp hơn trần của luồng theo chương, và có lý do.
     *
     * <p>Một người mở ba tab là ba kết nối ở đây, trong khi ở luồng kia họ chỉ
     * chiếm chỗ khi đang thật sự đọc một chương. Trần này là để phần "tiện lợi"
     * không ăn hết bộ nhớ của phần "đang phục vụ người đọc"; chạm trần thì cái
     * chuông vẫn chạy, chỉ là nó cập nhật ở lần tải trang thay vì ngay lập tức.
     */
    private static final int MAX_SUBSCRIBERS = 300;

    /** Nhịp giữ kết nối, ngắn hơn hạn chờ của mọi proxy thường gặp. */
    private static final long HEARTBEAT_MS = 25_000L;

    private final SseHub<Long> hub = new SseHub<>("thong-bao", MAX_SUBSCRIBERS, EMITTER_TTL);

    /**
     * Một cửa sổ của người này bắt đầu lắng nghe.
     *
     * <p>Bên gọi phải xác định được {@code userId} từ phía máy chủ trước khi gọi
     * vào đây — xem {@code NotificationController}. Lớp này không kiểm quyền và
     * cũng không nên: nó không biết gì về HTTP.
     *
     * @return null khi đã chạm trần; bên gọi trả 503 và trang vẫn chạy bình thường
     */
    public SseEmitter subscribe(Long userId) {
        return hub.subscribe(userId, "subscribed");
    }

    /**
     * Đẩy một thông báo vừa được ghi xuống mọi cửa sổ của người nhận.
     *
     * <p>{@code AFTER_COMMIT} là điều kiện: gửi sớm hơn thì một giao dịch cuộn
     * ngược vẫn kịp báo "đã hoàn 100 Xu" cho một lần hoàn chưa xảy ra, và người
     * nhận sẽ đi tìm số tiền ấy trong ví.
     *
     * <p>Không ai đang mở cũng không sao — đó là trường hợp thường gặp nhất, và
     * cũng chính là trường hợp mà việc lưu xuống cơ sở dữ liệu sinh ra để lo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreated event) {
        int told = hub.send(event.userId(), "notification",
                new NotificationFrame(event.notification(), event.unread()));

        if (told > 0) {
            log.info("Đẩy thông báo {} tới {} cửa sổ của người {}",
                    event.notification().id(), told, event.userId());
        }
    }

    /**
     * Đồng bộ trạng thái đã đọc giữa các cửa sổ của cùng một tài khoản.
     *
     * <p>Xem {@link NotificationsRead} về lý do việc này phải đi ra ngoài chứ
     * không chỉ nằm trong tab vừa bấm.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsRead(NotificationsRead event) {
        hub.send(event.userId(), "notifications-read",
                new ReadFrame(event.everything(), event.ids(), event.unread()));
    }

    /** Nhịp tim giữ kết nối qua các lớp proxy. Xem {@link SseHub#heartbeat}. */
    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        hub.heartbeat();
    }

    /** Số kết nối đang mở — dùng cho kiểm thử và cho log lúc chạm trần. */
    public int openConnections() {
        return hub.openConnections();
    }

    /**
     * Khung tin mang một thông báo mới.
     *
     * <p>Đi kèm {@code unread} chứ không để trình duyệt tự cộng thêm một: máy chủ
     * là bên biết con số đúng, và một khung tin bị bỏ lỡ sẽ khiến phép cộng ở
     * trình duyệt lệch mãi mãi.
     */
    public record NotificationFrame(NotificationDto notification, long unread) {
    }

    /**
     * Khung tin báo trạng thái đã đọc vừa đổi.
     *
     * @param all    lệnh "đánh dấu tất cả"; khi true thì {@code ids} rỗng
     * @param ids    những thông báo lẻ vừa được đọc
     * @param unread số còn lại chưa đọc, theo máy chủ
     */
    public record ReadFrame(boolean all, List<Long> ids, long unread) {
    }
}
