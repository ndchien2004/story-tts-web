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
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MEMBER;

    /** Admin cấp/thu hồi thủ công trong trang quản trị. */
    @Column(name = "is_vip", nullable = false)
    @Builder.Default
    private boolean vip = false;

    @Column(name = "display_name", length = 100)
    private String displayName;

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
}
