package com.storytts.backend.service.support;

import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.exception.AccountLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ai mở được kết nối WebSocket, và ai không.
 *
 * <h3>Vì sao chỗ này cần một bài kiểm riêng</h3>
 * Cùng lý do với {@code NotificationStreamAccessTest}, và mạnh hơn một bậc.
 * {@code /ws/support} là điểm thứ hai — và điểm cuối cùng — mà
 * {@code SecurityConfig} cố ý <b>không</b> chặn trước, vì API
 * {@code WebSocket} của trình duyệt không đặt được header. Toàn bộ phần kiểm
 * quyền của lần bắt tay nằm trong {@link SupportHandshakeInterceptor}.
 *
 * <p>Nói thẳng hệ quả: nếu hai phép kiểm trong lớp ấy bị xóa đi, không một bài
 * kiểm nào khác trong dự án đỏ lên — luồng vẫn ghi đúng, phân quyền theo từng
 * lệnh vẫn chạy, mọi thứ khác vẫn xanh — trong khi một người lạ mở được một
 * đường nhận tin nhắn riêng của người khác. Bài này đỏ.
 *
 * <h3>"Đã kết nối" không có nghĩa là "đã xác thực"</h3>
 * Đó là câu mà mọi khẳng định dưới đây kiểm chứng: trả về {@code false} nghĩa
 * là <i>không có kết nối nào được mở</i>, không phải một kết nối nửa vời chờ
 * xác thực sau. Một trạng thái nửa vời là một trạng thái mà một dòng mã về sau
 * sẽ quên kiểm.
 */
class SupportHandshakeAccessTest {

    private static final long READER = 7L;
    private static final long ADMIN = 9L;

    private SupportStreamTickets tickets;
    private SupportService supportService;
    private SupportHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tickets = new SupportStreamTickets();
        supportService = mock(SupportService.class);
        interceptor = new SupportHandshakeInterceptor(tickets, supportService);

        when(supportService.resolveActor(READER))
                .thenReturn(new SupportService.Actor(READER, SupportSenderRole.USER));
        when(supportService.resolveActor(ADMIN))
                .thenReturn(new SupportService.Actor(ADMIN, SupportSenderRole.ADMIN));
    }

    /* ================================================================== */

    @Test
    @DisplayName("vé hợp lệ mở được kết nối, và danh tính đi cùng nó")
    void aValidTicketOpensTheConnection() {
        Map<String, Object> attributes = new HashMap<>();

        assertThat(shakeHands(tickets.issue(READER), attributes)).isTrue();

        assertThat(attributes)
                .containsEntry(SupportHandshakeInterceptor.ATTR_USER_ID, READER)
                .containsEntry(SupportHandshakeInterceptor.ATTR_ROLE, SupportSenderRole.USER);
    }

    @Test
    @DisplayName("vai trò đến từ máy chủ, không từ chuỗi truy vấn")
    void theRoleComesFromTheServer() {
        Map<String, Object> attributes = new HashMap<>();

        // Người đọc cố tự nhận là quản trị viên trên URL. Tham số ấy không có
        // chỗ nào để được đọc tới, và vai trò vẫn là vai trò của tài khoản.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/support");
        request.setQueryString("ticket=" + tickets.issue(READER) + "&role=ADMIN");

        assertThat(shakeHands(request, attributes)).isTrue();
        assertThat(attributes)
                .containsEntry(SupportHandshakeInterceptor.ATTR_ROLE, SupportSenderRole.USER);
    }

    @Test
    @DisplayName("không vé, vé bịa, vé rỗng — không kết nối nào được mở, và trả 401")
    void aMissingOrForgedTicketIsRefused() {
        for (String ticket : new String[]{null, "", "   ", "khong-phai-ve"}) {
            Map<String, Object> attributes = new HashMap<>();
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(shakeHands(ticket, attributes, response)).isFalse();

            assertThat(attributes).isEmpty();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Test
    @DisplayName("vé dùng được đúng một lần: bản sao lọt ra ngoài không mở được gì")
    void aTicketIsSpentOnFirstUse() {
        String ticket = tickets.issue(READER);

        assertThat(shakeHands(ticket, new HashMap<>())).isTrue();
        assertThat(shakeHands(ticket, new HashMap<>())).isFalse();
    }

    @Test
    @DisplayName("vé của người này không mở được kết nối mang danh người kia")
    void oneTicketOpensOneIdentity() {
        Map<String, Object> mine = new HashMap<>();
        Map<String, Object> theirs = new HashMap<>();

        assertThat(shakeHands(tickets.issue(READER), mine)).isTrue();
        assertThat(shakeHands(tickets.issue(ADMIN), theirs)).isTrue();

        assertThat(mine).containsEntry(SupportHandshakeInterceptor.ATTR_USER_ID, READER);
        assertThat(theirs).containsEntry(SupportHandshakeInterceptor.ATTR_USER_ID, ADMIN);
    }

    @Test
    @DisplayName("tài khoản bị khóa giữa lúc phát vé và lúc bắt tay vẫn không vào được")
    void anAccountLockedAfterTheTicketWasIssuedIsRefused() {
        // Cảnh có thật: vé sống chín mươi giây, và quản trị viên hoàn toàn có
        // thể bấm khóa trong quãng ấy. Vé hợp lệ nhưng tài khoản thì không.
        String ticket = tickets.issue(READER);
        when(supportService.resolveActor(anyLong())).thenThrow(new AccountLockedException());

        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(shakeHands(ticket, attributes, response)).isFalse();
        assertThat(attributes).isEmpty();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("lời từ chối không nói ra vì sao — cùng câu trả lời cho vé sai và tài khoản bị khóa")
    void theRefusalRevealsNothing() throws Exception {
        MockHttpServletResponse forged = new MockHttpServletResponse();
        shakeHands("khong-phai-ve", new HashMap<>(), forged);

        String ticket = tickets.issue(READER);
        when(supportService.resolveActor(anyLong())).thenThrow(new AccountLockedException());
        MockHttpServletResponse locked = new MockHttpServletResponse();
        shakeHands(ticket, new HashMap<>(), locked);

        // Hai lý do khác nhau, một câu trả lời. Người gọi không phân biệt được
        // "vé sai" với "tài khoản này tồn tại nhưng đã bị khóa".
        assertThat(locked.getStatus()).isEqualTo(forged.getStatus());
        assertThat(locked.getContentAsString()).isEqualTo(forged.getContentAsString());
    }

    /* ================================================================== */

    private boolean shakeHands(String ticket, Map<String, Object> attributes) {
        return shakeHands(ticket, attributes, new MockHttpServletResponse());
    }

    private boolean shakeHands(String ticket, Map<String, Object> attributes,
                               MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/support");
        if (ticket != null) {
            request.setQueryString("ticket=" + ticket);
        }
        return shakeHands(request, attributes, response);
    }

    private boolean shakeHands(MockHttpServletRequest request, Map<String, Object> attributes) {
        return shakeHands(request, attributes, new MockHttpServletResponse());
    }

    private boolean shakeHands(MockHttpServletRequest request, Map<String, Object> attributes,
                               MockHttpServletResponse rawResponse) {
        ServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
        ServerHttpResponse serverResponse = new ServletServerHttpResponse(rawResponse);
        return interceptor.beforeHandshake(serverRequest, serverResponse,
                mock(WebSocketHandler.class), attributes);
    }
}
