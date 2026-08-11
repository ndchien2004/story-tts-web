package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một lần người dùng mua VIP.
 *
 * <p>Số tháng và số tiền được sao chép từ {@link VipPlan} lúc tạo đơn thay vì
 * đọc lại từ gói khi thanh toán xong: gói có thể đổi giá hoặc bị xóa, còn đơn
 * đã trả tiền thì phải cộng đúng những gì người dùng đã mua.
 *
 * <p>{@link #orderCode} là số PayOS dùng để định danh giao dịch — bắt buộc là
 * số nguyên dương và không được trùng trong cùng một kênh thanh toán, nên nó
 * cũng là khóa để tra cứu đơn khi webhook gọi về.
 */
@Entity
@Table(
        name = "vip_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_vip_orders_order_code", columnNames = "order_code"),
        indexes = {
                @Index(name = "idx_vip_orders_user", columnList = "user_id"),
                @Index(name = "idx_vip_orders_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VipOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đơn gửi sang PayOS. Xem ghi chú ở đầu lớp. */
    @Column(name = "order_code", nullable = false)
    private Long orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Có thể null nếu gói đã bị xóa; các trường chép lại bên dưới vẫn đủ dùng. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private VipPlan plan;

    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @Column(name = "months", nullable = false)
    private int months;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VipOrderStatus status = VipOrderStatus.PENDING;

    /** Định danh link thanh toán bên PayOS, dùng khi cần hỏi lại tình trạng. */
    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Hạn VIP sau khi đơn này được cộng — giữ lại để đối chiếu về sau. */
    @Column(name = "vip_until_after")
    private Instant vipUntilAfter;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isPaid() {
        return status == VipOrderStatus.PAID;
    }
}
