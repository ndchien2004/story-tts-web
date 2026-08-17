package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một dòng sổ cái Xu — bất biến, chỉ ghi thêm, không bao giờ sửa hay xóa.
 *
 * <h3>Vì sao {@link #amount} có dấu</h3>
 * Âm là trừ, dương là cộng. Nhờ vậy bất biến của cả hệ thống viết được thành một
 * dòng — tổng {@code amount} của một người phải bằng {@code balance} ví của người
 * ấy — thay vì phải rẽ nhánh theo {@link #type} mới biết nên cộng hay trừ. Một
 * loại giao dịch mới thêm vào sau này cũng không đụng tới phép kiểm ấy.
 *
 * <h3>Vì sao lưu cả số dư trước và sau</h3>
 * Dư thừa về mặt dữ liệu, và cố ý dư thừa. Không có hai cột này, phát hiện được
 * số dư lệch với lịch sử cũng không tìm ra chỗ lệch. Có chúng thì việc ấy là tìm
 * dòng đầu tiên có {@code balanceBefore} khác {@code balanceAfter} của dòng liền
 * trước — một câu truy vấn, không phải một cuộc điều tra.
 *
 * <h3>Những trường cố ý không có</h3>
 * <ul>
 *   <li><b>{@code status}</b> — mọi dòng ở đây được ghi bên trong giao dịch đã
 *       commit của thao tác sinh ra nó. Không có dòng nào ở trạng thái chờ, nên
 *       một cột trạng thái sẽ luôn mang đúng một giá trị.</li>
 *   <li><b>{@code currency}</b> — chỉ có một đơn vị là Xu. Cột này đáng có vào
 *       ngày xuất hiện đơn vị thứ hai, không phải trước đó.</li>
 *   <li><b>{@code idempotency_key}</b> — tính một-lần ở đây đến từ ràng buộc cơ
 *       sở dữ liệu chứ không từ một khóa do client gửi lên:
 *       {@code UNIQUE(user_id, chapter_id)} cho việc mua chương, và câu UPDATE
 *       có điều kiện trên đơn thanh toán cho việc nạp Xu. Cả hai đều chặt hơn một
 *       cột khóa, vì chúng không thể bị bỏ qua bởi một client quên gửi.</li>
 * </ul>
 */
@Entity
@Table(
        name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_wallet_tx_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_wallet_tx_reference", columnList = "reference_type, reference_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wallet_tx_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WalletTransactionType type;

    /** Có dấu: âm là trừ, dương là cộng. Xem ghi chú ở đầu lớp. */
    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_before", nullable = false)
    private long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private WalletReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /** Câu người dùng đọc được trong trang lịch sử giao dịch. */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
