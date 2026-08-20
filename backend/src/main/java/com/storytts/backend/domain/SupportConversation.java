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
     * @return true nếu mốc thật sự tiến lên
     */
    public boolean advanceReadMark(SupportSenderRole side, Long messageId) {
        if (messageId == null) {
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
