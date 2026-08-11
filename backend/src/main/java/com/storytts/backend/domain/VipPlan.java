package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một gói VIP bán trên trang nâng cấp.
 *
 * <p>Giá và số tháng do Admin tự đặt trong bảng quản trị, nên thêm gói mới
 * ("6 tháng", "1 năm"…) không cần sửa mã nguồn. Đơn hàng chỉ tham chiếu tới gói
 * để tra cứu; số tháng và số tiền được sao chép sang đơn lúc tạo, vì một gói
 * đổi giá về sau không được phép làm thay đổi các đơn đã thanh toán.
 */
@Entity
@Table(name = "vip_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Số tháng VIP mà gói này cộng thêm. */
    @Column(name = "months", nullable = false)
    private int months;

    /** Giá bán, tính bằng đồng. PayOS chỉ nhận số nguyên. */
    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(length = 300)
    private String description;

    /** Gói tắt vẫn giữ nguyên trong lịch sử đơn, chỉ không bán nữa. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Thứ tự hiển thị trên trang nâng cấp; nhỏ hơn thì đứng trước. */
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

    /** Giá mỗi tháng — trang nâng cấp dùng để chỉ ra gói dài kỳ rẻ hơn ở đâu. */
    public long pricePerMonth() {
        return months <= 0 ? priceVnd : Math.round((double) priceVnd / months);
    }
}
