package com.storytts.backend.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vé một lần, sống ngắn, để mở luồng thông báo riêng của một người.
 *
 * <h3>Vấn đề</h3>
 * {@code EventSource} của trình duyệt không đặt được header
 * {@code Authorization}. Luồng theo chương né được điều đó bằng cách không cần
 * đăng nhập — nó chỉ chở ba con số công khai. Luồng thông báo thì không né được:
 * nó chở nội dung riêng của một tài khoản, nên nó phải biết mình đang nói với ai.
 *
 * <h3>Vì sao không dùng lại {@code ?access_token=}</h3>
 * Cơ chế ấy đã có sẵn cho thẻ {@code <audio>}, nhưng
 * {@code JwtAuthenticationFilter} cố ý giới hạn nó vào đúng những đường mà một
 * thẻ media trỏ tới, và ghi rõ lý do: token trên URL là token đi vào access log
 * của nhà cung cấp, vào lịch sử trình duyệt, vào ảnh chụp màn hình. Với đường
 * phát audio thì đó là cái giá phải trả. Với đường này thì không — vì có một
 * lựa chọn rẻ hơn.
 *
 * <p>Cái vé dưới đây là lựa chọn ấy. So với việc nới danh sách ngoại lệ kia:
 *
 * <pre>
 *   token phiên trên URL → sống 24 giờ, mở được mọi đường của tài khoản
 *   vé này trên URL      → sống 60 giây, dùng được một lần, mở đúng một luồng
 * </pre>
 *
 * Rò ra ngoài thì cái thứ nhất là mất tài khoản; cái thứ hai là mất một thứ đã
 * hết hạn từ lâu và đã bị tiêu ngay khi kết nối mở.
 *
 * <h3>Vì sao để trong bộ nhớ</h3>
 * Vé sống sáu mươi giây và chỉ có nghĩa với đúng tiến trình đang giữ kết nối
 * SSE ấy — mà kết nối SSE thì vốn đã gắn với một tiến trình. Cho nó vào cơ sở
 * dữ liệu là thêm hai lượt ghi cho mỗi lần mở tab để lưu một thứ sẽ bị xóa
 * trước khi kịp nguội. Máy chủ khởi động lại thì mọi kết nối SSE cũng đứt, và
 * trình duyệt xin vé mới rồi nối lại — đúng đường nó vẫn đi khi mạng chập chờn.
 *
 * <p>Cùng lập luận và cùng hình dạng với {@code TokenBucket} của lớp giới hạn
 * tần suất: một bản đồ trong bộ nhớ, tự dọn, có trần.
 */
@Component
@Slf4j
public class NotificationStreamTickets {

    /**
     * Đủ để mở một kết nối ngay sau khi xin, không đủ để nằm lại ở đâu đó.
     *
     * <p>Trình duyệt xin vé rồi mở {@code EventSource} ngay dòng sau; sáu mươi
     * giây là khoảng cách giữa hai việc ấy trên một đường truyền tệ.
     */
    private static final Duration TTL = Duration.ofSeconds(60);

    /**
     * Trần số vé đang chờ.
     *
     * <p>Mỗi vé là vài chục byte và tự hết hạn sau một phút, nên con số này rất
     * khó chạm bằng cách dùng bình thường — nó ở đây để một vòng lặp xin vé
     * không biến bản đồ này thành đường rò bộ nhớ. Chạm trần thì dọn trước; vẫn
     * đầy thì từ chối, và người dùng mất phần thông báo tức thời chứ không mất
     * gì khác.
     */
    private static final int MAX_OUTSTANDING = 2_000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private record Ticket(Long userId, Instant expiresAt) {
    }

    /**
     * Phát một vé cho người đang đăng nhập.
     *
     * @return chuỗi vé, hoặc null khi bản đồ đã đầy
     */
    public String issue(Long userId) {
        sweep();

        if (tickets.size() >= MAX_OUTSTANDING) {
            log.warn("Từ chối phát vé luồng thông báo: đang giữ {} vé", tickets.size());
            return null;
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = ENCODER.encodeToString(bytes);

        tickets.put(ticket, new Ticket(userId, Instant.now().plus(TTL)));
        return ticket;
    }

    /**
     * Đổi vé lấy danh tính, và tiêu nó.
     *
     * <p>{@code remove} chứ không {@code get}: một vé dùng được hai lần là một vé
     * mà bản sao lọt vào log vẫn còn giá trị. Hệ quả là {@code EventSource} tự
     * nối lại sẽ bị từ chối — và đó là hành vi mong muốn, vì mỗi lần nối lại
     * phía trình duyệt cũng là một lần nó phải đồng bộ lại hộp thư. Xem
     * {@code useNotifications}.
     *
     * @return id người dùng, hoặc null nếu vé sai, đã dùng, hoặc đã hết hạn
     */
    public Long redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }

        Ticket found = tickets.remove(ticket);
        if (found == null) {
            return null;
        }
        if (found.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return found.userId();
    }

    /** Hạn của một vé, để controller nói lại cho trình duyệt biết. */
    public long ttlSeconds() {
        return TTL.toSeconds();
    }

    /** Số vé đang chờ — dùng cho kiểm thử. */
    public int outstanding() {
        sweep();
        return tickets.size();
    }

    /**
     * Bỏ những vé đã quá hạn.
     *
     * <p>Chạy ngay trên luồng đang xin vé thay vì trên một tác vụ định kỳ: bản đồ
     * này nhỏ, và một vòng quét vài trăm phần tử rẻ hơn nhiều so với việc giữ
     * thêm một nhịp {@code @Scheduled} cho nó. Máy chủ không có ai dùng thì cũng
     * không có gì cần dọn.
     */
    private void sweep() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Ticket>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
    }
}
