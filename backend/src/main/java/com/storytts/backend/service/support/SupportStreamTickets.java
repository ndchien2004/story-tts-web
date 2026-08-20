package com.storytts.backend.service.support;

import com.storytts.backend.security.OneTimeTicketStore;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Vé một lần để mở kết nối WebSocket của hộp thư hỗ trợ.
 *
 * <h3>Cùng bài toán với luồng thông báo, cùng lời giải</h3>
 * API {@code WebSocket} của trình duyệt không đặt được header
 * {@code Authorization} — đúng hạn chế của {@code EventSource}, và vì đúng lý do
 * ấy: cả hai đều là hàm dựng nhận một URL, không phải một request cấu hình được.
 *
 * <p>Nên danh tính đi trên URL, và nó đi bằng một cái vé chứ không bằng token
 * phiên. Lý lẽ đầy đủ nằm ở {@link OneTimeTicketStore}; phần cần nhắc lại ở đây
 * là hệ quả: cầm được vé của người khác thì mở được đúng một kết nối, trong
 * vòng sáu mươi giây, và kết nối ấy vẫn phải qua tất cả các phép kiểm quyền cho
 * <i>từng lệnh</i> — xem {@code SupportService.resolveActor}.
 *
 * <h3>Vì sao một bản đồ riêng, không dùng chung với thông báo</h3>
 * Vì một cái vé nên mở được đúng một thứ. Dùng chung một bản đồ thì vé xin cho
 * luồng thông báo cũng mở được kết nối hỗ trợ — không phải một lỗ hổng leo
 * thang quyền (cùng một người, cùng một tài khoản), nhưng là một sự nhập nhằng
 * không đổi lại được gì, và là chỗ mà một tính năng thứ ba về sau sẽ vô tình
 * thừa hưởng.
 *
 * <h3>Vì sao hạn dài hơn một chút</h3>
 * Sáu mươi giây ở luồng thông báo là khoảng cách giữa "xin vé" và "mở
 * EventSource" trên một đường truyền tệ. Ở đây khoảng cách ấy dài hơn: trình
 * duyệt còn phải hoàn tất bắt tay HTTP Upgrade, thứ mà vài proxy doanh nghiệp
 * xử lý chậm hơn hẳn một request thường. Chín mươi giây cho phần chênh ấy, và
 * vẫn ngắn hơn hẳn mọi thứ đáng gọi là một phiên.
 */
@Component
public class SupportStreamTickets {

    private static final Duration TTL = Duration.ofSeconds(90);

    /**
     * Trần số vé đang chờ.
     *
     * <p>Thấp hơn luồng thông báo, và có lý do: cái chuông mở ở <i>mọi</i> trang
     * của người đã đăng nhập, còn kết nối này chỉ mở khi ai đó thật sự đang mở
     * hộp thư hỗ trợ. Một trần rộng bằng nhau ở đây là một trần không nói gì.
     */
    private static final int MAX_OUTSTANDING = 500;

    private final OneTimeTicketStore store =
            new OneTimeTicketStore("ho-tro", TTL, MAX_OUTSTANDING);

    /** @return chuỗi vé, hoặc null khi đã giữ quá nhiều vé chờ */
    public String issue(Long userId) {
        return store.issue(userId);
    }

    /** @return id người dùng, hoặc null nếu vé sai, đã dùng, hoặc đã hết hạn */
    public Long redeem(String ticket) {
        return store.redeem(ticket);
    }

    public long ttlSeconds() {
        return store.ttlSeconds();
    }

    /** Số vé đang chờ — dùng cho kiểm thử. */
    public int outstanding() {
        return store.outstanding();
    }
}
