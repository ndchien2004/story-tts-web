package com.storytts.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.dto.common.ApiErrorResponse;
import com.storytts.backend.security.CustomUserDetailsService;
import com.storytts.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

import java.time.Instant;
import java.util.List;

/**
 * Cấu hình Spring Security: stateless + JWT, CORS cho React, phân quyền theo URL.
 * <p>
 * Lưu ý: các endpoint đọc chương/nghe audio để {@code permitAll} ở tầng URL, vì "Khách"
 * vẫn được đọc chương PUBLIC. Việc chặn chương MEMBER/VIP nằm ở
 * {@code AccessControlService} — một lớp kiểm tra quyền dùng chung (mục 7 & 10 đề bài).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

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
                        .authenticationEntryPoint((request, response, ex) -> writeError(
                                response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                "Bạn cần đăng nhập để thực hiện thao tác này.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) -> writeError(
                                response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                                "Bạn không có quyền thực hiện thao tác này.", request.getRequestURI())))
                .authorizeHttpRequests(auth -> auth
                        // --- Công khai: đăng ký / đăng nhập ---
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                        // --- Tài liệu API ---
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // --- Khu vực quản trị: chỉ Admin ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // --- Đọc dữ liệu truyện: cho phép cả Khách, quyền chương xử lý ở service ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/stories/**", "/api/chapters/**",
                                "/api/genres/**", "/api/authors/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chapters/*/tts").permitAll()

                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider(passwordEncoder()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Trình phát audio cần đọc được các header này khi stream theo Range.
        configuration.setExposedHeaders(List.of("Content-Disposition", "Content-Range", "Accept-Ranges", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, int status, String error,
                            String message, String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status, error, message, path, null, null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
