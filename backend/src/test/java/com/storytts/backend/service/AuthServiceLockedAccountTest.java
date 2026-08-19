package com.storytts.backend.service;

import com.storytts.backend.config.GoogleProperties;
import com.storytts.backend.config.LoginThrottleProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.GoogleLoginRequest;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.exception.AccountLockedException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.GoogleIdTokenVerifier;
import com.storytts.backend.security.GoogleIdTokenVerifier.GoogleAccount;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cửa cuối cùng: một tài khoản bị khóa không được đăng nhập lại.
 *
 * <p>Chặn ở đây là điều kiện để việc đá người dùng ra có nghĩa. Không có nó,
 * mọi thứ còn lại chỉ là một bất tiện kéo dài vài giây: bị đăng xuất xong, gõ
 * lại đúng mật khẩu, và có ngay một token mới hoàn toàn hợp lệ.
 *
 * <p>Cả hai đường vào đều phải nói cùng một câu. Đường Google dễ bị bỏ sót vì
 * mật khẩu không tham gia gì vào đó, nhưng nó cấp ra đúng loại token ấy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceLockedAccountTest {

    private static final String PASSWORD = "matkhau-dung";
    private static final String SUBJECT = "109876543210987654321";
    private static final String EMAIL = "doc@test.local";

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

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, currentUserService,
                googleIdTokenVerifier, new GoogleProperties("demo.apps.googleusercontent.com",
                "https://certs"),
                new LoginThrottleProperties(10, java.time.Duration.ofMinutes(15)),
                passwordResetService, readerNarrationCleanup);

        // Mật khẩu đúng, có chủ ý: thứ đang được kiểm là trạng thái tài khoản,
        // không phải thông tin đăng nhập. Để mật khẩu sai thì bài test vẫn xanh
        // ngay cả khi phần chặn tài khoản bị khóa biến mất hoàn toàn.
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.findByUsernameOrEmail(anyString()))
                .thenReturn(Optional.of(lockedUser()));

        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleAccount(SUBJECT, EMAIL, "Độc Giả", null));
        when(userRepository.findByGoogleId(anyString())).thenReturn(Optional.of(lockedUser()));

        // Đường Google ghi lại tài khoản để gắn định danh trước khi xét trạng
        // thái, nên save phải trả về chính thứ nó nhận. Mock mặc định trả null,
        // và khi ấy bài test sẽ chết vì NullPointerException — tức là xanh hay
        // đỏ vì một lý do chẳng liên quan gì tới điều đang được kiểm.
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("đăng nhập bằng mật khẩu đúng vào tài khoản bị khóa: từ chối, không cấp token")
    void passwordLoginIsRefused() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nguoidoc", PASSWORD)))
                .isInstanceOf(AccountLockedException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("đăng nhập bằng Google vào tài khoản bị khóa: từ chối, không cấp token")
    void googleLoginIsRefused() {
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token")))
                .isInstanceOf(AccountLockedException.class);

        verify(jwtService, never()).generateToken(any());
    }

    private static User lockedUser() {
        return User.builder()
                .id(42L)
                .username("nguoidoc")
                .email(EMAIL)
                .passwordHash("bcrypt:bam")
                .googleId(SUBJECT)
                .role(Role.MEMBER)
                .enabled(false)
                .build();
    }
}
