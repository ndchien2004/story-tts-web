package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Một dòng trong hộp thư của người đọc.
 *
 * <h3>Đây mới là nguồn sự thật, không phải luồng SSE</h3>
 * Khung tin đẩy xuống trình duyệt chỉ là bản sao của hàng này, gửi đi <i>sau
 * khi</i> giao dịch nghiệp vụ đã commit. Mất khung tin — mạng đứt, tab ngủ, máy
 * chủ khởi động lại — thì hàng vẫn ở đây và lần đồng bộ kế tiếp tìm thấy nó.
 * Người đang offline lúc quản trị viên cấp VIP vẫn thấy lời chúc mừng khi đăng
 * nhập lại, vì lời chúc ấy được ghi vào bảng này chứ không được thả vào một
 * socket không ai nghe.
 *
 * <h3>Vì sao không có cột {@code is_read}</h3>
 * {@link #readAt} trả lời được cả hai câu — "đã đọc chưa" là {@code readAt is
 * not null}, "đọc lúc nào" là chính nó. Thêm một cột boolean là có hai nguồn sự
 * thật cho một câu hỏi, và hai cột luôn phải bằng nhau là hai cột có thể lệch
 * nhau. Cùng lập luận đã dùng ở {@code gift_codes} khi từ chối cột
 * {@code status}.
 *
 * <h3>{@link #eventId} — chỗ chặn thông báo trùng</h3>
 * Mọi sự kiện thời gian thực đều nên được coi là <b>có thể tới hai lần</b>: một
 * webhook gọi lại, một lần thử lại của trình duyệt, một handler chạy hai lượt.
 * Nên mỗi thông báo mang theo định danh của <i>sự kiện nghiệp vụ</i> sinh ra
 * nó, và {@code UNIQUE (user_id, event_id)} biến "không tạo trùng" thành một
 * quy tắc của cơ sở dữ liệu chứ không phải một phép kiểm trong Java mà hai
 * luồng song song đều vượt qua được.
 *
 * <p>Định danh ấy được dựng từ chính sự việc — {@code chapter-deleted:41:7} là
 * "chương 41 bị gỡ, phần của người dùng 7" — nên xử lý lại cùng một lần xóa
 * không sinh ra dòng thứ hai. Nó <i>không</i> phải là thứ giữ cho tiền không bị
 * hoàn hai lần: việc ấy do ví và bảng quyền đọc lo, và phải đứng vững kể cả khi
 * bảng này trống. Thông báo là bản tường thuật, không phải sổ cái.
 *
 * <h3>Những thứ cố ý không lưu ở đây</h3>
 * <ul>
 *   <li><b>Số dư, số tiền thật</b> — {@link #metadata} có chép lại số Xu đã hoàn
 *       để câu chữ đọc được ngay, nhưng con số đáng tin nằm ở sổ cái ví. Trang
 *       thông báo luôn mời người đọc sang đó đối chiếu.</li>
 *   <li><b>HTML</b> — {@link #title} và {@link #message} là văn bản thuần. Không
 *       có chỗ nào trong giao diện dựng chúng thành HTML, nên nội dung do quản
 *       trị viên gõ không mở được đường chèn mã.</li>
 *   <li><b>Trạng thái gửi</b> — không có {@code sent}, {@code attempt_count} hay
 *       {@code last_error}. Việc gửi không phải một lời hứa cần theo dõi: hàng
 *       này tồn tại là đủ, và trình duyệt tự lấy về những gì nó chưa thấy mỗi
 *       lần nối lại.</li>
 * </ul>
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_user_event",
                columnNames = {"user_id", "event_id"}),
        indexes = {
                @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_notifications_user_unread", columnList = "user_id, read_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người nhận. Xóa tài khoản thì hộp thư đi theo — không như sổ cái ví, ở đây
     * không còn gì đáng giữ lại khi người nhận không còn.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.INFO;

    /** Một dòng, đọc được ở danh sách thu gọn. Văn bản thuần. */
    @Column(nullable = false, length = 160)
    private String title;

    /** Vài câu, đọc được mà không cần bấm vào đâu. Văn bản thuần. */
    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40)
    private NotificationAction actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_entity_type", length = 30)
    private NotificationEntityType relatedEntityType;

    /** Xem ghi chú ở {@link NotificationEntityType}: cặp này là gợi ý, không phải khóa ngoại. */
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    /**
     * Vài con số phụ, dạng JSON phẳng — số Xu đã hoàn, số chương, hạn VIP.
     *
     * <p>Nhỏ và có chủ đích: đây là chỗ giữ cho lược đồ không phải mọc thêm cột
     * mỗi lần có một loại thông báo mới, chứ không phải chỗ đổ nguyên một bản
     * ghi vào. Trần 1000 ký tự là để điều đó không trôi dần.
     */
    @Column(length = 1000)
    private String metadata;

    /** Định danh sự kiện nghiệp vụ. Xem ghi chú ở đầu lớp. */
    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    /** Null nghĩa là chưa đọc. Xem ghi chú ở đầu lớp. */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    /**
     * Đánh dấu đã đọc, đúng một lần.
     *
     * @return true nếu lần gọi này thật sự đổi trạng thái — bên gọi dùng nó để
     *         khỏi phát một sự kiện đồng bộ cho một việc không xảy ra
     */
    public boolean markRead(Instant when) {
        if (readAt != null) {
            return false;
        }
        readAt = when;
        return true;
    }
}
