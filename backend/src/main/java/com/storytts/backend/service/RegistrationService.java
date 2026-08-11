package com.storytts.backend.service;

import com.storytts.backend.domain.PendingRegistration;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.RegisterResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.PendingRegistrationRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Đăng ký tài khoản, có bước xác thực email bằng mã OTP.
 *
 * <p>Điểm cốt lõi: <b>hàng trong bảng {@code users} chỉ xuất hiện sau khi mã
 * được nhập đúng.</b> Trong lúc chờ, thông tin đăng ký nằm ở
 * {@link PendingRegistration}. Nhờ vậy không có tài khoản nửa vời nào chiếm chỗ
 * một địa chỉ email mà chủ nhân địa chỉ đó chưa từng đồng ý.
 *
 * <p>Máy chủ chưa cấu hình SMTP thì không có đường nào gửi mã đi, nên luồng lùi
 * về cách cũ — tạo tài khoản ngay — thay vì làm chức năng đăng ký ngừng hoạt
 * động hoàn toàn. Giống cách Cloudinary, PayOS và đăng nhập Google tự tắt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /** Mã chỉ có sáu chữ số, nên giới hạn số lần đoán mới là thứ bảo vệ nó. */
    private static final int MAX_ATTEMPTS = 5;

    private static final int CODE_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AuthService authService;

    private final SecureRandom random = new SecureRandom();

    /** Bước một: nhận thông tin đăng ký và gửi mã xác thực. */
    @Transactional
    public RegisterResponse start(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        requireIdentityAvailable(username, email);

        if (!mailService.isConfigured()) {
            log.info("Chưa cấu hình SMTP, bỏ qua bước xác thực email cho {}", username);
            return RegisterResponse.completed(authService.issueSession(
                    createUser(username, email, request.password(), request.displayName())));
        }

        Instant now = Instant.now();
        pendingRepository.deleteExpiredBefore(now);
        if (pendingRepository.usernameHeldByAnother(username, email, now)) {
            throw new BadRequestException(
                    "Tên đăng nhập '%s' đang được một lượt đăng ký khác giữ chỗ. Vui lòng chọn tên khác."
                            .formatted(username));
        }

        PendingRegistration pending = pendingRepository.findByEmail(email)
                .orElseGet(PendingRegistration::new);
        pending.setUsername(username);
        pending.setEmail(email);
        pending.setPasswordHash(passwordEncoder.encode(request.password()));
        pending.setDisplayName(request.displayName());

        // Đăng ký lại ngay sau lần trước thì giữ nguyên mã đang nằm trong hòm
        // thư: đổi mã mà không gửi mã mới sẽ làm hỏng cái người ta vừa nhận, còn
        // gửi thêm một lá nữa là biến form này thành công cụ dội thư người khác.
        boolean sendNow = pending.getLastSentAt() == null
                || pending.getLastSentAt().plus(RESEND_COOLDOWN).isBefore(now);
        if (sendNow) {
            issueCode(pending, now);
        }

        pendingRepository.save(pending);
        return RegisterResponse.awaitingCode(email, (int) CODE_TTL.toMinutes());
    }

    /**
     * Bước hai: mã đúng thì tài khoản được tạo ngay tại đây.
     *
     * <p>{@code noRollbackFor} là chủ ý: đếm số lần nhập sai chỉ có tác dụng khi
     * nó được ghi lại: để giao dịch cuộn ngược theo ngoại lệ thì bộ đếm không
     * bao giờ tăng và mã sáu chữ số có thể dò thoải mái.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verify(String rawEmail, String rawCode) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        PendingRegistration pending = pendingRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException(
                        "Không tìm thấy yêu cầu đăng ký nào cho email này. Vui lòng đăng ký lại."));

        if (pending.isExpired()) {
            pendingRepository.delete(pending);
            throw new BadRequestException("Mã xác thực đã hết hạn. Vui lòng đăng ký lại.");
        }

        if (!pending.getCodeHash().equals(hash(rawCode.trim()))) {
            pending.setAttempts(pending.getAttempts() + 1);
            int remaining = MAX_ATTEMPTS - pending.getAttempts();
            if (remaining <= 0) {
                pendingRepository.delete(pending);
                throw new BadRequestException(
                        "Mã xác thực không đúng và bạn đã hết lượt thử. Vui lòng đăng ký lại.");
            }
            throw new BadRequestException(
                    "Mã xác thực không đúng. Bạn còn %d lần thử.".formatted(remaining));
        }

        // Hỏi lại lần nữa: trong mười phút chờ, tên đăng nhập hoặc email này có
        // thể đã bị một tài khoản khác lấy mất.
        requireIdentityAvailable(pending.getUsername(), email);

        User user = createUser(pending.getUsername(), email, pending.getPasswordHash(),
                pending.getDisplayName(), true);
        pendingRepository.delete(pending);
        return authService.issueSession(user);
    }

    /** Gửi lại mã mới cho một lượt đăng ký đang chờ. */
    @Transactional
    public void resendCode(String rawEmail) {
        requireMailConfigured();

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        PendingRegistration pending = pendingRepository.findByEmail(email)
                .filter(candidate -> !candidate.isExpired())
                .orElseThrow(() -> new BadRequestException(
                        "Không tìm thấy yêu cầu đăng ký nào còn hiệu lực. Vui lòng đăng ký lại."));

        Instant now = Instant.now();
        Instant nextAllowed = pending.getLastSentAt().plus(RESEND_COOLDOWN);
        if (nextAllowed.isAfter(now)) {
            throw new BadRequestException("Vui lòng đợi %d giây trước khi gửi lại mã."
                    .formatted(Duration.between(now, nextAllowed).toSeconds() + 1));
        }

        issueCode(pending, now);
        pendingRepository.save(pending);
    }

    /* ------------------------------------------------------------------ */
    /* Mã xác thực                                                         */
    /* ------------------------------------------------------------------ */

    /** Sinh mã mới, đặt lại hạn dùng và số lần thử, rồi gửi đi. */
    private void issueCode(PendingRegistration pending, Instant now) {
        String code = newCode();
        pending.setCodeHash(hash(code));
        pending.setExpiresAt(now.plus(CODE_TTL));
        pending.setAttempts(0);
        pending.setLastSentAt(now);

        String greeting = pending.getDisplayName() == null || pending.getDisplayName().isBlank()
                ? pending.getUsername() : pending.getDisplayName();
        mailService.sendRegistrationCode(pending.getEmail(), greeting, code, (int) CODE_TTL.toMinutes());
    }

    /** Sáu chữ số, giữ cả số 0 đứng đầu để mã nào cũng dài đúng bằng nhau. */
    private String newCode() {
        return "%06d".formatted(random.nextInt(CODE_BOUND));
    }

    private String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không có thuật toán SHA-256", ex);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Tài khoản                                                           */
    /* ------------------------------------------------------------------ */

    private User createUser(String username, String email, String password, String displayName) {
        return createUser(username, email, passwordEncoder.encode(password), displayName, false);
    }

    /**
     * @param passwordAlreadyHashed mật khẩu lấy từ bảng chờ thì đã băm sẵn từ
     *                              bước một, băm lần nữa là hỏng
     */
    private User createUser(String username, String email, String password, String displayName,
                            boolean passwordAlreadyHashed) {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordAlreadyHashed ? password : passwordEncoder.encode(password))
                .displayName(displayName)
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build());

        log.info("Tài khoản mới đăng ký: {}", user.getUsername());
        return user;
    }

    private void requireIdentityAvailable(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Tên đăng nhập '%s' đã được sử dụng.".formatted(username));
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email '%s' đã được đăng ký.".formatted(email));
        }
    }

    private void requireMailConfigured() {
        if (!mailService.isConfigured()) {
            throw new BadRequestException(
                    "Chức năng gửi email chưa được cấu hình trên máy chủ. Vui lòng liên hệ quản trị viên.");
        }
    }
}
