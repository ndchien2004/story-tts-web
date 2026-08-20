package com.storytts.backend.service.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.exception.AccountLockedException;
import com.storytts.backend.exception.SupportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

/**
 * Bộ xử lý khung tin của hộp thư hỗ trợ.
 *
 * <h3>Lớp này cố ý mỏng</h3>
 * Nó dịch một khung tin JSON thành một lời gọi vào {@link SupportService}, rồi
 * dịch kết quả ngược lại. Không có phép kiểm quyền nào <i>của riêng nó</i>, và
 * đó là điểm chính: đặc tả nói rõ WebSocket không được là lối vòng qua phép
 * kiểm của REST, và cách chắc chắn nhất để giữ điều đó là hai cửa vào gọi vào
 * đúng một chỗ.
 *
 * <p>So sánh cho rõ: {@code SupportController} cũng dịch một request HTTP thành
 * đúng những lời gọi ấy. Hai lớp mỏng, một lớp quyết định.
 *
 * <h3>Ba việc nó thật sự làm</h3>
 * <ol>
 *   <li><b>Đọc lại quyền cho mỗi khung tin.</b> {@code resolveActor} chạm cơ sở
 *       dữ liệu mỗi lần, và vai trò lấy được từ đó — không phải vai trò ghi
 *       trong sổ lúc bắt tay — mới là thứ quyết định lệnh nào chạy được.</li>
 *   <li><b>Phát hiện quyền đổi giữa chừng.</b> Vai trò mới khác vai trò lúc bắt
 *       tay nghĩa là kết nối này đang nằm sai nhóm trong sổ định tuyến, nên nó
 *       bị đóng và trình duyệt nối lại với vai trò đúng.</li>
 *   <li><b>Không để lộ gì khi hỏng.</b> Mọi ngoại lệ ngoài dự kiến thành một mã
 *       chung; không có tên lớp, câu SQL hay ngăn xếp nào đi ra ngoài.</li>
 * </ol>
 *
 * <h3>Thứ tự khung tin khi gửi thành công</h3>
 * <pre>
 *   message:new  (tới mọi cửa sổ liên quan, phát ở AFTER_COMMIT)
 *   message:ack  (chỉ tới cửa sổ đã gửi)
 * </pre>
 *
 * Lời báo nhận đi <i>sau</i>, và đó là hệ quả tất yếu của việc đẩy tin xảy ra ở
 * {@code AFTER_COMMIT} — tức là bên trong lời gọi gửi, trước khi nó trả về.
 * Không sao: cửa sổ đã gửi nhận ra tin của chính mình qua {@code clientMessageId}
 * và gộp nó vào tin nhắn lạc quan đang hiện, nên hai khung tin đến theo thứ tự
 * nào cũng ra cùng một màn hình. Việc ấy được viết ra ở đây vì nó là thứ dễ
 * tưởng là lỗi khi đọc nhật ký.
 *
 * <h3>Về luồng và về pool kết nối</h3>
 * Khung tin được xử lý trên luồng của vùng chứa servlet, và nó có chạm cơ sở dữ
 * liệu — đúng như một request HTTP chạm. Điều <b>không</b> xảy ra là một kết nối
 * cơ sở dữ liệu bị giữ suốt vòng đời của WebSocket: giao dịch mở ra và đóng lại
 * bên trong một lời gọi, rồi kết nối về pool. Hai hàng rào tần suất — theo kết
 * nối trước khi phân tích, theo tài khoản trước khi ghi — là thứ giữ cho một
 * đợt khung tin dồn dập không biến thành một đợt giao dịch dồn dập.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportWebSocketHandler extends TextWebSocketHandler {

    private static final String TYPE_SEND = "message:send";
    private static final String TYPE_READ = "message:read";
    private static final String TYPE_PING = "ping";

    /** Câu trả lời chung cho một lỗi ngoài dự kiến. Không nói gì về bên trong. */
    private static final String GENERIC_ERROR = "SUPPORT_ERROR";

    /** Sàn của trần khung tin: đủ rộng để một cấu hình rất chặt vẫn gửi được. */
    private static final int MIN_TEXT_LIMIT_BYTES = 16 * 1024;

    /** Giao thức này không nhận khung nhị phân ở đâu cả. */
    private static final int BINARY_LIMIT_BYTES = 1024;

    private final SupportService supportService;
    private final SupportSocketRegistry registry;
    private final SupportRealtime realtime;
    private final SupportProperties properties;
    private final ObjectMapper objectMapper;

    /* ------------------------------------------------------------------ */
    /* Vòng đời kết nối                                                    */
    /* ------------------------------------------------------------------ */

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) {
        Long userId = (Long) raw.getAttributes().get(SupportHandshakeInterceptor.ATTR_USER_ID);
        SupportSenderRole role =
                (SupportSenderRole) raw.getAttributes().get(SupportHandshakeInterceptor.ATTR_ROLE);

        if (userId == null || role == null) {
            // Không thể xảy ra khi interceptor đã chạy — nó trả false thay vì
            // để lọt. Vẫn kiểm, để việc gắn handler này vào một đường không có
            // interceptor là một kết nối bị đóng chứ không phải một kết nối vô
            // danh được phục vụ.
            closeQuietly(raw, SupportCloseCodes.UNAUTHORIZED);
            return;
        }

        // Trần kích thước khung tin, đặt TRƯỚC khi vào sổ và trước khi khung
        // tin đầu tiên có thể tới.
        //
        // Đây là hàng rào duy nhất chặn được một khung tin 50MB: mọi phép kiểm
        // trong Java đều đã quá muộn khi ấy — máy chủ đã phải nhận đủ số byte
        // để có gì mà kiểm. Vượt trần thì vùng chứa hủy phiên, và một hộp có
        // 224MB heap không bị mười kết nối biến thành hết bộ nhớ.
        //
        // Tính từ trần tin nhắn thay vì đặt cứng, với bốn byte cho một ký tự:
        // tiếng Việt có dấu chiếm ba byte trong UTF-8 và khung tin còn chở phần
        // JSON bao quanh. Đặt cứng thì ngày ai đó nới app.support.max-message-length,
        // tin dài sẽ bị cắt ở tầng dưới — cấu hình nói một đằng, hành vi một nẻo.
        raw.setTextMessageSizeLimit(
                Math.max(MIN_TEXT_LIMIT_BYTES, properties.effectiveMaxMessageLength() * 4 + 2048));
        // Giao thức này không nhận nhị phân ở đâu cả; đủ nhỏ để vùng chứa không
        // cấp phát một bộ đệm lớn cho thứ sẽ bị bỏ ngay.
        raw.setBinaryMessageSizeLimit(BINARY_LIMIT_BYTES);

        SupportSession session = registry.register(raw, userId, role);
        if (session == null) {
            closeQuietly(raw, SupportCloseCodes.TOO_MANY_CONNECTIONS);
            return;
        }

        // Giữ ngay trong túi thuộc tính của phiên thay vì trong một bản đồ thứ
        // hai: một bản đồ nữa là một chỗ nữa phải nhớ dọn, và quên dọn nó là
        // một đường rò bộ nhớ không ai thấy cho tới khi hết heap.
        raw.getAttributes().put(ATTR_SESSION, session);

        registry.sendTo(session, realtime.frame(SupportRealtime.EVENT_CONNECTION_READY,
                new ReadyFrame(role,
                        properties.effectiveMaxMessageLength(),
                        properties.historyPageSize(),
                        Instant.now())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        registry.unregister(sessionOf(raw));
    }

    @Override
    public void handleTransportError(WebSocketSession raw, Throwable exception) {
        log.debug("Lỗi đường truyền trên kết nối hỗ trợ {}: {}",
                raw.getId(), exception.getMessage());
        SupportSession session = sessionOf(raw);
        if (session != null) {
            session.close(CloseStatus.SERVER_ERROR);
            registry.unregister(session);
        } else {
            closeQuietly(raw, CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Trình duyệt trả lời nhịp tim.
     *
     * <p>Đây là nửa còn lại của cơ chế phát hiện kết nối chết: máy chủ ping, và
     * chỉ một kết nối còn sống mới pong. Không có nó thì mốc hoạt động chỉ tiến
     * lên khi người dùng <i>gõ</i> gì đó, và một tab mở im lặng cả buổi sẽ bị
     * đóng như một kết nối ma.
     */
    @Override
    protected void handlePongMessage(WebSocketSession raw, PongMessage message) {
        SupportSession session = sessionOf(raw);
        if (session != null) {
            session.touch();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Khung tin                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
        SupportSession session = sessionOf(raw);
        if (session == null) {
            closeQuietly(raw, SupportCloseCodes.UNAUTHORIZED);
            return;
        }
        session.touch();

        // Hàng rào theo kết nối chạy TRƯỚC khi phân tích, và đó là điểm chính:
        // nó bảo vệ chính công việc phân tích. Xem SupportSession.allowFrame.
        if (!session.allowFrame()) {
            sendError(session, SupportException.Reason.SUPPORT_RATE_LIMITED.name(),
                    SupportException.Reason.SUPPORT_RATE_LIMITED.getMessage(), null);
            return;
        }

        Inbound inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), Inbound.class);
        } catch (Exception ex) {
            // Một máy khách viết đúng không bao giờ gửi thứ không phân tích
            // được, nên không có lý do giữ kết nối lại để nhận thêm.
            log.info("Đóng kết nối hỗ trợ {}: khung tin không đọc được", session.id());
            session.close(SupportCloseCodes.PROTOCOL_ABUSE);
            registry.unregister(session);
            return;
        }

        if (inbound == null || inbound.type() == null) {
            session.close(SupportCloseCodes.PROTOCOL_ABUSE);
            registry.unregister(session);
            return;
        }

        try {
            switch (inbound.type()) {
                case TYPE_SEND -> handleSend(session, inbound);
                case TYPE_READ -> handleRead(session, inbound);
                case TYPE_PING -> { /* Đã touch() ở trên; không cần trả lời gì thêm. */ }
                default -> sendError(session, "UNKNOWN_FRAME",
                        "Máy chủ không hiểu yêu cầu này.", inbound.clientMessageId());
            }
        } catch (SupportException ex) {
            sendError(session, ex.code(), ex.getMessage(), inbound.clientMessageId());
        } catch (AccountLockedException ex) {
            // Tài khoản vừa bị khóa. Nói một câu rồi cắt: giữ kết nối lại chỉ để
            // từ chối từng khung tin một là giữ một đường nhận tin cho một tài
            // khoản không được phép nhận nữa.
            sendError(session, AccountLockedException.CODE, ex.getMessage(),
                    inbound.clientMessageId());
            session.close(SupportCloseCodes.ACCESS_REVOKED);
            registry.unregister(session);
        } catch (RuntimeException ex) {
            // Ngăn xếp vào nhật ký, một mã chung ra ngoài. Tên lớp, câu SQL và
            // hình dạng lược đồ không bao giờ được đi qua đường này.
            log.error("Lỗi khi xử lý khung tin hỗ trợ loại {} của người {}",
                    inbound.type(), session.userId(), ex);
            sendError(session, GENERIC_ERROR,
                    "Đã có lỗi xảy ra. Vui lòng thử lại.", inbound.clientMessageId());
        }
    }

    /**
     * Gửi một tin nhắn.
     *
     * <p>Người đọc không gửi kèm {@code conversationId} và giá trị ấy cũng không
     * được đọc tới trên nhánh của họ: luồng suy ra từ quyền sở hữu. Quản trị
     * viên thì phải gửi, vì họ trả lời được mọi luồng — và tham số ấy đi qua
     * đúng phép kiểm quyền mà đường REST dùng.
     */
    private void handleSend(SupportSession session, Inbound inbound) {
        SupportService.Actor actor = actorOf(session);
        if (actor == null) {
            return;
        }

        SupportSendRequest request =
                new SupportSendRequest(inbound.clientMessageId(), inbound.content());

        SupportStore.Appended appended = actor.role() == SupportSenderRole.ADMIN
                ? supportService.sendAsAdmin(actor, inbound.conversationId(), request)
                : supportService.sendAsUser(actor, request);

        var view = actor.role() == SupportSenderRole.ADMIN
                ? appended.adminView()
                : appended.userView();

        registry.sendTo(session, realtime.frame(SupportRealtime.EVENT_MESSAGE_ACK,
                new AckFrame(
                        view.clientMessageId(),
                        view.id(),
                        view.conversationId(),
                        appended.duplicate() ? AckStatus.DUPLICATE : AckStatus.ACCEPTED,
                        view.createdAt())));
    }

    /** Đẩy mốc đã đọc của phía này lên. Khung tin báo nhận đi ra từ {@code SupportRealtime}. */
    private void handleRead(SupportSession session, Inbound inbound) {
        SupportService.Actor actor = actorOf(session);
        if (actor == null) {
            return;
        }
        if (inbound.lastMessageId() == null || inbound.lastMessageId() <= 0) {
            throw new SupportException(SupportException.Reason.INVALID_READ_TARGET);
        }

        if (actor.role() == SupportSenderRole.ADMIN) {
            supportService.markReadAsAdmin(actor, inbound.conversationId(), inbound.lastMessageId());
        } else {
            supportService.markReadAsUser(actor, inbound.lastMessageId());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /** Khóa của {@link SupportSession} trong túi thuộc tính của phiên WebSocket. */
    private static final String ATTR_SESSION = "supportSession";

    private static SupportSession sessionOf(WebSocketSession raw) {
        Object found = raw.getAttributes().get(ATTR_SESSION);
        return found instanceof SupportSession session ? session : null;
    }

    /**
     * Ai đang gửi khung tin này, đọc lại từ cơ sở dữ liệu.
     *
     * <p>Và chỗ duy nhất bắt được "quyền đổi giữa chừng". Một quản trị viên vừa
     * bị hạ quyền vẫn không <i>làm</i> được gì — mọi phép kiểm ở tầng service
     * dùng vai trò vừa đọc — nhưng kết nối của họ vẫn nằm trong nhóm "phía hỗ
     * trợ" của sổ định tuyến, tức là vẫn <i>nhận</i> được tin của mọi luồng.
     * Đóng kết nối là cách duy nhất sửa việc ấy ngay; nối lại cho họ một chỗ
     * đúng trong sổ.
     *
     * @return null nghĩa là kết nối vừa bị đóng và bên gọi phải dừng lại
     */
    private SupportService.Actor actorOf(SupportSession session) {
        SupportService.Actor actor = supportService.resolveActor(session.userId());
        if (actor.role() != session.role()) {
            log.info("Đóng kết nối hỗ trợ {}: quyền của người {} đã đổi {} → {}",
                    session.id(), session.userId(), session.role(), actor.role());
            sendError(session, "ROLE_CHANGED",
                    "Quyền của tài khoản vừa thay đổi. Vui lòng tải lại trang.", null);
            session.close(SupportCloseCodes.ACCESS_REVOKED);
            registry.unregister(session);
            return null;
        }
        return actor;
    }

    private void sendError(SupportSession session, String code, String message,
                           String clientMessageId) {
        String payload = realtime.frame(SupportRealtime.EVENT_ERROR,
                new ErrorFrame(code, message, clientMessageId));
        if (payload != null) {
            registry.sendTo(session, payload);
        }
    }

    private static void closeQuietly(WebSocketSession raw, CloseStatus status) {
        try {
            raw.close(status);
        } catch (Exception ex) {
            log.debug("Đóng kết nối {} không sạch: {}", raw.getId(), ex.getMessage());
        }
    }

    /**
     * Khung tin đi lên, và danh sách những gì nó <b>không</b> có.
     *
     * <p>Không có {@code senderId}. Không có {@code senderRole}. Không có
     * {@code createdAt}. Không có {@code status} của tin nhắn. Chúng không bị
     * "bỏ qua" — chúng không có biến nào để nhận, và
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} khiến việc gửi chúng
     * lên là một việc không có tác dụng gì thay vì một lỗi.
     *
     * <p>Đó là hình thức mạnh nhất của "không tin trình duyệt": không phải một
     * phép kiểm có thể viết thiếu, mà là sự vắng mặt của chỗ để cất giá trị.
     *
     * @param conversationId chỉ có nghĩa với quản trị viên; nhánh của người đọc
     *                       không đọc tới nó
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Inbound(String type,
                   String clientMessageId,
                   String content,
                   Long conversationId,
                   Long lastMessageId) {
    }

    /** Khung tin mở màn: đủ để giao diện dựng ô soạn tin đúng trần ký tự. */
    record ReadyFrame(SupportSenderRole role,
                      int maxMessageLength,
                      int historyPageSize,
                      Instant serverTime) {
    }

    /**
     * Kết quả một lượt gửi, trả về đúng cửa sổ đã gửi.
     *
     * <h3>Ngữ nghĩa của {@code status}</h3>
     * <pre>
     *   ACCEPTED  → tin vừa được ghi xuống cơ sở dữ liệu và đã commit
     *   DUPLICATE → lần bấm gửi này đã được ghi từ trước; {@code messageId} là
     *               id của tin đã có, không phải một tin thứ hai
     * </pre>
     *
     * Cả hai đều là <b>thành công</b> với trình duyệt: cả hai đều nghĩa là câu
     * ấy đã nằm trong cơ sở dữ liệu đúng một lần. Đó chính là điều khiến việc
     * gửi lại an toàn — và điều khiến nó an toàn là {@code clientMessageId} được
     * giữ nguyên qua mọi lần thử.
     *
     * <p>Không có {@code REJECTED}: một lượt gửi bị từ chối đi ra bằng khung tin
     * {@code error} với mã riêng của nó, vì lý do từ chối là thứ giao diện phải
     * phân biệt được — "quá dài" và "luồng đã bị khóa" dẫn tới hai màn hình khác
     * nhau. Nhét cả hai vào một trạng thái của báo nhận là mất đúng phần thông
     * tin ấy.
     *
     * @param createdAt mốc của máy chủ. Trình duyệt thay mốc lạc quan của mình
     *                  bằng mốc này, nên thứ tự hiển thị không bao giờ phụ thuộc
     *                  vào đồng hồ máy khách.
     */
    record AckFrame(String clientMessageId,
                    Long messageId,
                    Long conversationId,
                    AckStatus status,
                    Instant createdAt) {
    }

    enum AckStatus {
        ACCEPTED,
        DUPLICATE
    }

    /**
     * Một lời từ chối.
     *
     * @param code             mã ổn định, giống hệt mã mà đường REST trả về cho
     *                         cùng tình huống — xem {@code SupportException}
     * @param clientMessageId  có mặt khi lỗi thuộc về một lượt gửi cụ thể, để
     *                         trình duyệt đánh dấu đúng bong bóng tin nhắn ấy là
     *                         thất bại thay vì hiện một thông báo chung chung
     */
    record ErrorFrame(String code, String message, String clientMessageId) {
    }
}
