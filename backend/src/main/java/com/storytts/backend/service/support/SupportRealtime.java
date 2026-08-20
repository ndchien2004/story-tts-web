package com.storytts.backend.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportInboxItemDto;
import com.storytts.backend.dto.support.SupportMessageDto;
import com.storytts.backend.service.AccountAccessRevoked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cầu nối giữa "đã ghi xong" và "đã đẩy đi".
 *
 * <h3>Vì sao lớp này tồn tại tách khỏi cả hai bên</h3>
 * {@link SupportStore} biết cách ghi và không biết gì về WebSocket;
 * {@link SupportSocketRegistry} biết cách gửi và không biết gì về giao dịch.
 * Chỗ nối phải nằm ở đâu đó, và nó phải làm đúng ba việc mà không bên nào tự
 * làm được:
 *
 * <ol>
 *   <li><b>Đợi commit.</b> Mọi bộ lắng nghe ở đây đều là {@code AFTER_COMMIT}.
 *       Gửi sớm hơn thì một giao dịch cuộn ngược vẫn kịp báo "đã gửi" cho một
 *       tin nhắn không tồn tại — và người nhận sẽ đi tìm nó trong một luồng
 *       trống. Cùng mốc mà {@code UserEventStream} dùng.</li>
 *   <li><b>Tuần tự hóa đúng một lần.</b> Một tin nhắn đi tới bốn cửa sổ được
 *       dựng thành chuỗi một lần ở đây, không phải bốn lần trong vòng gửi.</li>
 *   <li><b>Chọn dạng cho từng phía.</b> Người đọc và quản trị viên nhìn thấy
 *       danh tính người gửi khác nhau — xem {@link SupportMessageDto}. Cả hai
 *       dạng đã được dựng sẵn trong sự kiện; ở đây chỉ còn việc gửi đúng cái nào
 *       tới đâu.</li>
 * </ol>
 *
 * <h3>Không có hàng đợi, không có gửi lại, và vì sao không cần</h3>
 * Đặc tả yêu cầu cân nhắc mẫu <i>transactional outbox</i>. Câu trả lời ở đây
 * giống hệt câu trả lời đã ghi cho hộp thư thông báo, và vì cùng lý do: bảng
 * {@code support_messages} <b>đã là</b> outbox. Nó được ghi trong cùng giao dịch
 * nghiệp vụ, và bên nhận không chờ ai gửi cho mình — trình duyệt tự hỏi lại lịch
 * sử ở mỗi lần nối lại và mỗi lần tab quay về tiền cảnh.
 *
 * <p>Nên một khung tin không đi được không mất mát gì: không có "lần gửi" nào là
 * lần cuối cùng. Thêm một bảng {@code outbox} với {@code status},
 * {@code attempt_count} và một tác vụ quét lại sẽ là bản sao thứ hai của cùng dữ
 * liệu, cộng một tiến trình nền trên một máy chủ ngủ sau mười lăm phút vắng
 * khách — nó giải quyết một vấn đề mà thiết kế này không có.
 *
 * <p>Điều đó <i>không</i> có nghĩa là mất khung tin thì không sao về mặt trải
 * nghiệm; nó có nghĩa là mất khung tin không mất dữ liệu. Phần trải nghiệm do
 * việc nối lại và đồng bộ lo, và đó là phần được kiểm ở
 * {@code useSupportThread}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupportRealtime {

    /* Tên khung tin. Hằng số vì chúng xuất hiện ở cả hai đầu và một lần gõ sai
     * sẽ hỏng im lặng — khung tin đi ra, không ai lắng nghe, không ai báo lỗi. */
    static final String EVENT_MESSAGE_NEW = "message:new";
    static final String EVENT_MESSAGE_ACK = "message:ack";
    static final String EVENT_MESSAGE_READ = "message:read";
    static final String EVENT_CONNECTION_READY = "connection:ready";
    static final String EVENT_ERROR = "error";

    private final SupportSocketRegistry registry;

    /**
     * Bean của ứng dụng, không phải một bản riêng.
     *
     * <p>Khác {@code NotificationService}, thứ cố ý dựng {@code ObjectMapper}
     * riêng để chạy được trong một lát cắt {@code @DataJpaTest}. Ở đây thì ngược
     * lại: khung tin WebSocket chở {@code Instant}, và nó <b>phải</b> được viết
     * ra giống hệt cách REST viết — cùng ISO-8601, cùng múi giờ. Một bản riêng
     * không có {@code JavaTimeModule} sẽ viết ra một mảng số, và trình duyệt sẽ
     * có hai cách đọc thời gian tùy theo dữ liệu đến từ đường nào.
     */
    private final ObjectMapper objectMapper;

    /* ------------------------------------------------------------------ */
    /* Tin nhắn                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Một tin nhắn vừa commit: đẩy xuống chủ luồng và xuống cả phía hỗ trợ.
     *
     * <p>Hai lượt gửi vì hai dạng dữ liệu khác nhau, không phải vì hai nhóm
     * người khác nhau. Nếu chỉ khác nhóm thì một khung tin là đủ.
     *
     * <p>Khung tin cũng tới cửa sổ của <i>chính người vừa gửi</i>, và đó là chủ
     * ý: các tab khác của họ cần thấy tin ấy. Tab đã gửi thì đã có nó rồi và bỏ
     * qua theo {@code clientMessageId} — xem {@code useSupportThread}. Lọc kết
     * nối gửi ra khỏi danh sách sẽ là tối ưu một lượt gửi bằng cách bắt máy chủ
     * nhớ kết nối nào đã gửi tin nào.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(SupportMessageCreated event) {
        String forUser = frame(EVENT_MESSAGE_NEW,
                new UserMessageFrame(event.userView(), event.userState()));
        String forAdmin = frame(EVENT_MESSAGE_NEW,
                new AdminMessageFrame(event.adminView(), event.adminState()));

        int toUser = forUser == null ? 0 : registry.sendToUser(event.ownerUserId(), forUser);
        int toAdmins = forAdmin == null ? 0 : registry.sendToAdmins(forAdmin);

        if (toUser + toAdmins > 0) {
            log.info("MESSAGE_DELIVERED ho-tro: luồng {}, {} cửa sổ người đọc, {} cửa sổ quản trị",
                    event.conversationId(), toUser, toAdmins);
        }
    }

    /**
     * Một phía vừa đọc tới đâu đó.
     *
     * <p>Đi tới cả hai phía bằng <i>một</i> hình dạng khung tin, và mỗi bên tự
     * đọc ra phần của mình bằng cách so {@code reader} với vai trò của chính nó:
     *
     * <pre>
     *   reader == vai của tôi → cập nhật con số chưa đọc (đồng bộ giữa các tab)
     *   reader != vai của tôi → cập nhật dấu "đã xem" (báo nhận)
     * </pre>
     *
     * Hai khung tin riêng sẽ là hai đường phải giữ đồng bộ để nói về đúng một
     * lần bấm.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadUpdated(SupportReadUpdated event) {
        String payload = frame(EVENT_MESSAGE_READ, new ReadFrame(
                event.conversationId(), event.reader(),
                event.lastReadMessageId(), event.readerUnread()));
        if (payload == null) {
            return;
        }
        registry.sendToUser(event.ownerUserId(), payload);
        registry.sendToAdmins(payload);
    }

    /* ------------------------------------------------------------------ */
    /* Thu hồi quyền                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Tài khoản vừa bị khóa hoặc vừa đổi quyền: cắt mọi kết nối của nó.
     *
     * <p>{@code AFTER_COMMIT} vì cùng lý do với mọi thứ khác trong lớp này — một
     * giao dịch cuộn ngược không được phép đá ai ra.
     *
     * <p>Đây là hàng rào thứ hai, không phải hàng rào chính: mỗi lệnh đều đọc
     * lại tài khoản từ cơ sở dữ liệu, nên một tài khoản bị khóa đã không gửi
     * được gì kể cả khi lời gọi này không chạy. Cái nó thêm vào là chặn đường
     * <i>nhận</i>, ngay lập tức. Xem {@link AccountAccessRevoked}.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessRevoked(AccountAccessRevoked event) {
        registry.revoke(event.userId(), SupportCloseCodes.ACCESS_REVOKED);
    }

    /*
     * Một cái bẫy của Spring, đáng ghi ra vì nó hỏng im lặng:
     * @TransactionalEventListener KHÔNG chạy gì cả khi sự kiện được phát ngoài
     * một giao dịch — không lỗi, không cảnh báo, khung tin đơn giản không bao
     * giờ đi ra.
     *
     * Cả ba nguồn sự kiện của lớp này đều phát từ bên trong một phương thức
     * @Transactional — SupportStore.append, SupportStore.markRead và
     * UserAdminService.setEnabled/setRole — nên nhánh ấy không tồn tại ở đây.
     * Thêm một nguồn thứ tư thì đây là điều phải kiểm lại đầu tiên.
     */

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Dựng một khung tin thành chuỗi JSON.
     *
     * <p>Trả về null khi không tuần tự hóa được, và bên gọi bỏ qua lượt gửi.
     * Ném ra ở đây sẽ là một ngoại lệ trên luồng đã commit xong — nó không cuộn
     * ngược được gì (giao dịch đã đóng) và chỉ làm hỏng nốt những lượt gửi còn
     * lại. Một khung tin không dựng được là một khung tin bị mất, và mất khung
     * tin thì đã có đường đồng bộ lo.
     */
    String frame(String type, Object payload) {
        try {
            return objectMapper.writeValueAsString(new Envelope(type, payload));
        } catch (JsonProcessingException ex) {
            log.warn("Không dựng được khung tin hỗ trợ loại {}: {}", type, ex.getMessage());
            return null;
        }
    }

    /**
     * Vỏ chung của mọi khung tin đi ra.
     *
     * <p>Một trường {@code type} ở ngoài cùng là thứ khiến bên nhận phân nhánh
     * được <i>trước</i> khi phải hiểu phần còn lại — và là thứ khiến việc thêm
     * một loại khung tin mới không làm hỏng máy khách cũ: chúng không nhận ra
     * {@code type} ấy và bỏ qua.
     */
    record Envelope(String type, Object payload) {
    }

    /** Tin nhắn mới, dạng người đọc nhìn thấy. */
    record UserMessageFrame(SupportMessageDto message, SupportConversationDto conversation) {
    }

    /** Tin nhắn mới, dạng khu quản trị nhìn thấy — kèm đủ thứ để cập nhật một dòng hộp thư. */
    record AdminMessageFrame(SupportMessageDto message, SupportInboxItemDto inbox) {
    }

    /** Một phía vừa đọc tới đâu. Xem {@link #onReadUpdated}. */
    record ReadFrame(Long conversationId, SupportSenderRole reader,
                     Long lastReadMessageId, long readerUnread) {
    }
}
