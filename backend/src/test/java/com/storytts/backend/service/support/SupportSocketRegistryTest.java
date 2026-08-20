package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.SupportSenderRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sổ kết nối: định tuyến, trần, dọn dẹp, và lệnh đá ra.
 *
 * <h3>Vì sao mock được ở đây trong khi tầng dữ liệu thì không</h3>
 * Vì thứ được kiểm ở đây <i>là</i> mã Java, không phải hành vi của cơ sở dữ
 * liệu. {@code WebSocketSession} là một giao diện của Spring và mọi điều lớp
 * này làm với nó gói gọn trong bốn việc: hỏi còn mở không, gửi, ping, đóng. Một
 * kết nối thật sẽ thêm một máy chủ nhúng và một trình duyệt vào bài kiểm mà
 * không thêm một khẳng định nào.
 *
 * <p>Ngược lại với {@code SupportJpaTest}, nơi mock sẽ chỉ khẳng định rằng mock
 * đã được lập trình đúng.
 *
 * <h3>Bốn thứ được chứng minh</h3>
 * <ol>
 *   <li>khung tin đi đúng nhóm, và <b>không</b> đi tới nhóm còn lại;</li>
 *   <li>hai cái trần chặn được cả một người mở quá nhiều lẫn cả máy chủ quá tải;</li>
 *   <li>kết nối chết được dọn khỏi sổ, và bộ đếm không bị trừ hai lần;</li>
 *   <li>lệnh đá ra đóng mọi kết nối của một tài khoản.</li>
 * </ol>
 */
class SupportSocketRegistryTest {

    private static final Long READER = 7L;
    private static final Long ADMIN = 9L;

    private final AtomicInteger sessionIds = new AtomicInteger();

    private SupportSocketRegistry registry;

    @BeforeEach
    void setUp() {
        // Trần rộng ở đây: phần lớn bài kiểm nói về định tuyến và dọn dẹp, và
        // một cái trần chật sẽ khiến chúng hỏng vì một lý do không liên quan gì
        // tới thứ đang kiểm. Ba bài kiểm về trần tự dựng sổ của riêng chúng.
        registry = registryWith(4, 10, Duration.ofMinutes(30), Duration.ofMinutes(2));
    }

    /* ================================================================== */
    /* Định tuyến                                                          */
    /* ================================================================== */

    @Test
    @DisplayName("khung tin của một người chỉ tới cửa sổ của người ấy")
    void aFrameReachesOnlyItsOwner() throws Exception {
        WebSocketSession mine = open(READER, SupportSenderRole.USER);
        WebSocketSession theirs = open(8L, SupportSenderRole.USER);

        assertThat(registry.sendToUser(READER, "{\"type\":\"message:new\"}")).isEqualTo(1);

        verify(mine).sendMessage(any());
        verify(theirs, never()).sendMessage(any());
    }

    @Test
    @DisplayName("ba tab của một người đều nhận được — danh tính người, không phải danh tính kết nối")
    void everyWindowOfTheSamePersonIsReached() throws Exception {
        WebSocketSession one = open(READER, SupportSenderRole.USER);
        WebSocketSession two = open(READER, SupportSenderRole.USER);
        WebSocketSession three = open(READER, SupportSenderRole.USER);

        assertThat(registry.sendToUser(READER, "khung")).isEqualTo(3);

        for (WebSocketSession session : new WebSocketSession[]{one, two, three}) {
            verify(session).sendMessage(any());
        }
    }

    @Test
    @DisplayName("kết nối vai quản trị viên không nhận bản dành cho người đọc, và ngược lại")
    void theTwoAudiencesNeverOverlap() throws Exception {
        // Cùng một tài khoản từng là thành viên rồi được nâng lên quản trị
        // viên: nó vẫn còn luồng cũ của mình. Không lọc theo vai trò thì kết
        // nối này nhận cả hai bản của cùng một tin, và giao diện vẽ nó hai lần.
        WebSocketSession asAdmin = open(ADMIN, SupportSenderRole.ADMIN);

        assertThat(registry.sendToUser(ADMIN, "bản của người đọc")).isZero();
        verify(asAdmin, never()).sendMessage(any());

        assertThat(registry.sendToAdmins("bản của quản trị")).isEqualTo(1);
        verify(asAdmin).sendMessage(any());
    }

    @Test
    @DisplayName("nội dung khung tin đi ra đúng như được đưa vào — không dựng lại cho mỗi kết nối")
    void thePayloadIsPassedThroughVerbatim() throws Exception {
        WebSocketSession session = open(READER, SupportSenderRole.USER);

        registry.sendToUser(READER, "{\"type\":\"message:new\",\"payload\":{}}");

        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(sent.capture());
        assertThat(((TextMessage) sent.getValue()).getPayload())
                .isEqualTo("{\"type\":\"message:new\",\"payload\":{}}");
    }

    /* ================================================================== */
    /* Trần                                                                */
    /* ================================================================== */

    @Test
    @DisplayName("trần theo tài khoản: kết nối thứ ba bị từ chối, hai cái đang mở vẫn sống")
    void thePerUserCeilingRefusesTheNewcomer() {
        registry = registryWith(2, 10, Duration.ofMinutes(30), Duration.ofMinutes(2));
        assertThat(registry.register(session(), READER, SupportSenderRole.USER)).isNotNull();
        assertThat(registry.register(session(), READER, SupportSenderRole.USER)).isNotNull();

        // Từ chối kết nối MỚI chứ không đá kết nối cũ ra: người đang trao đổi
        // dở không đáng bị mất đường vì có người mở thêm tab.
        assertThat(registry.register(session(), READER, SupportSenderRole.USER)).isNull();
        assertThat(registry.connectionsOf(READER)).isEqualTo(2);
    }

    @Test
    @DisplayName("trần chung của máy chủ chặn cả những tài khoản chưa chạm trần của mình")
    void theGlobalCeilingApplies() {
        registry = registryWith(4, 3, Duration.ofMinutes(30), Duration.ofMinutes(2));
        registry.register(session(), 1L, SupportSenderRole.USER);
        registry.register(session(), 2L, SupportSenderRole.USER);
        registry.register(session(), 3L, SupportSenderRole.USER);

        assertThat(registry.openConnections()).isEqualTo(3);
        assertThat(registry.register(session(), 4L, SupportSenderRole.USER)).isNull();
    }

    @Test
    @DisplayName("một lần từ chối vì chạm trần không để lại khóa rỗng trong sổ")
    void aRefusedRegistrationLeavesNoTrace() {
        registry = registryWith(4, 3, Duration.ofMinutes(30), Duration.ofMinutes(2));
        registry.register(session(), 1L, SupportSenderRole.USER);
        registry.register(session(), 2L, SupportSenderRole.USER);
        registry.register(session(), 3L, SupportSenderRole.USER);

        registry.register(session(), 99L, SupportSenderRole.USER);

        assertThat(registry.connectionsOf(99L)).isZero();
        assertThat(registry.openConnections()).isEqualTo(3);
    }

    /* ================================================================== */
    /* Dọn dẹp                                                             */
    /* ================================================================== */

    @Test
    @DisplayName("kết nối chết bị dọn ngay lúc gửi hỏng, và chỗ của nó được trả lại")
    void aDeadConnectionIsSweptOnFailedSend() throws Exception {
        WebSocketSession dead = session();
        doThrow(new IOException("đầu bên kia đã đi")).when(dead).sendMessage(any());
        registry.register(dead, READER, SupportSenderRole.USER);

        assertThat(registry.sendToUser(READER, "khung")).isZero();
        assertThat(registry.openConnections()).isZero();
        assertThat(registry.connectionsOf(READER)).isZero();
    }

    @Test
    @DisplayName("bỏ sổ hai lần không trừ bộ đếm hai lần")
    void unregisteringTwiceIsHarmless() {
        SupportSession session = registry.register(session(), READER, SupportSenderRole.USER);

        registry.unregister(session);
        registry.unregister(session);
        registry.unregister(null);

        assertThat(registry.openConnections()).isZero();
        // Chỗ đã được trả lại thật sự, không phải chỉ trên con số.
        assertThat(registry.register(session(), READER, SupportSenderRole.USER)).isNotNull();
    }

    @Test
    @DisplayName("nhịp tim đóng kết nối đã sống hết hạn, kèm mã mời nối lại")
    void theHeartbeatRetiresAnExpiredConnection() throws Exception {
        registry = registryWith(4, 10, Duration.ofMillis(1), Duration.ofMinutes(2));
        WebSocketSession raw = session();
        registry.register(raw, READER, SupportSenderRole.USER);

        // Hạn sống đo bằng đồng hồ tường (Instant), thứ chỉ nhích theo từng
        // bước rời rạc — trên Windows là khoảng một phần trăm giây. Một hạn
        // "một nano giây" vì thế có thể chưa trôi qua vào lúc nhịp tim chạy, và
        // bài kiểm sẽ hỏng lúc được lúc không vì đồng hồ chứ không vì mã.
        Thread.sleep(20);

        registry.heartbeat();

        assertThat(registry.openConnections()).isZero();
        verifyClosedWith(raw, SupportCloseCodes.RECONNECT);
    }

    @Test
    @DisplayName("nhịp tim đóng kết nối im lặng quá lâu")
    void theHeartbeatRetiresASilentConnection() throws Exception {
        // Hạn im lặng đo bằng System.nanoTime(), thứ mịn hơn hẳn đồng hồ tường,
        // nên một nano giây ở đây là đủ và không cần chờ.
        registry = registryWith(4, 10, Duration.ofMinutes(30), Duration.ofNanos(1));
        WebSocketSession raw = session();
        registry.register(raw, READER, SupportSenderRole.USER);

        registry.heartbeat();

        assertThat(registry.openConnections()).isZero();
        verifyClosedWith(raw, SupportCloseCodes.RECONNECT);
    }

    @Test
    @DisplayName("nhịp tim ping kết nối còn sống thay vì đóng nó")
    void theHeartbeatPingsTheLiving() throws Exception {
        WebSocketSession raw = session();
        registry.register(raw, READER, SupportSenderRole.USER);

        registry.heartbeat();

        assertThat(registry.openConnections()).isEqualTo(1);
        verify(raw).sendMessage(any());
        verify(raw, never()).close(any());
    }

    /* ================================================================== */
    /* Thu hồi quyền                                                       */
    /* ================================================================== */

    @Test
    @DisplayName("khóa tài khoản đóng mọi kết nối của người ấy và không đụng tới ai khác")
    void revokeClosesEveryConnectionOfOneAccount() throws Exception {
        WebSocketSession one = open(READER, SupportSenderRole.USER);
        WebSocketSession two = open(READER, SupportSenderRole.USER);
        WebSocketSession bystander = open(8L, SupportSenderRole.USER);

        assertThat(registry.revoke(READER, SupportCloseCodes.ACCESS_REVOKED)).isEqualTo(2);

        verifyClosedWith(one, SupportCloseCodes.ACCESS_REVOKED);
        verifyClosedWith(two, SupportCloseCodes.ACCESS_REVOKED);
        verify(bystander, never()).close(any());
        assertThat(registry.connectionsOf(READER)).isZero();
        assertThat(registry.openConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("đá một tài khoản không có kết nối nào là lệnh rỗng")
    void revokingNothingIsHarmless() {
        assertThat(registry.revoke(1234L, SupportCloseCodes.ACCESS_REVOKED)).isZero();
    }

    /* ================================================================== */
    /* Tiện ích                                                            */
    /* ================================================================== */

    private WebSocketSession open(Long userId, SupportSenderRole role) {
        WebSocketSession raw = session();
        registry.register(raw, userId, role);
        return raw;
    }

    private WebSocketSession session() {
        WebSocketSession raw = mock(WebSocketSession.class);
        when(raw.getId()).thenReturn("phien-" + sessionIds.incrementAndGet());
        when(raw.isOpen()).thenReturn(true);
        return raw;
    }

    private static void verifyClosedWith(WebSocketSession raw, CloseStatus expected)
            throws IOException {
        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(raw, times(1)).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(expected.getCode());
    }

    /**
     * Một sổ kết nối với đúng bốn con số mà bài kiểm quan tâm.
     *
     * <p>Trần đặt thấp để chạm tới chúng mà không phải mở bốn trăm kết nối; hạn
     * sống và hạn im lặng đặt ngắn để nhịp tim có việc để làm ngay.
     */
    private static SupportSocketRegistry registryWith(int perUser, int global,
                                                      Duration maxLifetime,
                                                      Duration idleTimeout) {
        return new SupportSocketRegistry(new SupportProperties(2000, 50, 200, 50, 20,
                perUser, global, Duration.ofSeconds(25), idleTimeout, maxLifetime));
    }
}
