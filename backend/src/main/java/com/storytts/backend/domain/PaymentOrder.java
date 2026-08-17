package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một lần mua bằng tiền thật, qua cổng thanh toán.
 *
 * <p>{@link #orderCode} là số PayOS dùng để định danh giao dịch — bắt buộc là số
 * nguyên dương và không được trùng trong cùng một kênh thanh toán, nên nó cũng là
 * khóa để tra cứu đơn khi webhook gọi về.
 *
 * <h3>Một bảng cho hai thứ bán được</h3>
 * Gói VIP và gói Xu đi qua đúng một cổng và đúng một endpoint webhook. PayOS gọi
 * về với một {@code orderCode} và không nói gì thêm, nên nếu có hai bảng đơn thì
 * webhook phải tra hai nơi — và cơ chế chống cộng tiền hai lần phải được viết hai
 * lần. Cơ chế ấy là một câu UPDATE có điều kiện đóng vai compare-and-set; viết
 * đúng nó ở bản sao thứ nhất rồi viết sai ở bản sao thứ hai là kết cục mặc định.
 * Một bảng thì chỉ có một chỗ để viết đúng.
 *
 * <p>{@link #kind} quyết định nhóm cột nào có nghĩa, và quyết định việc gì xảy ra
 * khi tiền về — xem {@code PaymentOrderLedger.fulfil}.
 *
 * <h3>Vì sao mọi thứ đều được chép lại vào đơn</h3>
 * {@link #itemName}, {@link #amountVnd}, {@link #months}, {@link #coinsGranted}
 * đều là bản chụp tại thời điểm mua. Gói đổi giá hay đổi tên về sau không được
 * phép làm một đơn đã thanh toán kể lại câu chuyện khác. Khóa ngoại tới gói chỉ
 * để tra cứu, và nó nullable vì gói có thể bị xóa.
 */
@Entity
@Table(
        name = "payment_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_orders_order_code", columnNames = "order_code"),
        indexes = {
                @Index(name = "idx_payment_orders_user", columnList = "user_id"),
                @Index(name = "idx_payment_orders_status", columnList = "status"),
                @Index(name = "idx_payment_orders_kind_status", columnList = "kind, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đơn gửi sang PayOS. Xem ghi chú ở đầu lớp. */
    @Column(name = "order_code", nullable = false)
    private Long orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Thứ đang được mua, và do đó việc gì xảy ra khi tiền về. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderKind kind;

    /** Tên của thứ đã mua, chép lại lúc tạo đơn. */
    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    /* ----- Riêng đơn gói VIP ----- */

    /** Có thể null nếu gói đã bị xóa; các trường chép lại vẫn đủ dùng. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private VipPlan plan;

    /** Số tháng VIP đơn này cộng. Null với đơn nạp Xu. */
    @Column(name = "months")
    private Integer months;

    /** Hạn VIP sau khi đơn này được cộng — giữ lại để đối chiếu về sau. */
    @Column(name = "vip_until_after")
    private Instant vipUntilAfter;

    /* ----- Riêng đơn nạp Xu ----- */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coin_package_id",
            foreignKey = @ForeignKey(name = "fk_payment_orders_coin_package"))
    private CoinPackage coinPackage;

    /** Số Xu đã cộng, gồm cả phần tặng. Null với đơn VIP. */
    @Column(name = "coins_granted")
    private Long coinsGranted;

    /* ----- Chung ----- */

    /** Định danh link thanh toán bên PayOS, dùng khi cần hỏi lại tình trạng. */
    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isPaid() {
        return status == PaymentOrderStatus.PAID;
    }
}
