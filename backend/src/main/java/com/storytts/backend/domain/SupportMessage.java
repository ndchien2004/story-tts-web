package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Một câu trong luồng hỗ trợ.
 *
 * <h3>Hàng này là nguồn sự thật, khung tin WebSocket chỉ là bản sao</h3>
 * Khung tin được đẩy đi <i>sau khi</i> giao dịch đã commit — xem
 * {@code SupportRealtime}. Mất khung tin (mạng đứt, tab ngủ, máy chủ khởi động
 * lại, một bản ứng dụng khác đang giữ kết nối) thì hàng vẫn ở đây, và lần đồng
 * bộ kế tiếp tìm thấy nó. Không có hàng đợi tin chưa gửi và không có cơ chế gửi
 * lại, vì không có "lần gửi" nào là lần cuối cùng.
 *
 * <h3>{@link #id} là thứ tự</h3>
 * {@code bigint auto_increment} của InnoDB, nên nó tăng đơn điệu trong phạm vi
 * một máy chủ. Mọi câu truy vấn lịch sử đều {@code ORDER BY id}, và con trỏ
 * phân trang cũng là {@code id} — không phải {@link #createdAt}, vốn có thể
 * trùng nhau tới từng micro giây và vốn là thứ mà một lần đồng bộ đồng hồ nhảy
 * ngược sẽ làm hỏng. Đồng hồ của trình duyệt thì không tham gia vào việc này ở
 * bất kỳ chỗ nào.
 *
 * <h3>Một khoảng hở mà {@code auto_increment} tạo ra, và cách nó được vá</h3>
 * Số thứ tự được cấp lúc {@code INSERT}, không phải lúc commit. Nên hai giao
 * dịch song song có thể commit ngược thứ tự id, và một trình duyệt vừa đồng bộ
 * tới id 11 sẽ không bao giờ hỏi lại id 10 nếu id 10 commit muộn hơn.
 *
 * <p>Chỗ hở ấy hẹp — nó chỉ rộng bằng quãng một giao dịch ba câu SQL — nhưng nó
 * có thật. Nó được vá ở phía trình duyệt: mỗi lần nối lại, trang tải <i>trang
 * cuối</i> của lịch sử chứ không chỉ xin phần "sau con trỏ", rồi gộp theo id.
 * Một lần đọc xảy ra <i>sau</i> khoảnh khắc hở thì thấy cả hai hàng. Xem
 * {@code useSupportConversation}.
 *
 * <h3>{@link #clientMessageId} — chỗ chống trùng</h3>
 * Đường mạng đứt sau khi máy chủ đã ghi xong nhưng trước khi lời báo nhận về
 * tới nơi là một chuyện <b>sẽ</b> xảy ra, và lần thử lại của trình duyệt mang
 * đúng chuỗi này. {@code UNIQUE (conversation_id, sender_id, client_message_id)}
 * biến "một lần bấm gửi = một tin nhắn" thành một quy tắc của cơ sở dữ liệu chứ
 * không phải một phép kiểm trong Java mà hai request song song đều vượt qua
 * được.
 *
 * <h3>Những thứ cố ý không có ở đây</h3>
 * <ul>
 *   <li><b>{@code deleted_at}</b> — sửa và xóa tin nhắn không nằm trong phạm vi
 *       tính năng, nên một cột không mã nào ghi vào chỉ là một lời hứa suông
 *       trong lược đồ.</li>
 *   <li><b>{@code status} kiểu SENT/DELIVERED/READ</b> — trạng thái ấy không
 *       thuộc về <i>tin nhắn</i>, nó thuộc về quan hệ giữa tin nhắn và một
 *       người đọc. Nó được suy ra từ mốc đã đọc của phía bên kia
 *       ({@code SupportConversation.readMarkOf}), nên không có cột nào phải cập
 *       nhật cho từng hàng mỗi lần ai đó cuộn xuống đáy.</li>
 *   <li><b>HTML</b> — {@link #content} là văn bản thuần, và không có chỗ nào
 *       trong giao diện dựng nó thành HTML. Cùng chính sách với
 *       {@code Notification.message}.</li>
 * </ul>
 */
@Entity
@Table(
        name = "support_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_support_messages_client",
                columnNames = {"conversation_id", "sender_id", "client_message_id"}),
        indexes = {
                @Index(name = "idx_support_messages_conversation",
                        columnList = "conversation_id, id"),
                @Index(name = "idx_support_messages_unread",
                        columnList = "conversation_id, sender_role, id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportMessage {

    /**
     * Trần của <i>lược đồ</i>, không phải trần mà máy chủ áp.
     *
     * <p>Trần thật nằm ở cấu hình ({@code app.support.max-message-length}) và
     * luôn nhỏ hơn hoặc bằng con số này. Hai tầng vì chúng trả lời hai câu khác
     * nhau: một cái là chính sách sản phẩm siết lại được bất cứ lúc nào, một cái
     * là chốt chặn cuối không cho một lỗi ở tầng trên ghi được hàng quá khổ.
     */
    public static final int CONTENT_LIMIT = 4000;

    /** Đủ cho một UUID có gạch nối, và không đủ cho một chỗ nhét dữ liệu. */
    public static final int CLIENT_ID_LIMIT = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_support_messages_conversation"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SupportConversation conversation;

    /**
     * Người gửi. Có với mọi tin của người thật; {@code null} với tin của trợ lý.
     *
     * <p>Tin hệ thống cũng mang id của chính người đã gây ra nó — quản trị viên
     * bấm đóng luồng, hoặc người đọc bấm xin gặp tư vấn viên — nên không có
     * hàng nào <i>của người</i> mà không truy được về một tài khoản thật.
     *
     * <h4>Vì sao V16 nới cột này thành nullable</h4>
     * V15 bắt {@code NOT NULL} vì ràng buộc duy nhất bên trên cần nó: MySQL coi
     * mỗi {@code NULL} là một giá trị khác nhau, nên một cột nullable sẽ để lọt
     * đúng những hàng cần chặn nhất. Lập luận ấy vẫn đúng — và nó vẫn có hiệu
     * lực ở đúng chỗ nó được dựng ra để giữ, vì tin của {@code USER} và
     * {@code ADMIN} trên thực tế luôn có {@code sender_id}.
     *
     * <p>Trợ lý thì không phải một người. Đường còn lại là dựng một hàng ma
     * trong {@code users} để trỏ tới, và tài khoản ma ấy sẽ hiện ra ở danh sách
     * người dùng, ở ô tìm kiếm của hộp thư, và ở mọi phép đếm về sau.
     * {@code NULL} nói đúng sự thật: câu này không của ai trong {@code users}.
     *
     * <p>Chống trùng cho hàng của trợ lý vì thế nằm ở chỗ khác, và chỗ ấy chặt
     * hơn: {@link #clientMessageId} của một câu trả lời được <i>suy ra</i> từ id
     * của chính câu hỏi, và phép tra trước khi ghi diễn ra khi hàng cuộc trò
     * chuyện đang bị khóa. Xem {@code SupportAssistant#replyIdFor}.
     *
     * @see #hasHumanSender()
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id",
            foreignKey = @ForeignKey(name = "fk_support_messages_sender"))
    private User sender;

    /** Xem {@link SupportSenderRole}: chốt tại thời điểm gửi, không suy ra lúc đọc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private SupportSenderRole senderRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private SupportMessageType messageType = SupportMessageType.TEXT;

    /** Văn bản thuần, đã chuẩn hóa và đã cắt bỏ ký tự điều khiển. Xem {@code SupportContent}. */
    @Column(nullable = false, length = CONTENT_LIMIT)
    private String content;

    /** Xem ghi chú ở đầu lớp. */
    @Column(name = "client_message_id", nullable = false, length = CLIENT_ID_LIMIT)
    private String clientMessageId;

    /**
     * Mốc do máy chủ đặt, luôn luôn.
     *
     * <p>Trình duyệt có gửi kèm mốc của nó cũng không được đọc tới: đồng hồ của
     * máy khách sai được cả năm, và một tin nhắn "gửi từ năm 2030" sẽ nằm mãi ở
     * đáy mọi danh sách.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Tin này có tính vào số chưa đọc của bên kia không. Xem {@link SupportMessageType}. */
    public boolean countsAsUnread() {
        return messageType == SupportMessageType.TEXT;
    }

    /**
     * Tin này do một tài khoản thật gửi, hay do trợ lý.
     *
     * <p>Một chỗ duy nhất cho phép kiểm ấy, vì mọi đường dựng DTO đều phải làm
     * nó trước khi chạm tới {@link #sender} — và quên một chỗ thì hậu quả là
     * một {@code NullPointerException} ở giữa hộp thư chứ không phải một dòng
     * hiển thị lệch.
     */
    public boolean hasHumanSender() {
        return sender != null && senderRole != SupportSenderRole.AI;
    }
}
