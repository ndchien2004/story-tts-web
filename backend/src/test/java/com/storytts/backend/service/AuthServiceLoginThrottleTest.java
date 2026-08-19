package com.storytts.backend.service;

import com.storytts.backend.config.GoogleProperties;
import com.storytts.backend.config.LoginThrottleProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.exception.LoginThrottledException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.GoogleIdTokenVerifier;
import com.storytts.backend.security.JwtService;
import com.storytts.backend.service.tts.ReaderNarrationCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hàng rào thứ hai của cửa đăng nhập: đếm theo tài khoản.
 *
 * <h3>Vì sao cần nó khi đã có giới hạn theo IP</h3>
 * {@code RateLimitFilter} đếm theo địa chỉ mạng, thứ duy nhất biết được khi chưa
 * ai đăng nhập. Nhưng kẻ có sẵn một dải địa chỉ chỉ cần đổi nguồn sau mỗi mười
 * lần thử, và hàng rào ấy không bao giờ đóng lại. Bộ đếm ở đây gắn với chính cái
 * tài khoản đang bị nhắm tới — thứ kẻ tấn công không đổi được.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceLoginThrottleTest {

    private static final int MAX_FAILURES = 3;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private ReaderNarrationCleanup readerNarrationCleanup;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                currentUserService, googleIdTokenVerifier,
                new GoogleProperties("demo.apps.googleusercontent.com", "https://certs"),
                new LoginThrottleProperties(MAX_FAILURES, Duration.ofMinutes(15)),
                passwordResetService, readerNarrationCleanup);

        user = User.builder()
                .id(1L).username("nguoidoc").email("doc@test.local")
                .passwordHash("bcrypt").role(Role.MEMBER).enabled(true)
                .build();

        when(userRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.of(user));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt");
    }

    @Test
    @DisplayName("gõ sai đủ số lần thì tài khoản nghỉ, và lần sau nhận 429 chứ không phải 401")
    void goSaiDuSoLanThiNghi() {
        wrongPassword();

        for (int i = 0; i < MAX_FAILURES; i++) {
            assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(this::login)
                .isInstanceOf(LoginThrottledException.class)
                .hasMessageContaining("Quên mật khẩu");
    }

    /**
     * BCrypt cố ý chậm. Một cửa đăng nhập vẫn băm mật khẩu cho người đang bị
     * chặn là một cửa vẫn cho kẻ tấn công tiêu CPU của máy chủ — nên phép so
     * phải nằm <i>sau</i> câu hỏi "còn đang nghỉ không".
     */
    @Test
    @DisplayName("đang nghỉ thì không băm mật khẩu nữa")
    void dangNghiThiKhongBamMatKhau() {
        wrongPassword();
        for (int i = 0; i < MAX_FAILURES; i++) {
            assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);
        }
        org.mockito.Mockito.clearInvocations(passwordEncoder);

        assertThatThrownBy(this::login).isInstanceOf(LoginThrottledException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("một lần đúng xóa sạch bộ đếm — người hay quên mật khẩu không tích dần tới mức khóa")
    void motLanDungXoaBoDem() {
        wrongPassword();
        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);

        rightPassword();
        authService.login(new LoginRequest("nguoidoc", "dung"));

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLoginLockedUntil()).isNull();
    }

    @Test
    @DisplayName("đếm liên tiếp, không đếm tổng: sai, đúng, rồi sai lại vẫn còn xa mức khóa")
    void demLienTiep() {
        wrongPassword();
        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);

        rightPassword();
        authService.login(new LoginRequest("nguoidoc", "dung"));

        wrongPassword();
        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLoginThrottled()).isFalse();
    }

    @Test
    @DisplayName("tên đăng nhập không tồn tại vẫn chỉ nhận 401 — không lộ tài khoản nào có thật")
    void tenKhongTonTai() {
        when(userRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(this::login).isInstanceOf(BadCredentialsException.class);
    }

    private void wrongPassword() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    }

    private void rightPassword() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    }

    private void login() {
        authService.login(new LoginRequest("nguoidoc", "sai"));
    }
}
