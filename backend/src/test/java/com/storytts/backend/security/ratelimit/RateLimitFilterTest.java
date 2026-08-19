package com.storytts.backend.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.storytts.backend.security.ApiErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Hàng rào tần suất.
 *
 * <p>Bài đáng đọc nhất là {@link #cuaDangNhapCoGaoRieng()}: nếu mọi nhóm dùng
 * chung một gáo thì mức phải lấy theo đường bận nhất — đường phát audio, thứ một
 * trình phát gọi hàng chục lần cho một chương — và cửa đăng nhập sẽ được kèm một
 * hạn mức rộng gấp trăm lần chỗ nó cần.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final int LOGIN_LIMIT = 3;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(
                new RateLimitRules(LOGIN_LIMIT, 5, 20, 10, 60, 600),
                new ApiErrorWriter(objectMapper),
                true);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("trong hạn mức thì request đi tiếp bình thường")
    void trongHanMucThiDiTiep() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            filter.doFilterInternal(login("1.2.3.4"), new MockHttpServletResponse(), filterChain);
        }
        verify(filterChain, times(LOGIN_LIMIT)).doFilter(any(), any());
    }

    @Test
    @DisplayName("quá hạn mức → 429 kèm Retry-After, và chuỗi filter dừng tại đây")
    void quaHanMucThiChan() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            filter.doFilterInternal(login("1.2.3.4"), new MockHttpServletResponse(), filterChain);
        }

        filter.doFilterInternal(login("1.2.3.4"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();

        // Dừng hẳn: cả điểm của hàng rào là không có gì đắt tiền chạy sau nó.
        verify(filterChain, times(LOGIN_LIMIT)).doFilter(any(), any());
    }

    @Test
    @DisplayName("mỗi địa chỉ một gáo — người này bị chặn không kéo theo người kia")
    void moiDiaChiMotGao() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT + 1; i++) {
            filter.doFilterInternal(login("1.2.3.4"), new MockHttpServletResponse(), filterChain);
        }

        filter.doFilterInternal(login("5.6.7.8"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Nếu mọi nhóm dùng chung một gáo, mức phải lấy theo đường bận nhất và cửa
     * đăng nhập sẽ rộng tới mức không còn là hàng rào.
     */
    @Test
    @DisplayName("cửa đăng nhập có gáo riêng, không dùng chung với đường đọc truyện")
    void cuaDangNhapCoGaoRieng() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT + 1; i++) {
            filter.doFilterInternal(login("1.2.3.4"), new MockHttpServletResponse(), filterChain);
        }

        filter.doFilterInternal(get("/api/chapters/5", "1.2.3.4"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Trình duyệt coi một lời từ chối ở bước preflight là "máy chủ này không cho
     * trang của bạn gọi tới", nên cả giao diện ngừng hoạt động chứ không riêng
     * lời gọi bị chặn.
     */
    @Test
    @DisplayName("preflight của CORS không bao giờ bị chặn")
    void preflightKhongBiChan() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest preflight = login("1.2.3.4");
            preflight.setMethod("OPTIONS");
            filter.doFilterInternal(preflight, new MockHttpServletResponse(), filterChain);
        }
        verify(filterChain, times(50)).doFilter(any(), any());
    }

    @Test
    @DisplayName("địa chỉ lấy từ X-Forwarded-For, vì sau proxy thì getRemoteAddr là của proxy")
    void docDiaChiQuaProxy() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT + 1; i++) {
            MockHttpServletRequest request = login("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        // Người thứ hai đi qua đúng proxy ấy vẫn phải lọt: nếu bộ lọc đọc nhầm
        // địa chỉ proxy thì cả Internet chung một gáo, và hàng rào chặn tất cả
        // mọi người cùng lúc thay vì chặn một ai.
        MockHttpServletRequest other = login("10.0.0.1");
        other.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");
        filter.doFilterInternal(other, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("tắt bằng cấu hình thì không chặn gì")
    void tatBangCauHinh() throws Exception {
        RateLimitFilter off = new RateLimitFilter(
                new RateLimitRules(LOGIN_LIMIT, 5, 20, 10, 60, 600),
                new ApiErrorWriter(new ObjectMapper().registerModule(new JavaTimeModule())),
                false);

        for (int i = 0; i < LOGIN_LIMIT + 5; i++) {
            off.doFilterInternal(login("1.2.3.4"), new MockHttpServletResponse(), filterChain);
        }

        verify(filterChain, times(LOGIN_LIMIT + 5)).doFilter(any(), any());
        verify(filterChain, never()).doFilter(null, null);
    }

    private static MockHttpServletRequest login(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletRequest get(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
