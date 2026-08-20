package com.storytts.backend.security;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vé một lần, sống ngắn, đổi lấy danh tính — dùng chung cho mọi đường thời gian
 * thực của trang.
 *
 * <h3>Vấn đề mà nó giải quyết</h3>
 * Hai giao thức đẩy tin của trình duyệt đều <b>không đặt được header
 * {@code Authorization}</b>: {@code EventSource} và {@code WebSocket}. Chúng chỉ
 * có URL. Nên danh tính phải đi trên URL bằng cách nào đó, và câu hỏi thật là
 * đi bằng <i>cái gì</i>.
 *
 * <pre>
 *   token phiên trên URL → sống 24 giờ, mở được mọi đường của tài khoản
 *   vé này trên URL      → sống 60 giây, dùng được một lần, mở đúng một luồng
 * </pre>
 *
 * Rò ra ngoài — vào access log của nhà cung cấp, vào lịch sử trình duyệt, vào
 * một ảnh chụp màn hình — thì cái thứ nhất là mất tài khoản, cái thứ hai là mất
 * một thứ đã hết hạn từ lâu và đã bị tiêu ngay khi kết nối mở.
 *
 * <p>Đó cũng là lý do {@code JwtAuthenticationFilter} cố ý <i>không</i> nới danh
 * sách {@code ?access_token=} của nó ra ngoài mấy đường phát media: với thẻ
 * {@code <audio>} thì token trên URL là cái giá phải trả, với những đường này
 * thì không — vì có lựa chọn rẻ hơn, và nó ở đây.
 *
 * <h3>Vì sao lớp này tồn tại thay vì hai bản sao</h3>
 * Hộp thư thông báo (SSE) và hộp thư hỗ trợ (WebSocket) cần đúng cùng một thứ,
 * và cùng một thứ ấy có bốn chỗ dễ viết sai: sinh chuỗi bằng nguồn ngẫu nhiên an
 * toàn, tiêu vé bằng {@code remove} chứ không {@code get}, kiểm hạn <i>sau</i>
 * khi đã tiêu, và có trần để một vòng lặp xin vé không thành đường rò bộ nhớ.
 * Chép bộ ấy sang một lớp thứ hai là chép cả bốn chỗ ấy.
 *
 * <p>Cái được chia sẻ là <i>mã</i>, không phải một thể hiện: mỗi bên dùng tự
 * dựng một bản với tên, hạn và trần của riêng nó, nên vé của luồng này không
 * bao giờ mở được luồng kia. Cùng hình dạng với {@code SseHub}.
 *
 * <h3>Vì sao để trong bộ nhớ</h3>
 * Vé sống sáu mươi giây và chỉ có nghĩa với đúng tiến trình đang giữ kết nối —
 * mà một kết nối SSE hay WebSocket thì vốn đã gắn với một tiến trình. Cho nó
 * vào cơ sở dữ liệu là thêm hai lượt ghi cho mỗi lần mở tab để lưu một thứ sẽ
 * bị xóa trước khi kịp nguội. Máy chủ khởi động lại thì mọi kết nối cũng đứt, và
 * trình duyệt xin vé mới rồi nối lại — đúng đường nó vẫn đi khi mạng chập chờn.
 *
 * <p>Hệ quả cần nói rõ cho ngày chạy nhiều bản ứng dụng: vé phát ở bản A không
 * đổi được ở bản B. Với một bộ cân bằng tải dán phiên (sticky session) thì
 * không có vấn đề gì; không dán thì đây là một trong hai chỗ phải đổi — chỗ kia
 * là sổ kết nối. Xem tài liệu triển khai.
 */
@Slf4j
public final class OneTimeTicketStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 32 byte ngẫu nhiên: quá rộng để dò, quá ngắn hạn để đáng dò. */
    private static final int TICKET_BYTES = 32;

    /** Tên luồng, chỉ dùng cho nhật ký. */
    private final String name;

    private final Duration ttl;
    private final int maxOutstanding;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private record Ticket(Long userId, Instant expiresAt) {
    }

    /**
     * @param ttl            đủ để mở một kết nối ngay sau khi xin, không đủ để
     *                       nằm lại ở đâu đó
     * @param maxOutstanding trần số vé đang chờ. Mỗi vé là vài chục byte và tự
     *                       hết hạn, nên con số này rất khó chạm bằng cách dùng
     *                       bình thường — nó ở đây để một vòng lặp xin vé không
     *                       biến bản đồ này thành đường rò bộ nhớ.
     */
    public OneTimeTicketStore(String name, Duration ttl, int maxOutstanding) {
        this.name = name;
        this.ttl = ttl;
        this.maxOutstanding = maxOutstanding;
    }

    /**
     * Phát một vé cho người đang đăng nhập.
     *
     * @return chuỗi vé, hoặc null khi bản đồ đã đầy. Bên gọi trả 503 và trang
     *         vẫn chạy — chỉ mất phần cập nhật tức thời.
     */
    public String issue(Long userId) {
        sweep();

        if (tickets.size() >= maxOutstanding) {
            log.warn("Từ chối phát vé luồng {}: đang giữ {} vé", name, tickets.size());
            return null;
        }

        byte[] bytes = new byte[TICKET_BYTES];
        RANDOM.nextBytes(bytes);
        String ticket = ENCODER.encodeToString(bytes);

        tickets.put(ticket, new Ticket(userId, Instant.now().plus(ttl)));
        return ticket;
    }

    /**
     * Đổi vé lấy danh tính, và tiêu nó.
     *
     * <p>{@code remove} chứ không {@code get}: một vé dùng được hai lần là một
     * vé mà bản sao lọt vào log vẫn còn giá trị. Hệ quả là cơ chế tự nối lại của
     * {@code EventSource} sẽ bị từ chối — và đó là hành vi mong muốn, vì mỗi lần
     * nối lại phía trình duyệt cũng phải là một lần đồng bộ lại dữ liệu, nên nó
     * phải đi qua mã của chúng ta chứ không qua mã của trình duyệt.
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
        return ttl.toSeconds();
    }

    /** Số vé đang chờ — dùng cho kiểm thử. */
    public int outstanding() {
        sweep();
        return tickets.size();
    }

    /**
     * Bỏ những vé đã quá hạn.
     *
     * <p>Chạy ngay trên luồng đang xin vé thay vì trên một tác vụ định kỳ: bản
     * đồ này nhỏ, và một vòng quét vài trăm phần tử rẻ hơn nhiều so với việc giữ
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
