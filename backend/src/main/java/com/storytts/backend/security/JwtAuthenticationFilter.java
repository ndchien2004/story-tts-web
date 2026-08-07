package com.storytts.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc JWT từ header {@code Authorization: Bearer ...} và nạp người dùng vào SecurityContext.
 * Không có token cũng không chặn request — endpoint công khai vẫn chạy được với tư cách "Khách".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtService.extractUserId(token);
            if (userId != null) {
                try {
                    AppUserPrincipal principal = userDetailsService.loadUserById(userId);
                    if (principal.isEnabled()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception ex) {
                    log.debug("Không nạp được người dùng từ JWT: {}", ex.getMessage());
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        // Thẻ <audio> của trình duyệt không gửi được header tuỳ chỉnh,
        // nên cho phép truyền token qua query param riêng cho luồng stream audio.
        String param = request.getParameter("access_token");
        return (param != null && !param.isBlank()) ? param : null;
    }
}
