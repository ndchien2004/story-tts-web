package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng users.
 * Quyền đọc chương được xác định bởi cặp {@link #role} + {@link #vip}.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_google_id", columnNames = "google_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 150)
    private String email;

    /** Mật khẩu đã băm bằng BCrypt — không bao giờ lưu plaintext. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Định danh tài khoản Google (claim {@code sub}), null nếu chưa liên kết.
     *
     * <p>Khóa theo {@code sub} chứ không theo email: Google cho phép đổi địa chỉ
     * email của một tài khoản, còn {@code sub} thì không bao giờ đổi.
     */
    @Column(name = "google_id", length = 64)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MEMBER;

    /**
     * VIP vĩnh viễn do Admin cấp/thu hồi thủ công trong trang quản trị.
     *
     * <p>Tách khỏi {@link #vipUntil}: quyền Admin cấp tay không có hạn và không
     * bị một gói mua hết hạn cuốn theo, còn gói mua thì ngược lại. Quyền đọc
     * chương VIP xét cả hai — xem {@link #isVip()}.
     */
    @Column(name = "is_vip", nullable = false)
    @Builder.Default
    private boolean vipGranted = false;

    /** Hạn VIP mua bằng tiền. Null nghĩa là chưa từng mua gói nào. */
    @Column(name = "vip_until")
    private Instant vipUntil;

    @Column(name = "display_name", length = 100)
    private String displayName;

    /** URL ảnh đại diện trên Cloudinary. Null nghĩa là dùng chữ cái đầu làm ảnh. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Quyền VIP đang có hiệu lực.
     *
     * <p>Cố ý giữ tên {@code isVip()}: mọi nơi hỏi "người này có phải VIP
     * không" — token, DTO, lớp kiểm tra quyền đọc chương — đều đi qua đây, nên
     * việc thêm gói có hạn không cần lan ra các lớp đó.
     */
    public boolean isVip() {
        return vipGranted || hasActiveSubscription();
    }

    /** Đang trong hạn của một gói đã mua. */
    public boolean hasActiveSubscription() {
        return vipUntil != null && vipUntil.isAfter(Instant.now());
    }

    /**
     * Cộng thêm số tháng vừa mua.
     *
     * <p>Mua nối khi hạn cũ còn hiệu lực thì cộng dồn từ hạn cũ, không phải từ
     * hôm nay — nếu không, gia hạn sớm sẽ ăn mất phần chưa dùng.
     */
    public Instant extendVip(int months) {
        Instant from = hasActiveSubscription() ? vipUntil : Instant.now();
        vipUntil = from.atZone(java.time.ZoneOffset.UTC).plusMonths(months).toInstant();
        return vipUntil;
    }
}
