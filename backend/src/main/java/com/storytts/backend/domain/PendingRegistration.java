package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một lượt đăng ký đang chờ xác thực email.
 *
 * <p>Đây là lý do bảng này tồn tại: tài khoản chỉ được ghi vào {@code users}
 * sau khi người đăng ký nhập đúng mã gửi tới hòm thư, nên thông tin họ khai
 * phải nằm tạm ở đâu đó trong lúc chờ. Không có bảng riêng thì hoặc là tạo
 * trước một tài khoản chưa xác thực trong {@code users}, hoặc là tin vào dữ
 * liệu client gửi lại ở bước hai — cả hai đều tệ hơn.
 *
 * <p>Mật khẩu ở đây đã băm sẵn bằng BCrypt, giống hệt lúc nằm trong
 * {@code users}: một lượt đăng ký bỏ dở cũng không được để lại mật khẩu thô.
 */
@Entity
@Table(
        name = "pending_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_pending_reg_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    /** SHA-256 của mã OTP dạng hex, luôn đúng 64 ký tự. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Số lần nhập sai.
     *
     * <p>Mã chỉ có sáu chữ số nên thứ thật sự chặn việc dò không phải là băm mà
     * là con số này: hết lượt thì lượt đăng ký bị hủy, muốn tiếp thì làm lại từ đầu.
     */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Mốc gửi mã gần nhất, dùng để chặn bấm "gửi lại" liên tục. */
    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
