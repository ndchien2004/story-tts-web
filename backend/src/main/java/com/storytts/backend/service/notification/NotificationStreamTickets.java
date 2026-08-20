package com.storytts.backend.service.notification;

import com.storytts.backend.security.OneTimeTicketStore;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
 * <h3>Phần việc thật nằm ở {@link OneTimeTicketStore}</h3>
 * Lớp này từng tự giữ bản đồ vé của mình. Nó không còn giữ nữa, vì hộp thư hỗ
 * trợ cần đúng cùng một cơ chế cho kết nối WebSocket của nó — và
 * {@code WebSocket} cũng không đặt được header, y hệt {@code EventSource}. Hai
 * bản sao của cùng một thứ là hai bản có thể sửa lệch nhau ở đúng bốn chỗ dễ
 * viết sai; lý lẽ đầy đủ nằm ở lớp kia.
 *
 * <p>Cái ở lại đây là <i>hai con số</i> — hạn vé và trần số vé — vì chúng thuộc
 * về luồng này chứ không thuộc về cơ chế. Mỗi luồng giữ bản đồ riêng, nên vé của
 * luồng thông báo không bao giờ mở được luồng hỗ trợ.
 */
@Component
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
     * không biến bộ nhớ thành đường rò. Chạm trần thì người dùng mất phần thông
     * báo tức thời chứ không mất gì khác.
     */
    private static final int MAX_OUTSTANDING = 2_000;

    private final OneTimeTicketStore store =
            new OneTimeTicketStore("thong-bao", TTL, MAX_OUTSTANDING);

    /**
     * Phát một vé cho người đang đăng nhập.
     *
     * @return chuỗi vé, hoặc null khi bản đồ đã đầy
     */
    public String issue(Long userId) {
        return store.issue(userId);
    }

    /**
     * Đổi vé lấy danh tính, và tiêu nó.
     *
     * @return id người dùng, hoặc null nếu vé sai, đã dùng, hoặc đã hết hạn
     */
    public Long redeem(String ticket) {
        return store.redeem(ticket);
    }

    /** Hạn của một vé, để controller nói lại cho trình duyệt biết. */
    public long ttlSeconds() {
        return store.ttlSeconds();
    }

    /** Số vé đang chờ — dùng cho kiểm thử. */
    public int outstanding() {
        return store.outstanding();
    }
}
