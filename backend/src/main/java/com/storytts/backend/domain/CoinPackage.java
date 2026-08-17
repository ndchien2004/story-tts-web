package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một gói nạp Xu bán trên trang nạp tiền.
 *
 * <p>Cùng hình dạng với {@link VipPlan}, và cùng lý do: giá là dữ liệu, không
 * phải mã nguồn. Thêm gói "200.000đ" hay đổi tỉ lệ quy đổi là việc của trang
 * quản trị, không phải của một lần build lại.
 *
 * <p>Đơn hàng chỉ tham chiếu tới gói để tra cứu; số Xu và số tiền được chép sang
 * đơn lúc thanh toán, vì một gói đổi giá về sau không được phép làm thay đổi
 * những đơn đã trả tiền.
 */
@Entity
@Table(name = "coin_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Giá bán, tính bằng đồng. PayOS chỉ nhận số nguyên. */
    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    /** Số Xu cơ bản của gói. */
    @Column(nullable = false)
    private long coins;

    /**
     * Xu tặng thêm.
     *
     * <p>Tách khỏi {@link #coins} để giao diện nói được "500 + 50 tặng" thay vì
     * một con số 550 không giải thích được vì sao gói lớn lại đáng mua hơn.
     */
    @Column(name = "bonus_coins", nullable = false)
    @Builder.Default
    private long bonusCoins = 0L;

    @Column(length = 300)
    private String description;

    /** Gói tắt vẫn giữ nguyên trong lịch sử đơn, chỉ không bán nữa. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Thứ tự hiển thị trên trang nạp; nhỏ hơn thì đứng trước. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Số Xu người mua thực nhận. */
    public long totalCoins() {
        return coins + bonusCoins;
    }
}
