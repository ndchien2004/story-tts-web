package com.storytts.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.AccountLockedException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lớp kiểm tra trạng thái tài khoản của toàn hệ thống.
 *
 * <p>Mọi bất biến của việc khóa tài khoản đều đứng hoặc đổ ở đây, vì đây là chỗ
 * duy nhất mà một request mang token đi qua trước khi tới bất kỳ controller nào.
 * Kiểm ở tầng service sẽ chỉ kiểm được những đường mà bài test nhớ liệt kê —
 * còn cái hỏng trong thực tế lại là một đường không ai nghĩ tới.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final Long USER_ID = 42L;
    private static final String TOKEN = "jwt-hop-le";

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // JavaTimeModule phải đăng ký tay ở đây. Khi chạy thật, bean ObjectMapper
        // do Spring Boot dựng đã có sẵn nó; bài test này tự dựng lấy một cái, mà
        // ApiErrorResponse có một trường Instant.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, new ApiErrorWriter(objectMapper));

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/me");
        response = new MockHttpServletResponse();

        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        // Bối cảnh bảo mật là biến toàn cục theo luồng: bỏ dọn thì bài test sau
        // thừa hưởng người dùng của bài test trước.
        SecurityContextHolder.clearContext();
    }

    // ==================== Tài khoản bị khóa ====================

    @Test
    @DisplayName("token của tài khoản bị khóa: dừng request, trả 401 ACCOUNT_LOCKED")
    void lockedAccountIsRefused() throws Exception {
        bearerTokenFor(lockedUser());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AccountLockedException.CODE);

        // Không đi tiếp: không controller nào được chạy.
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * Đây là bài test cho chính cái lỗi đã được báo.
     *
     * <p>Đường đọc chương để {@code permitAll} ở tầng URL, vì Khách vẫn đọc được
     * chương công khai. Trước đây filter gặp tài khoản bị khóa thì chỉ *không*
     * đặt authentication rồi cho request chạy tiếp — nên người bị khóa lặng lẽ
     * tụt xuống thành Khách và đọc truyện tiếp như không có gì xảy ra, trong khi
     * trình duyệt vẫn vẽ giao diện đã đăng nhập vì chẳng có tín hiệu nào báo cho
     * nó cả.
     */
    @Test
    @DisplayName("đường công khai cũng bị chặn: khóa không được phép biến thành tụt xuống Khách")
    void lockedAccountIsRefusedEvenOnPublicPaths() throws Exception {
        request.setRequestURI("/api/chapters/5");
        bearerTokenFor(lockedUser());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AccountLockedException.CODE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    /**
     * Thẻ {@code <audio>} của trình duyệt không đặt được header, nên đường phát
     * nhận token qua query. Nó phải bị chặn y hệt — nếu không thì bản audio của
     * chương trả phí vẫn chảy về máy người đã bị khóa.
     */
    @Test
    @DisplayName("token truyền qua query của đường phát audio cũng bị chặn")
    void lockedAccountIsRefusedOnTheAudioQueryParam() throws Exception {
        request.setRequestURI("/api/chapters/5/audio/9");
        request.setParameter("access_token", TOKEN);
        when(userDetailsService.loadUserById(USER_ID))
                .thenReturn(new AppUserPrincipal(lockedUser()));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AccountLockedException.CODE);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== Những đường không được phép hỏng theo ====================

    @Test
    @DisplayName("tài khoản bình thường: được nạp vào bối cảnh và request chạy tiếp")
    void activeAccountPassesThrough() throws Exception {
        bearerTokenFor(activeUser());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((AppUserPrincipal) authentication.getPrincipal()).getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("không có token: là Khách, không phải lỗi")
    void anonymousRequestPassesThrough() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * Token ký đúng nhưng người dùng đã bị xóa hẳn.
     *
     * <p>Không phải "bị khóa", nên không trả ACCOUNT_LOCKED: nói với trình duyệt
     * rằng tài khoản bị khóa trong khi nó không tồn tại là hướng người dùng đi
     * liên hệ quản trị viên về một thứ không có thật. Rơi về Khách như một token
     * không đọc được.
     */
    @Test
    @DisplayName("token trỏ tới người dùng không còn tồn tại: về Khách, không phải ACCOUNT_LOCKED")
    void unknownUserFallsBackToGuest() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(userDetailsService.loadUserById(USER_ID))
                .thenThrow(new UsernameNotFoundException("không có"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain(AccountLockedException.CODE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("token không đọc được: về Khách")
    void invalidTokenFallsBackToGuest() throws Exception {
        request.addHeader("Authorization", "Bearer rac-ruoi");
        when(jwtService.extractUserId("rac-ruoi")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==================== Trợ giúp ====================

    private void bearerTokenFor(User user) {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(new AppUserPrincipal(user));
    }

    private static User lockedUser() {
        return user(false);
    }

    private static User activeUser() {
        return user(true);
    }

    private static User user(boolean enabled) {
        return User.builder()
                .id(USER_ID)
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .enabled(enabled)
                .build();
    }
}
