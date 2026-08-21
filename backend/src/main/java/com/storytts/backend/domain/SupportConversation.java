package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Luồng hỗ trợ của <b>một</b> người đọc, kéo dài suốt đời tài khoản.
 *
 * <h3>Một người, một luồng, vĩnh viễn</h3>
 * {@code UNIQUE (user_id)} — xem V15 — và đó chính là phần chống đua của việc
 * tạo. Hai tab cùng bấm "Liên hệ hỗ trợ" thì một bên thua ở tầng cơ sở dữ liệu
 * và chỉ việc đọc lại hàng đã có; không có phép kiểm nào trong Java mà hai
 * luồng song song cùng vượt qua được.
 *
 * <p>Phương án "một luồng <i>đang mở</i> cho một người" bị bỏ vì hai lý do độc
 * lập: MySQL không có chỉ mục một phần để diễn đạt nó, và nó cắt một cuộc trao
 * đổi liên tục thành nhiều mảnh mà người hỗ trợ phải tự ghép lại. Ở đây luồng
 * là liên tục; {@link #status} nói nó đang ở giai đoạn nào.
 *
 * <h3>Bốn cột {@code lastMessage*} là bộ nhớ đệm, không phải nguồn sự thật</h3>
 * Chúng tồn tại để danh sách hộp thư của quản trị viên không phải hỏi thêm một
 * câu cho mỗi dòng — đúng cái N+1 mà một hộp thư ba mươi dòng sẽ tạo ra. Chúng
 * được ghi trong <i>cùng</i> giao dịch với tin nhắn sinh ra chúng, nên không có
 * khoảnh khắc nào bảng này nói khác bảng kia; và mất chúng đi thì dựng lại được
 * bằng một câu truy vấn.
 *
 * <h3>Hai mốc đã đọc, không phải hai bộ đếm</h3>
 * {@link #userLastReadMessageId} và {@link #adminLastReadMessageId} là mốc đơn
 * điệu. Đây là khác biệt then chốt so với một con số chưa đọc lưu sẵn:
 *
 * <pre>
 *   bộ đếm → cộng khi có tin, trừ khi có người đọc → hai lệnh, chạy song
 *            song thì trôi đi không đường về
 *   mốc    → chỉ một lệnh: "đẩy lên tới id này"    → số chưa đọc luôn là
 *            một phép đếm dẫn xuất, không bao giờ sai
 * </pre>
 *
 * Hệ quả đúng theo yêu cầu: tin A, tin B, một lần đánh dấu đã đọc, rồi tin C —
 * C không bao giờ bị coi là đã đọc, vì id của nó lớn hơn mốc.
 *
 * <p>Phía quản trị là <i>một</i> mốc dùng chung cho mọi quản trị viên, và đó là
 * chủ ý: hộp thư hỗ trợ là hàng đợi của cả đội chứ không phải hộp thư riêng của
 * từng người. Một người đã đọc thì việc ấy đã xong với cả đội — cùng cách vận
 * hành của mọi hộp thư hỗ trợ dùng chung.
 *
 * <h3>Vì sao không có cột {@code version}</h3>
 * Đường ghi nóng duy nhất của bảng này — gửi một tin — vừa phải đọc
 * {@link #status} vừa phải ghi bốn cột {@code lastMessage*}, nên nó khóa hàng
 * bằng {@code SELECT ... FOR UPDATE} và giữ khóa ấy tới hết một giao dịch chỉ
 * gồm ba câu lệnh SQL. Khóa bi quan đã xếp hàng các lượt gửi lại rồi; thêm một
 * cột phiên bản chỉ biến mỗi lượt gửi thứ hai thành một lần thử lại cho cùng
 * một kết quả. Xem {@code SupportMessageStore}.
 */
@Entity
@Table(
        name = "support_conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_support_conversations_user",
                columnNames = "user_id"),
        indexes = {
                @Index(name = "idx_support_conversations_status_activity",
                        columnList = "status, last_message_at"),
                @Index(name = "idx_support_conversations_activity",
                        columnList = "last_message_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportConversation {

    /** Đúng chiều dài cột {@code last_message_preview} trong lược đồ — xem V15. */
    public static final int PREVIEW_LIMIT = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chủ luồng — người đọc.
     *
     * <p>Quản trị viên không có hàng ở đây: họ là "phía hỗ trợ", một phía chung,
     * không phải một người. Xóa tài khoản thì luồng đi theo, như hộp thư thông
     * báo ở V14.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_support_conversations_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SupportConversationStatus status = SupportConversationStatus.OPEN;

    /**
     * Ai đang nợ câu trả lời: trợ lý AI hay người thật. Xem
     * {@link SupportAssistantMode} và V16.
     *
     * <p>Mặc định {@code HUMAN} chứ không phải {@code AI}, và điều đó cố ý ở
     * cả hai đầu: những hàng có từ trước V16 giữ nguyên hành vi cũ, còn một
     * đường ghi mới nào đó quên đặt mode thì sinh ra một luồng hỗ trợ bình
     * thường — tới được quản trị viên — chứ không phải một luồng AI im lặng
     * nuốt mất câu hỏi.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_mode", nullable = false, length = 20)
    @Builder.Default
    private SupportAssistantMode assistantMode = SupportAssistantMode.HUMAN;

    /** Id tin nhắn cuối. Null khi luồng vừa tạo và chưa ai nói gì. */
    @Column(name = "last_message_id")
    private Long lastMessageId;

    /** Mốc xếp thứ tự của hộp thư quản trị. Xem ghi chú ở đầu lớp. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Vài chục ký tự đầu của tin cuối, để danh sách đọc được mà không cần mở. */
    @Column(name = "last_message_preview", length = PREVIEW_LIMIT)
    private String lastMessagePreview;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_sender_role", length = 20)
    private SupportSenderRole lastMessageSenderRole;

    /** Mốc đã đọc của người đọc. 0 nghĩa là chưa đọc gì. */
    @Column(name = "user_last_read_message_id", nullable = false)
    @Builder.Default
    private Long userLastReadMessageId = 0L;

    /** Mốc đã đọc dùng chung của phía hỗ trợ. Xem ghi chú ở đầu lớp. */
    @Column(name = "admin_last_read_message_id", nullable = false)
    @Builder.Default
    private Long adminLastReadMessageId = 0L;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * Ai đã đóng. Dấu vết kiểm toán, không phải khóa nghiệp vụ.
     *
     * <p>{@code ON DELETE SET NULL} chứ không cascade: mất một cái tên còn hơn
     * mất cả luồng hội thoại.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by",
            foreignKey = @ForeignKey(name = "fk_support_conversations_closed_by"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User closedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /* ------------------------------------------------------------------ */
    /* Chuyển trạng thái                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Bên này có được phép gửi vào luồng đang ở trạng thái hiện tại không.
     *
     * <p>Đúng một quy tắc, và nó nằm ở đây chứ không rải ra hai đường gọi (REST
     * và WebSocket): {@link SupportConversationStatus#BLOCKED} chặn người đọc,
     * không chặn quản trị viên. {@code CLOSED} không chặn ai — nó mở lại, xem
     * {@link #reopenOnNewMessage}.
     */
    public boolean allowsSendBy(SupportSenderRole role) {
        return status != SupportConversationStatus.BLOCKED || role == SupportSenderRole.ADMIN;
    }

    /**
     * Một tin mới kéo luồng đã đóng quay lại {@code OPEN}.
     *
     * <p>Gọi bên trong cùng giao dịch ghi tin ấy, sau khi hàng đã bị khóa. Đó là
     * điều kiện để cuộc đua "đóng đúng lúc người ta đang gõ" có kết cục xác
     * định: hoặc đóng xong rồi tin tới và mở lại, hoặc tin ghi xong rồi mới
     * đóng. Không có nhánh thứ ba nào mà câu vừa gõ biến mất.
     *
     * <p>{@code BLOCKED} thì không mở lại: quản trị viên trả lời vào một luồng
     * đã chặn không có nghĩa là bỏ chặn.
     *
     * @return true nếu lần gọi này thật sự đổi trạng thái
     */
    public boolean reopenOnNewMessage() {
        if (status != SupportConversationStatus.CLOSED) {
            return false;
        }
        status = SupportConversationStatus.OPEN;
        closedAt = null;
        closedBy = null;
        return true;
    }

    /**
     * Đổi trạng thái theo lệnh của quản trị viên.
     *
     * @return true nếu trạng thái thật sự đổi — bên gọi dùng nó để khỏi ghi một
     *         tin hệ thống cho một lệnh rỗng, và khỏi đẩy một khung tin cho một
     *         việc không xảy ra
     */
    public boolean transitionTo(SupportConversationStatus next, User actor, Instant when) {
        if (status == next) {
            return false;
        }
        status = next;
        if (next == SupportConversationStatus.CLOSED) {
            closedAt = when;
            closedBy = actor;
        } else {
            closedAt = null;
            closedBy = null;
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Trợ lý AI và việc chuyển cho người thật                             */
    /* ------------------------------------------------------------------ */

    /**
     * Luồng đã tạo nhưng chưa ai nói câu nào.
     *
     * <p>Đây là thứ giao diện dùng để biết có nên hỏi "bạn muốn chat với AI
     * hay gặp tư vấn viên?", và nó là một phép suy ra chứ không phải một
     * trạng thái được lưu. Lý do: không có quy tắc nào ở phía máy chủ đổi theo
     * nó, mà một giá trị enum không đổi quy tắc nào là một nhánh mà mọi phép
     * kiểm về sau phải nhớ tới.
     *
     * <p>Nó cũng vá một chỗ vốn có từ V15: {@code threadForUser} tạo luồng
     * ngay khi người ta <i>mở</i> hộp thoại hỗ trợ. Việc ấy vô hại — luồng
     * rỗng không bật huy hiệu của ai — nhưng nó khiến "có hàng trong bảng
     * chưa" không trả lời được câu "người này đã chọn gì chưa".
     */
    public boolean awaitingFirstWord() {
        return lastMessageId == null;
    }

    /** Trợ lý có được phép sinh câu trả lời cho luồng này ngay bây giờ không. */
    public boolean assistantMayReply() {
        return assistantMode.answeredByAssistant()
                && status != SupportConversationStatus.BLOCKED;
    }

    /**
     * Người đọc chọn trò chuyện với trợ lý AI.
     *
     * <p>Chỗ duy nhất trong lược đồ có đường đi <i>ngược</i> từ người thật về
     * máy, nên nó là chỗ duy nhất cần một điều kiện. Điều kiện ấy là
     * {@code CLOSED}: một cuộc trao đổi đang dở với tư vấn viên không được phép
     * bị trợ lý giành lấy sau lưng cả hai bên.
     *
     * <p>Nhưng cấm hẳn cũng sai. Luồng ở đây sống suốt đời tài khoản, nên "đã
     * từng gặp tư vấn viên một lần hồi tháng Giêng" mà cấm vĩnh viễn thì tính
     * năng này coi như không tồn tại với người ấy. {@code CLOSED} là ranh giới
     * đúng: hồ sơ cũ đã khép lại, và chính người đọc bấm chọn.
     *
     * <p>{@code BLOCKED} thì không: người bị chặn không được cấp thêm một
     * đường nói chuyện nào, kể cả với máy.
     *
     * @return true nếu lần gọi này thật sự đổi mode
     */
    public boolean startAssistantSession() {
        if (status == SupportConversationStatus.BLOCKED) {
            throw new IllegalStateException("Luồng đang bị khóa.");
        }
        if (assistantMode == SupportAssistantMode.AI) {
            return false;
        }
        if (assistantMode == SupportAssistantMode.HUMAN
                && status != SupportConversationStatus.CLOSED
                && !awaitingFirstWord()) {
            throw new IllegalStateException("Luồng đang do tư vấn viên phụ trách.");
        }
        assistantMode = SupportAssistantMode.AI;
        return true;
    }

    /**
     * Xin chuyển cho người thật.
     *
     * <p><b>Tính bất biến (idempotent) nằm ở đây, không nằm ở tầng gọi.</b> Bấm
     * nút hai lần, hai tab bấm cùng lúc, hoặc một lần thử lại sau khi mạng đứt
     * — lần thứ hai trở đi thấy mode đã rời khỏi {@code AI} và trả về false.
     * Bên gọi dùng nó để khỏi ghi một tin hệ thống thứ hai và khỏi đẩy một
     * khung tin cho một việc không xảy ra.
     *
     * <p>Phép kiểm này chỉ đáng tin khi hàng đang bị {@code SELECT ... FOR
     * UPDATE} giữ, và đó chính là điều {@code SupportStore} bảo đảm. Không có
     * ràng buộc cơ sở dữ liệu nào ở đây được, vì "đã chuyển giao" là một lần
     * đổi giá trị chứ không phải một hàng mới.
     *
     * @return true nếu lần gọi này thật sự chuyển giao
     */
    public boolean requestHandoff() {
        if (assistantMode != SupportAssistantMode.AI) {
            return false;
        }
        assistantMode = SupportAssistantMode.HANDOFF;
        return true;
    }

    /**
     * Đưa thẳng vào hàng đợi người thật, không đi qua trợ lý.
     *
     * <p>Đường của người bấm "Chat với tư vấn viên" ngay từ màn hình đầu. Khác
     * {@link #requestHandoff()} ở chỗ nó chấp nhận cả mode {@code HUMAN} —
     * nghĩa là nó cũng là đường mà một luồng cũ đã đóng quay lại hàng đợi.
     *
     * @return true nếu lần gọi này thật sự đổi mode
     */
    public boolean queueForHuman() {
        if (assistantMode == SupportAssistantMode.HANDOFF) {
            return false;
        }
        assistantMode = SupportAssistantMode.HANDOFF;
        return true;
    }

    /**
     * Một quản trị viên vừa động vào luồng: trả lời, đổi trạng thái, bất kỳ
     * việc gì có chủ ý.
     *
     * <p>Đây là chỗ {@code HANDOFF → HUMAN} xảy ra, và nó xảy ra <i>tự động</i>
     * chứ không bằng một nút "nhận việc" riêng. Lý do: một nút như thế là một
     * bước người trực có thể quên, và mỗi lần quên là một luồng nằm mãi trong
     * phép đếm chờ trả lời dù đã được trả lời. Hành động thật sự — gõ một câu —
     * là bằng chứng đáng tin hơn một cái bấm nút.
     *
     * <p>Nó cũng bao trùm {@code AI → HUMAN}: quản trị viên nhảy vào một cuộc
     * đang do trợ lý phụ trách thì quyền ưu tiên thuộc về người thật, và trợ lý
     * im ngay từ câu ấy. Chiều ngược lại không tự động bao giờ.
     *
     * @return true nếu lần gọi này thật sự đổi mode
     */
    public boolean takenOverByHuman() {
        if (assistantMode == SupportAssistantMode.HUMAN) {
            return false;
        }
        assistantMode = SupportAssistantMode.HUMAN;
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Bộ nhớ đệm của tin cuối                                             */
    /* ------------------------------------------------------------------ */

    /** Ghi lại tin cuối. Gọi trong cùng giao dịch với lệnh chèn tin ấy. */
    public void rememberLastMessage(SupportMessage message) {
        lastMessageId = message.getId();
        lastMessageAt = message.getCreatedAt();
        lastMessageSenderRole = message.getSenderRole();
        lastMessagePreview = preview(message.getContent());
    }

    /**
     * Mốc đã đọc của một phía.
     *
     * <p>Chỉ tăng, không bao giờ lùi: hai tab bấm lệch nhịp — cái đang xem dở
     * một đoạn cũ bấm sau cái đã cuộn xuống đáy — không được phép kéo con số
     * chưa đọc quay lại.
     *
     * <p>{@link SupportSenderRole#AI} không có mốc nào để tiến, và phép kiểm
     * ấy không phải hình thức: {@code SupportStore.append} gọi thẳng hàm này
     * với vai của người gửi, nên nếu không chặn thì mỗi câu trả lời của trợ lý
     * sẽ đẩy mốc của <i>quản trị viên</i> lên — âm thầm xóa sạch số chưa đọc
     * của một luồng mà người trực còn chưa mở tới.
     *
     * @return true nếu mốc thật sự tiến lên
     */
    public boolean advanceReadMark(SupportSenderRole side, Long messageId) {
        if (messageId == null || !side.isViewer()) {
            return false;
        }
        if (side == SupportSenderRole.USER) {
            if (userLastReadMessageId >= messageId) {
                return false;
            }
            userLastReadMessageId = messageId;
            return true;
        }
        if (adminLastReadMessageId >= messageId) {
            return false;
        }
        adminLastReadMessageId = messageId;
        return true;
    }

    /** Mốc đã đọc của một phía, để câu đếm chưa đọc dùng lại. */
    public Long readMarkOf(SupportSenderRole side) {
        return side == SupportSenderRole.USER ? userLastReadMessageId : adminLastReadMessageId;
    }

    /**
     * Cắt cho vừa cột thay vì để cơ sở dữ liệu từ chối cả hàng.
     *
     * <p>Cùng lập luận với {@code NotificationService.clamp}: cái giá của một
     * câu quá dài phải là một dòng xem trước bị cụt, chứ không phải một tin nhắn
     * không gửi được.
     */
    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.length() <= PREVIEW_LIMIT) {
            return flat;
        }
        return flat.substring(0, PREVIEW_LIMIT - 1) + "…";
    }
}
