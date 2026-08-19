package com.storytts.backend.service;

import com.storytts.backend.config.GoogleProperties;
import com.storytts.backend.config.LoginThrottleProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.GoogleLoginRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử nhánh đăng nhập bằng Google của {@link AuthService}.
 *
 * <p>Việc kiểm chữ ký ID token thuộc về {@code GoogleIdTokenVerifier} và đã bị
 * thay bằng mock, nên bài test <b>không gọi Google</b>. Cái được kiểm ở đây là
 * quyết định của service: ghép vào tài khoản nào, khi nào mới tạo tài khoản mới,
 * và tên đăng nhập sinh ra có hợp lệ không.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceGoogleTest {

    private static final String SUBJECT = "109876543210987654321";
    private static final String EMAIL = "doc.gia@gmail.com";
    private static final String PICTURE = "https://lh3.googleusercontent.com/a/anh-google";
    private static final String CLIENT_ID = "demo.apps.googleusercontent.com";

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
                googleIdTokenVerifier, new GoogleProperties(CLIENT_ID, "https://certs"),
                new LoginThrottleProperties(10, java.time.Duration.ofMinutes(15)),
                passwordResetService, readerNarrationCleanup);

        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleAccount(SUBJECT, EMAIL, "Độc Giả", PICTURE));
        when(googleIdTokenVerifier.isConfigured()).thenReturn(true);
        when(userRepository.findByGoogleId(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt:ngau-nhien");
        when(jwtService.generateToken(any())).thenReturn("jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("Lần đầu đăng nhập Google thì tài khoản được tạo ngay tại đây")
    void lanDauThiTaoTaiKhoan() {
        AuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        User created = savedUser();
        assertThat(created.getUsername()).isEqualTo("doc.gia");
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getGoogleId()).isEqualTo(SUBJECT);
        assertThat(created.getDisplayName()).isEqualTo("Độc Giả");
        assertThat(created.getAvatarUrl()).isEqualTo(PICTURE);
        assertThat(created.getRole()).isEqualTo(Role.MEMBER);
        assertThat(created.isEnabled()).isTrue();
        assertThat(response.token()).isEqualTo("jwt");
    }

    /**
     * Mật khẩu không dùng tới, nhưng cột vẫn NOT NULL — quan trọng là chỗ trống
     * đó không được lấp bằng một chuỗi cố định mà ai cũng đoán ra.
     */
    @Test
    @DisplayName("Tài khoản Google vẫn có mật khẩu băm, và là chuỗi ngẫu nhiên")
    void matKhauChoTaiKhoanGoogleLaNgauNhien() {
        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(raw.capture());

        assertThat(raw.getValue()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(savedUser().getPasswordHash()).isEqualTo("bcrypt:ngau-nhien");
    }

    @Test
    @DisplayName("Tên đăng nhập bị trùng thì nối thêm số cho tới khi còn trống")
    void tenDangNhapTrungThiNoiThemSo() {
        when(userRepository.existsByUsername("doc.gia")).thenReturn(true);
        when(userRepository.existsByUsername("doc.gia1")).thenReturn(true);

        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        assertThat(savedUser().getUsername()).isEqualTo("doc.gia2");
    }

    @Test
    @DisplayName("Email có ký tự lạ vẫn cho ra tên đăng nhập hợp lệ")
    void tenDangNhapChiGiuKyTuHopLe() {
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleAccount(SUBJECT, "nguyễn+test@gmail.com", null, null));

        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        assertThat(savedUser().getUsername()).matches("^[a-zA-Z0-9._-]{3,50}$");
    }

    /** Email bị ràng buộc duy nhất, nên tạo thêm tài khoản ở đây là chắc chắn lỗi. */
    @Test
    @DisplayName("Đã đăng ký bằng mật khẩu: bấm nút Google thì ghép vào tài khoản cũ")
    void ghepVaoTaiKhoanCungEmail() {
        User existing = existingUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        assertThat(existing.getGoogleId()).isEqualTo(SUBJECT);
        assertThat(savedUser()).isSameAs(existing);
    }

    @Test
    @DisplayName("Tìm theo googleId trước, nên đổi email bên Google vẫn về đúng tài khoản")
    void timTheoGoogleIdTruoc() {
        User existing = existingUser();
        existing.setGoogleId(SUBJECT);
        existing.setEmail("email.cu@gmail.com");
        when(userRepository.findByGoogleId(SUBJECT)).thenReturn(Optional.of(existing));

        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        verify(userRepository, never()).findByEmail(anyString());
        assertThat(savedUser()).isSameAs(existing);
    }

    @Test
    @DisplayName("Ảnh đại diện người dùng tự tải lên không bị ảnh Google ghi đè")
    void khongGhiDeAnhDaiDienCu() {
        User existing = existingUser();
        existing.setAvatarUrl("https://res.cloudinary.com/anh-tu-tai-len.png");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        authService.loginWithGoogle(new GoogleLoginRequest("id-token"));

        assertThat(existing.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/anh-tu-tai-len.png");
    }

    @Test
    @DisplayName("Tài khoản bị khóa thì đăng nhập Google cũng không vào được")
    void taiKhoanBiKhoaThiBiChan() {
        User existing = existingUser();
        existing.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        // Ngoại lệ riêng chứ không phải BadRequestException như trước: tài khoản
        // bị khóa giờ trả về 401 kèm mã ACCOUNT_LOCKED, cùng một câu trả lời mà
        // JwtAuthenticationFilter đưa ra — nhờ đó trình duyệt chỉ cần biết một
        // quy tắc để đăng xuất, dù nó phát hiện ra ở đường nào.
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token")))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("bị khóa");
    }

    @Test
    @DisplayName("Máy chủ báo đúng những cách đăng nhập đang bật")
    void bangCachDangNhapDangBat() {
        when(passwordResetService.isAvailable()).thenReturn(false);

        var providers = authService.providers();

        assertThat(providers.googleEnabled()).isTrue();
        assertThat(providers.googleClientId()).isEqualTo(CLIENT_ID);
        assertThat(providers.passwordResetEnabled()).isFalse();
    }

    @Test
    @DisplayName("Chưa cấu hình Google thì không trả client id ra ngoài")
    void chuaCauHinhThiKhongTraClientId() {
        when(googleIdTokenVerifier.isConfigured()).thenReturn(false);

        var providers = authService.providers();

        assertThat(providers.googleEnabled()).isFalse();
        assertThat(providers.googleClientId()).isNull();
    }

    /* ------------------------------------------------------------------ */

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private User existingUser() {
        return User.builder()
                .id(7L)
                .username("docgia")
                .email(EMAIL)
                .passwordHash("bcrypt:mat-khau-that")
                .displayName("Độc giả")
                .role(Role.MEMBER)
                .enabled(true)
                .build();
    }
}
