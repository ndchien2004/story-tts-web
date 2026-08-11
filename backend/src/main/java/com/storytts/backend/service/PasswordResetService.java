package com.storytts.backend.service;

import com.storytts.backend.config.AppMailProperties;
import com.storytts.backend.domain.PasswordResetToken;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.PasswordResetTokenRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Quên mật khẩu: phát liên kết một lần qua email rồi đổi mật khẩu theo liên kết đó.
 *
 * <p>Hai nguyên tắc chi phối lớp này:
 *
 * <ol>
 *   <li><b>Không tiết lộ email nào đã đăng ký.</b> Yêu cầu gửi liên kết luôn trả
 *       lời giống nhau, kể cả khi địa chỉ đó không có trong hệ thống — nếu không,
 *       trang này thành công cụ dò danh sách người dùng.
 *   <li><b>Cơ sở dữ liệu chỉ giữ băm của token.</b> Chuỗi gốc chỉ nằm trong email,
 *       nên đọc được bảng cũng không dựng lại được liên kết.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    /** 32 byte ngẫu nhiên, quá thưa để dò trúng trong thời gian token còn sống. */
    private static final int TOKEN_BYTES = 32;

    /** Bản ghi hết hạn quá lâu thì không còn giá trị tra cứu, dọn đi cho gọn bảng. */
    private static final Duration KEEP_EXPIRED_FOR = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppMailProperties properties;

    private final SecureRandom random = new SecureRandom();

    public boolean isAvailable() {
        return mailService.isConfigured();
    }

    /** Gửi liên kết đặt lại mật khẩu, nếu địa chỉ này thuộc về một tài khoản đang hoạt động. */
    @Transactional
    public void requestReset(String rawEmail) {
        requireAvailable();

        String email = rawEmail.trim().toLowerCase();
        Optional<User> found = userRepository.findByEmail(email).filter(User::isEnabled);
        if (found.isEmpty()) {
            log.info("Yêu cầu đặt lại mật khẩu cho email không dùng được: {}", email);
            return;
        }

        User user = found.get();
        Instant now = Instant.now();
        tokenRepository.deleteExpiredBefore(now.minus(KEEP_EXPIRED_FOR));
        tokenRepository.invalidateAllFor(user, now);

        String token = newToken();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(now.plus(Duration.ofMinutes(properties.resetTokenTtlMinutes())))
                .build());

        mailService.sendPasswordReset(user, buildResetLink(token), properties.resetTokenTtlMinutes());
    }

    /** Đổi mật khẩu theo token trong liên kết, rồi đánh dấu token đã dùng. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        requireAvailable();

        PasswordResetToken record = tokenRepository.findByTokenHash(hash(token))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new BadRequestException(
                        "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới."));

        User user = record.getUser();
        if (!user.isEnabled()) {
            throw new BadRequestException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        record.setUsedAt(Instant.now());
        log.info("Đã đặt lại mật khẩu cho tài khoản {}", user.getUsername());
    }

    /* ------------------------------------------------------------------ */
    /* Token                                                               */
    /* ------------------------------------------------------------------ */

    /** Base64 dạng URL-safe, nhờ vậy ghép thẳng vào liên kết không cần mã hóa lại. */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không có thuật toán SHA-256", ex);
        }
    }

    private String buildResetLink(String token) {
        String separator = properties.resetUrl().contains("?") ? "&" : "?";
        return properties.resetUrl() + separator + "token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new BadRequestException(
                    "Chức năng gửi email chưa được cấu hình trên máy chủ. Vui lòng liên hệ quản trị viên.");
        }
    }
}
