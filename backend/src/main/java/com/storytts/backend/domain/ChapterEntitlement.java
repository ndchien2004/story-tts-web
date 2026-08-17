package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * "Người này được mở chương kia" — một dòng, vĩnh viễn.
 *
 * <h3>Vì sao tách khỏi sổ cái ví</h3>
 * Mua một chương bằng Xu sinh ra một dòng ở cả hai nơi, nên thoạt nhìn bảng này
 * là thừa. Nhưng hai bảng trả lời hai câu hỏi khác nhau, ở hai nhịp khác nhau:
 *
 * <ul>
 *   <li>"Người này đã trả bao nhiêu Xu, khi nào, cho cái gì" là câu hỏi kế toán.
 *       Hỏi vài lần một ngày, và câu trả lời phải đầy đủ tới từng đồng.</li>
 *   <li>"Người này có được mở chương 412 không" là câu hỏi quyền. Hỏi ở mọi lần
 *       tải trang, và phải trả lời được bằng một lần đọc chỉ mục — không phải
 *       bằng việc duyệt lịch sử tiền bạc của người ấy để xem có dòng nào trỏ tới
 *       chương ấy chưa.</li>
 * </ul>
 *
 * <p>Và quyền còn đến từ nơi khác ngoài tiền — xem {@link EntitlementSource}.
 *
 * <h3>Ràng buộc quan trọng nhất</h3>
 * {@code UNIQUE(user_id, chapter_id)} là thứ khiến bấm "Mở khóa" ba lần liên tiếp
 * không thể trừ Xu ba lần. Không phải nhờ mã nguồn kiểm tra trước — mã nguồn có
 * kiểm, nhưng hai request chạy song song thì cả hai đều thấy "chưa mua" — mà nhờ
 * cơ sở dữ liệu từ chối dòng thứ hai. Request thua cuộc bị cuộn ngược nguyên vẹn,
 * nên Xu của nó quay lại cùng.
 */
@Entity
@Table(
        name = "chapter_entitlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_entitlement_user_chapter",
                columnNames = {"user_id", "chapter_id"}
        ),
        indexes = @Index(name = "idx_entitlement_chapter", columnList = "chapter_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entitlement_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entitlement_chapter"))
    private Chapter chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EntitlementSource source;

    /**
     * Số Xu đã trả, chép lại tại thời điểm mua.
     *
     * <p>Giá chương đổi về sau không được phép làm sai lệch điều đã xảy ra rồi.
     * 0 với quyền do quản trị viên cấp.
     */
    @Column(name = "coins_spent", nullable = false)
    @Builder.Default
    private long coinsSpent = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
