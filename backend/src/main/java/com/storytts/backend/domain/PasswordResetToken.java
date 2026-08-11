package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một lượt yêu cầu đặt lại mật khẩu.
 *
 * <p>Bảng lưu <b>băm SHA-256</b> của token chứ không lưu token gốc: chuỗi gốc
 * chỉ tồn tại trong email gửi đi, nên người đọc được cơ sở dữ liệu vẫn không
 * dựng lại được liên kết để chiếm tài khoản.
 */
@Entity
@Table(
        name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_prt_token_hash", columnNames = "token_hash")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 của token dạng hex, luôn đúng 64 ký tự. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Khác null nghĩa là token đã dùng rồi hoặc đã bị thay bằng lượt yêu cầu mới. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Còn dùng được: chưa ai dùng và chưa hết hạn. */
    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
