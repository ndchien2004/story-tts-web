package com.storytts.backend.service;

import com.storytts.backend.domain.PendingRegistration;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.RegisterResponse;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.PendingRegistrationRepository;
import com.storytts.backend.repository.UserRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử đăng ký có xác thực email.
 *
 * <p>Lời hứa quan trọng nhất của luồng này chỉ có một câu: <b>chưa nhập đúng mã
 * thì chưa có hàng nào trong bảng {@code users}</b>. Phần lớn các bài dưới đây
 * là những cách khác nhau để hỏi lại đúng câu đó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    private static final String USERNAME = "docgia";
    private static final String EMAIL = "doc.gia@example.com";
    private static final String PASSWORD = "MatKhau@1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PendingRegistrationRepository pendingRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;
    @Mock
    private AuthService authService;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(
                userRepository, pendingRepository, passwordEncoder, mailService, authService);

        when(mailService.isConfigured()).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(pendingRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(pendingRepository.usernameHeldByAnother(anyString(), anyString(), any())).thenReturn(false);
        when(pendingRepository.save(any(PendingRegistration.class))).thenAnswer(c -> c.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(c -> "bcrypt:" + c.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(c -> {
            User saved = c.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        when(authService.issueSession(any(User.class))).thenAnswer(
                c -> AuthResponse.of("jwt", 1000L, UserDto.from(c.getArgument(0))));
    }

    /* ------------------------------------------------------------------ */
    /* Bước một: gửi mã                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("Bấm đăng ký chỉ gửi mã, chưa ghi tài khoản nào vào cơ sở dữ liệu")
    void buocMotKhongTaoTaiKhoan() {
        RegisterResponse response = service.start(request());

        verify(userRepository, never()).save(any());
        verify(mailService).sendRegistrationCode(eq(EMAIL), anyString(), anyString(), anyInt());
        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.session()).isNull();
        assertThat(response.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Mã đi trong email, cơ sở dữ liệu chỉ giữ băm của nó")
    void chiLuuBamCuaMa() {
        service.start(request());

        assertThat(sentCode()).matches("^\\d{6}$");
        assertThat(savedPending().getCodeHash()).hasSize(64).isEqualTo(sha256Hex(sentCode()));
    }

    @Test
    @DisplayName("Mật khẩu nằm trong bảng chờ cũng đã băm, không lưu bản thô")
    void matKhauTrongBangChoDaBam() {
        service.start(request());

        assertThat(savedPending().getPasswordHash()).isEqualTo("bcrypt:" + PASSWORD);
    }

    @Test
    @DisplayName("Tên đăng nhập đã có người dùng thì chặn ngay, không gửi mã")
    void tenDangNhapTrungThiChanTuDau() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> service.start(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã được sử dụng");

        verify(mailService, never()).sendRegistrationCode(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Tên đăng nhập đang bị một lượt đăng ký khác giữ chỗ cũng bị chặn")
    void tenDangNhapBiLuotKhacGiuCho() {
        when(pendingRepository.usernameHeldByAnother(eq(USERNAME), eq(EMAIL), any())).thenReturn(true);

        assertThatThrownBy(() -> service.start(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("giữ chỗ");
    }

    /**
     * Không có SMTP thì không có đường nào gửi mã đi, nên chức năng đăng ký lùi
     * về cách cũ thay vì ngừng hoạt động.
     */
    @Test
    @DisplayName("Máy chủ chưa cấu hình email: tạo tài khoản ngay và trả luôn phiên đăng nhập")
    void chuaCauHinhEmailThiTaoNgay() {
        when(mailService.isConfigured()).thenReturn(false);

        RegisterResponse response = service.start(request());

        assertThat(response.verificationRequired()).isFalse();
        assertThat(response.session()).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(pendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đăng ký lại ngay sau lần trước thì không gửi thêm thư, mã cũ vẫn dùng được")
    void dangKyLaiTrongThoiGianChoThiKhongGuiThem() {
        PendingRegistration existing = pendingWithCode("111111");
        existing.setLastSentAt(Instant.now().minusSeconds(5));
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        service.start(request());

        verify(mailService, never()).sendRegistrationCode(anyString(), anyString(), anyString(), anyInt());
        assertThat(existing.getCodeHash()).isEqualTo(sha256Hex("111111"));
    }

    /* ------------------------------------------------------------------ */
    /* Bước hai: nhập mã                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("Mã đúng: tài khoản được tạo, bản ghi chờ bị xóa")
    void maDungThiTaoTaiKhoan() {
        PendingRegistration pending = pendingWithCode("123456");
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        service.verify(EMAIL, "123456");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);
        assertThat(saved.getValue().isEnabled()).isTrue();
        verify(pendingRepository).delete(pending);
    }

    /** Băm lại một chuỗi đã băm thì mật khẩu người dùng đặt không đăng nhập được nữa. */
    @Test
    @DisplayName("Mật khẩu lấy nguyên từ bảng chờ, không băm chồng thêm lần nữa")
    void khongBamChongMatKhau() {
        PendingRegistration pending = pendingWithCode("123456");
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        service.verify(EMAIL, "123456");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(pending.getPasswordHash());
    }

    @Test
    @DisplayName("Mã sai: đếm thêm một lần thử và tuyệt đối không tạo tài khoản")
    void maSaiThiKhongTaoTaiKhoan() {
        PendingRegistration pending = pendingWithCode("123456");
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("còn 4 lần thử");

        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hết lượt thử thì lượt đăng ký bị hủy hẳn")
    void hetLuotThuThiHuyLuotDangKy() {
        PendingRegistration pending = pendingWithCode("123456");
        pending.setAttempts(4);
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hết lượt thử");

        verify(pendingRepository).delete(pending);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mã hết hạn thì bị từ chối và bản ghi chờ bị dọn đi")
    void maHetHanThiBiTuChoi() {
        PendingRegistration pending = pendingWithCode("123456");
        pending.setExpiresAt(Instant.now().minusSeconds(1));
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.verify(EMAIL, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hết hạn");

        verify(pendingRepository).delete(pending);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không có lượt đăng ký nào cho email này thì không xác thực được")
    void khongCoLuotDangKyNao() {
        assertThatThrownBy(() -> service.verify(EMAIL, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đăng ký lại");
    }

    /** Mười phút chờ là đủ để một tài khoản khác kịp lấy mất tên đăng nhập đó. */
    @Test
    @DisplayName("Tên đăng nhập bị lấy mất trong lúc chờ thì báo lỗi thay vì ghi đè")
    void tenDangNhapBiLayMatTrongLucCho() {
        PendingRegistration pending = pendingWithCode("123456");
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> service.verify(EMAIL, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã được sử dụng");

        verify(userRepository, never()).save(any());
    }

    /* ------------------------------------------------------------------ */
    /* Gửi lại mã                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("Gửi lại mã: mã mới thay mã cũ và số lần thử được đặt lại")
    void guiLaiMaThiDatLaiSoLanThu() {
        PendingRegistration pending = pendingWithCode("123456");
        pending.setAttempts(3);
        pending.setLastSentAt(Instant.now().minusSeconds(120));
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        service.resendCode(EMAIL);

        assertThat(pending.getAttempts()).isZero();
        assertThat(pending.getCodeHash()).isEqualTo(sha256Hex(sentCode()));
        assertThat(sentCode()).isNotEqualTo("123456");
    }

    @Test
    @DisplayName("Bấm gửi lại quá sớm thì bị chặn, không thành công cụ dội thư")
    void guiLaiQuaSomThiBiChan() {
        PendingRegistration pending = pendingWithCode("123456");
        pending.setLastSentAt(Instant.now().minusSeconds(5));
        when(pendingRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.resendCode(EMAIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đợi");

        verify(mailService, never()).sendRegistrationCode(anyString(), anyString(), anyString(), anyInt());
    }

    /* ------------------------------------------------------------------ */

    private RegisterRequest request() {
        return new RegisterRequest(USERNAME, EMAIL, PASSWORD, "Độc giả");
    }

    private PendingRegistration pendingWithCode(String code) {
        return PendingRegistration.builder()
                .id(1L)
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash("bcrypt:" + PASSWORD)
                .displayName("Độc giả")
                .codeHash(sha256Hex(code))
                .expiresAt(Instant.now().plusSeconds(600))
                .attempts(0)
                .lastSentAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private PendingRegistration savedPending() {
        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRepository).save(captor.capture());
        return captor.getValue();
    }

    private String sentCode() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationCode(anyString(), anyString(), captor.capture(), anyInt());
        return captor.getValue();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
