package com.storytts.backend.config;

import com.storytts.backend.security.ApiErrorWriter;
import com.storytts.backend.security.CustomUserDetailsService;
import com.storytts.backend.security.JwtAuthenticationFilter;
import com.storytts.backend.security.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cấu hình Spring Security: stateless + JWT, CORS cho React, phân quyền theo URL.
 * <p>
 * Lưu ý: các endpoint đọc chương/nghe audio để {@code permitAll} ở tầng URL, vì "Khách"
 * vẫn được đọc chương PUBLIC. Việc chặn chương MEMBER/VIP nằm ở
 * {@code AccessControlService} — một lớp kiểm tra quyền dùng chung.
 * <p>
 * Ngoại lệ đáng chú ý: <b>tạo</b> audio bằng AI thì bắt buộc đăng nhập ngay ở tầng
 * URL này, vì mỗi lần tạo là một lần gọi API tính tiền và hạn mức mỗi ngày chỉ đếm
 * được khi biết là của ai.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CorsProperties corsProperties;
    private final ApiErrorWriter apiErrorWriter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer -> AbstractHttpConfigurer.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        // Hai câu trả lời này cố ý *không* nói gì về tài khoản bị
                        // khóa. Request mang token của một tài khoản bị khóa
                        // không bao giờ tới được đây — JwtAuthenticationFilter
                        // đã chặn và tự trả lời từ trước đó, vì nó là chỗ duy
                        // nhất biết được token thuộc về ai.
                        .authenticationEntryPoint((request, response, ex) -> apiErrorWriter.write(
                                request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                "Bạn cần đăng nhập để thực hiện thao tác này."))
                        .accessDeniedHandler((request, response, ex) -> apiErrorWriter.write(
                                request, response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                                "Bạn không có quyền thực hiện thao tác này.")))
                .authorizeHttpRequests(auth -> auth
                        // --- Công khai: đăng ký / đăng nhập / quên mật khẩu ---
                        // Liệt kê từng đường thay vì /api/auth/**, để /me và
                        // /logout vẫn nằm sau lớp xác thực.
                        .requestMatchers(
                                "/api/auth/register", "/api/auth/register/verify",
                                "/api/auth/register/resend", "/api/auth/login", "/api/auth/google",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/providers").permitAll()

                        // --- Tài liệu API ---
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // --- Thanh toán ---
                        // PayOS gọi về không mang theo JWT nào; thứ chặn người lạ
                        // là chữ ký HMAC trong body, đối chiếu ở PaymentOrderService.
                        .requestMatchers(HttpMethod.POST, "/api/payments/payos/webhook").permitAll()
                        // Bảng giá là thứ cần xem được trước khi có tài khoản.
                        .requestMatchers(HttpMethod.GET, "/api/vip/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/wallet/packages").permitAll()

                        // --- Khu vực quản trị: chỉ Admin ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // --- Đọc dữ liệu truyện: cho phép cả Khách, quyền chương xử lý ở service ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/stories/**", "/api/chapters/**",
                                "/api/genres/**", "/api/authors/**").permitAll()

                        // --- Nhạc nền ---
                        // Không có gì riêng tư ở đây: đây là nhạc quản trị viên
                        // chủ động mở cho mọi người nghe, và trang đọc hỏi danh
                        // sách này ngay khi mở một chương công khai.
                        .requestMatchers(HttpMethod.GET, "/api/bgm/**").permitAll()

                        // --- Nghe bằng AI ---
                        // Trạng thái xem được cả khi chưa đăng nhập, để trang đọc biết
                        // nên hiện nút hay hiện lời mời đăng nhập. Còn việc tạo audio
                        // (POST /api/chapters/{id}/tts) không nằm trong danh sách này,
                        // nên nó rơi vào anyRequest().authenticated() bên dưới: tạo
                        // audio là tiêu tiền, phải có danh tính mới tính hạn mức được.
                        .requestMatchers(HttpMethod.GET, "/api/tts/status").permitAll()

                        // --- Trợ lý AI ---
                        // Cùng một cách chia với "Nghe bằng AI", vì cùng một lý
                        // do: trạng thái xem được khi chưa đăng nhập, để trang
                        // đọc biết máy chủ này có bật trợ lý không mà quyết định
                        // vẽ hay không vẽ cái nút. Còn POST /api/ai/story-assistant
                        // cố ý vắng mặt ở đây: mỗi câu hỏi là một lần gọi API
                        // tính tiền, và hạn mức mỗi ngày chỉ đếm được khi biết
                        // là của ai.
                        .requestMatchers(HttpMethod.GET, "/api/ai/status").permitAll()

                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider(passwordEncoder()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Trước cả lớp xác thực, và thứ tự ấy là điểm chính: nạp người
                // dùng từ JWT tốn một câu SELECT cho mỗi request: chặn sau đó
                // nghĩa là kẻ bắn request dồn dập vẫn bắt được cơ sở dữ liệu
                // làm việc cho mình.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Trình phát audio cần đọc được các header này khi stream theo Range;
        // Retry-After là để trang đọc nói được "hết lượt, mai quay lại".
        configuration.setExposedHeaders(List.of("Content-Disposition", "Content-Range",
                "Accept-Ranges", "Content-Length", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
