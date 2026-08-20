package com.storytts.backend.service.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cái vé mở luồng thông báo.
 *
 * <h3>Vì sao nó đáng có một bài kiểm riêng</h3>
 * Đây là chỗ duy nhất trong cả tính năng mà một chuỗi trên URL quyết định người
 * gọi là ai. Mọi đường khác đọc danh tính từ token đã qua chuỗi lọc; đường này
 * thì không đọc được, vì {@code EventSource} không gửi header. Nên ba tính chất
 * dưới đây là toàn bộ thứ đứng giữa hộp thư của một người và một người lạ:
 * không đoán được, dùng đúng một lần, và hết hạn nhanh.
 */
class NotificationStreamTicketsTest {

    private final NotificationStreamTickets tickets = new NotificationStreamTickets();

    @Test
    @DisplayName("vé đổi được đúng người đã xin nó")
    void aTicketResolvesToItsOwner() {
        String ticket = tickets.issue(7L);
        assertThat(tickets.redeem(ticket)).isEqualTo(7L);
    }

    @Test
    @DisplayName("vé dùng được đúng một lần")
    void aTicketIsSpentOnUse() {
        String ticket = tickets.issue(7L);

        assertThat(tickets.redeem(ticket)).isEqualTo(7L);
        // Một bản sao lọt vào access log hay lịch sử trình duyệt vì thế không
        // mở được gì. Hệ quả kèm theo: cơ chế tự nối lại của EventSource bị từ
        // chối, và đó là hành vi mong muốn — xem NotificationStreamTickets.
        assertThat(tickets.redeem(ticket)).isNull();
    }

    @Test
    @DisplayName("vé bịa ra, vé rỗng và null đều không mở được gì")
    void nonsenseOpensNothing() {
        assertThat(tickets.redeem("khong-phai-ve")).isNull();
        assertThat(tickets.redeem("")).isNull();
        assertThat(tickets.redeem(null)).isNull();
    }

    @Test
    @DisplayName("vé của người này không mở được luồng của người kia")
    void ticketsDoNotCrossAccounts() {
        String mine = tickets.issue(7L);
        String theirs = tickets.issue(8L);

        assertThat(tickets.redeem(mine)).isEqualTo(7L);
        assertThat(tickets.redeem(theirs)).isEqualTo(8L);
    }

    @Test
    @DisplayName("mỗi lần xin là một chuỗi khác, đủ dài để không đoán được")
    void everyTicketIsFresh() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String ticket = tickets.issue(7L);
            // 32 byte ngẫu nhiên mã hóa base64url: không có chỗ cho việc dò.
            assertThat(ticket).hasSizeGreaterThanOrEqualTo(40);
            assertThat(seen.add(ticket)).isTrue();
        }
    }

    @Test
    @DisplayName("vé đã tiêu không nằm lại trong bộ nhớ")
    void spentTicketsAreNotKept() {
        String ticket = tickets.issue(7L);
        assertThat(tickets.outstanding()).isEqualTo(1);

        tickets.redeem(ticket);
        assertThat(tickets.outstanding()).isZero();
    }

    @Test
    @DisplayName("vé sống đủ ngắn để một lần rò rỉ không còn nghĩa lý gì")
    void theWindowIsShort() {
        // Không chờ thật ở đây — một bài kiểm ngồi đợi 60 giây là một bài kiểm
        // sẽ bị tắt đi. Điều đáng ghim là chính con số: nó phải ở thang giây,
        // không phải thang giờ như một phiên đăng nhập.
        assertThat(tickets.ttlSeconds()).isLessThanOrEqualTo(120);
    }
}
