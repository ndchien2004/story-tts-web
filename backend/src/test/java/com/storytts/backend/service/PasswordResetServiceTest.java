package com.storytts.backend.service;

import com.storytts.backend.config.AppMailProperties;
import com.storytts.backend.domain.PasswordResetToken;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.PasswordResetTokenRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử luồng quên mật khẩu.
 *
 * <p>Điều được kiểm ở đây không phải là "gửi được email" — việc đó thuộc về
 * {@code MailService} và đã bị thay bằng mock — mà là những quyết định khiến
 * luồng này an toàn hay không: không tiết lộ email nào đã đăng ký, không lưu
 * token gốc xuống cơ sở dữ liệu, và một liên kết chỉ dùng được đúng một lần.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final String EMAIL = "doc.gia@example.com";
    private static final String RESET_URL = "http://localhost:5173/dat-lai-mat-khau";
    private static final int TTL_MINUTES = 30;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        AppMailProperties properties =
                new AppMailProperties("no-reply@storytts.local", "Truyen Nghe", RESET_URL, TTL_MINUTES);
        service = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, mailService, properties);

        user = User.builder()
                .id(1L)
                .username("docgia")
                .email(EMAIL)
                .passwordHash("hash-cu")
                .displayName("Độc giả")
                .role(Role.MEMBER)
                .enabled(true)
                .build();

        when(mailService.isConfigured()).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "bcrypt:" + call.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(call -> call.getArgument(0));
    }

    /* ------------------------------------------------------------------ */
    /* Yêu cầu gửi liên kết                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("Email lạ: im lặng bỏ qua, không phát token và cũng không báo lỗi")
    void emailKhongTonTaiThiKhongLoRa() {
        when(userRepository.findByEmail("nguoi.la@example.com")).thenReturn(Optional.empty());

        service.requestReset("nguoi.la@example.com");

        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordReset(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Tài khoản bị khóa cũng không nhận được liên kết")
    void taiKhoanBiKhoaThiKhongGui() {
        user.setEnabled(false);

        service.requestReset(EMAIL);

        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordReset(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Email viết hoa và thừa khoảng trắng vẫn tra đúng tài khoản")
    void emailDuocChuanHoaTruocKhiTra() {
        service.requestReset("  Doc.Gia@Example.COM  ");

        verify(userRepository).findByEmail(EMAIL);
        verify(mailService).sendPasswordReset(eq(user), anyString(), eq(TTL_MINUTES));
    }

    @Test
    @DisplayName("Liên kết cũ bị vô hiệu trước khi phát liên kết mới")
    void phatTokenMoiThiHuyTokenCu() {
        service.requestReset(EMAIL);

        verify(tokenRepository).invalidateAllFor(eq(user), any(Instant.class));
    }

    /**
     * Bài quan trọng nhất của lớp này: chuỗi đi trong email và chuỗi nằm trong
     * cơ sở dữ liệu phải là hai thứ khác nhau, cái sau là băm của cái trước.
     */
    @Test
    @DisplayName("Chỉ băm của token được lưu, token gốc chỉ nằm trong email")
    void chiLuuBamCuaToken() {
        service.requestReset(EMAIL);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordReset(eq(user), link.capture(), eq(TTL_MINUTES));

        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(link.getValue()).startsWith(RESET_URL + "?token=");
        assertThat(saved.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(rawToken)
                .isEqualTo(sha256Hex(rawToken));
    }

    @Test
    @DisplayName("Token hết hạn đúng theo cấu hình")
    void tokenHetHanTheoCauHinh() {
        service.requestReset(EMAIL);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());

        assertThat(saved.getValue().getExpiresAt())
                .isCloseTo(Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES),
                        within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Chưa cấu hình email thì báo rõ thay vì im lặng nuốt yêu cầu")
    void chuaCauHinhEmailThiBaoLoi() {
        when(mailService.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.requestReset(EMAIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa được cấu hình");
    }

    /* ------------------------------------------------------------------ */
    /* Đổi mật khẩu theo token                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("Token hợp lệ: mật khẩu được băm lại và token bị đánh dấu đã dùng")
    void doiMatKhauThanhCong() {
        PasswordResetToken record = usableToken();
        when(tokenRepository.findByTokenHash(sha256Hex("token-that"))).thenReturn(Optional.of(record));

        service.resetPassword("token-that", "MatKhauMoi@1");

        assertThat(user.getPasswordHash()).isEqualTo("bcrypt:MatKhauMoi@1");
        assertThat(record.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Token không có trong bảng thì bị từ chối")
    void tokenLaBiTuChoi() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("token-bia", "MatKhauMoi@1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không hợp lệ hoặc đã hết hạn");

        assertThat(user.getPasswordHash()).isEqualTo("hash-cu");
    }

    @Test
    @DisplayName("Một liên kết chỉ dùng được một lần")
    void tokenDaDungThiKhongDungLai() {
        PasswordResetToken record = usableToken();
        record.setUsedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.resetPassword("token-that", "MatKhauMoi@1"))
                .isInstanceOf(BadRequestException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-cu");
    }

    @Test
    @DisplayName("Token quá hạn thì không đổi được mật khẩu")
    void tokenHetHanThiBiTuChoi() {
        PasswordResetToken record = usableToken();
        record.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.resetPassword("token-that", "MatKhauMoi@1"))
                .isInstanceOf(BadRequestException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-cu");
    }

    @Test
    @DisplayName("Tài khoản bị khóa thì token còn hạn cũng không mở được")
    void taiKhoanBiKhoaThiKhongDoiDuoc() {
        user.setEnabled(false);
        PasswordResetToken record = usableToken();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.resetPassword("token-that", "MatKhauMoi@1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bị khóa");

        assertThat(user.getPasswordHash()).isEqualTo("hash-cu");
    }

    /* ------------------------------------------------------------------ */

    private PasswordResetToken usableToken() {
        return PasswordResetToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(sha256Hex("token-that"))
                .expiresAt(Instant.now().plusSeconds(600))
                .createdAt(Instant.now())
                .build();
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
