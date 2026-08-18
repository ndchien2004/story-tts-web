package com.storytts.backend.service;

import com.storytts.backend.config.GoogleProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.AuthProvidersDto;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.GoogleLoginRequest;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.exception.AccountLockedException;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.GoogleIdTokenVerifier;
import com.storytts.backend.security.GoogleIdTokenVerifier.GoogleAccount;
import com.storytts.backend.security.JwtService;
import com.storytts.backend.service.tts.ReaderNarrationCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/** Đăng ký / đăng nhập / lấy thông tin tài khoản hiện tại. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Tên đăng nhập suy ra từ email phải hợp lệ với luật của form đăng ký. */
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GoogleProperties googleProperties;
    private final PasswordResetService passwordResetService;
    private final ReaderNarrationCleanup readerNarrationCleanup;

    private final SecureRandom random = new SecureRandom();

    /**
     * Cấp JWT cho một tài khoản.
     *
     * <p>Công khai vì mọi đường vào hệ thống đều kết thúc ở đây: đăng nhập bằng
     * mật khẩu, bằng Google, và bước nhập mã xác thực của
     * {@link RegistrationService}.
     */
    public AuthResponse issueSession(User user) {
        // Bản audio người này tự dựng ở phiên trước không được sống sang phiên
        // mới. Dọn ở đây chứ không chỉ ở lúc đăng xuất, vì đóng thẳng trình
        // duyệt thì không có ai báo cho máy chủ cả — mở phiên là mốc duy nhất
        // chắc chắn xảy ra.
        readerNarrationCleanup.discardFor(user.getId());

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.getExpirationMs(), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Sai thông tin đăng nhập"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Sai thông tin đăng nhập");
        }
        if (!user.isEnabled()) {
            throw new AccountLockedException();
        }
        return issueSession(user);
    }

    /**
     * Đăng nhập bằng Google — cũng là đường đăng ký, vì lần đầu tiên thì tài
     * khoản được tạo ngay tại đây.
     *
     * <p>Ghép với tài khoản sẵn có theo {@code sub} trước, sau đó mới tới email.
     * Nhờ bước thứ hai, người đã đăng ký bằng mật khẩu rồi bấm nút Google vẫn về
     * đúng tài khoản cũ thay vì có thêm một tài khoản trùng email — mà email thì
     * đằng nào cũng bị ràng buộc duy nhất.
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleAccount account = googleIdTokenVerifier.verify(request.credential());
        String email = account.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByGoogleId(account.subject())
                .or(() -> userRepository.findByEmail(email))
                .map(existing -> linkGoogleAccount(existing, account))
                .orElseGet(() -> createFromGoogleAccount(account, email));

        if (!user.isEnabled()) {
            throw new AccountLockedException();
        }
        return issueSession(user);
    }

    /** Các cách đăng nhập đang bật, để frontend không hiện nút chỉ báo lỗi khi bấm. */
    @Transactional(readOnly = true)
    public AuthProvidersDto providers() {
        boolean googleEnabled = googleIdTokenVerifier.isConfigured();
        return new AuthProvidersDto(
                googleEnabled,
                googleEnabled ? googleProperties.clientId() : null,
                passwordResetService.isAvailable());
    }

    @Transactional(readOnly = true)
    public UserDto currentUser() {
        return UserDto.from(currentUserService.requireCurrentUser());
    }

    /* ------------------------------------------------------------------ */
    /* Tài khoản Google                                                    */
    /* ------------------------------------------------------------------ */

    private User linkGoogleAccount(User user, GoogleAccount account) {
        if (user.getGoogleId() == null) {
            user.setGoogleId(account.subject());
            log.info("Đã liên kết tài khoản {} với Google", user.getUsername());
        }
        // Chỉ lấy ảnh của Google khi người dùng chưa có ảnh nào: ghi đè mỗi lần
        // đăng nhập sẽ xóa mất ảnh họ tự tải lên.
        if (user.getAvatarUrl() == null && account.pictureUrl() != null) {
            user.setAvatarUrl(account.pictureUrl());
        }
        return userRepository.save(user);
    }

    private User createFromGoogleAccount(GoogleAccount account, String email) {
        String username = uniqueUsernameFrom(email);
        User user = User.builder()
                .username(username)
                .email(email)
                // Tài khoản Google không có mật khẩu riêng. Cột này vẫn NOT NULL
                // nên đặt một chuỗi ngẫu nhiên không ai đoán được; muốn có mật
                // khẩu thật thì đi đường "quên mật khẩu".
                .passwordHash(passwordEncoder.encode(randomSecret()))
                .googleId(account.subject())
                .displayName(account.name() != null && !account.name().isBlank()
                        ? account.name() : username)
                .avatarUrl(account.pictureUrl())
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("Tài khoản mới đăng ký bằng Google: {}", user.getUsername());
        return user;
    }

    /**
     * Tên đăng nhập lấy từ phần trước dấu @ của email, bỏ các ký tự không hợp lệ.
     *
     * <p>Trùng thì nối thêm số cho tới khi còn trống — hai địa chỉ khác nhau ở
     * hai nhà cung cấp vẫn có thể cho ra cùng một phần đầu.
     */
    private String uniqueUsernameFrom(String email) {
        String base = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() < MIN_USERNAME_LENGTH) {
            base = "user" + base;
        }
        if (base.length() > MAX_USERNAME_LENGTH - 4) {
            base = base.substring(0, MAX_USERNAME_LENGTH - 4);
        }

        String candidate = base;
        for (int suffix = 1; userRepository.existsByUsername(candidate); suffix++) {
            candidate = base + suffix;
        }
        return candidate;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
