package com.storytts.backend.security;

import com.storytts.backend.exception.AccountLockedException;
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
 *
 * <h3>Đây là lớp kiểm tra trạng thái tài khoản của toàn hệ thống</h3>
 * Mỗi request mang token đều nạp lại người dùng từ cơ sở dữ liệu, nên một tài
 * khoản bị khóa mất quyền ngay lập tức chứ không phải đợi token hết hạn. Không
 * có danh sách token bị thu hồi, cũng không có cột phiên bản token — cơ sở dữ
 * liệu đã là nguồn sự thật, và nó được hỏi ở mọi request.
 *
 * <p>Cái giá là một câu SELECT theo khóa chính cho mỗi request có token. Đổi lại
 * là không tồn tại khoảng thời gian nào mà quyền đã bị thu hồi nhưng token vẫn
 * còn tác dụng — thứ mà mọi phương án nhanh hơn (cache trạng thái, tin vào claim
 * trong token) đều phải chấp nhận.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ApiErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtService.extractUserId(token);
            if (userId != null) {
                AppUserPrincipal principal;
                try {
                    principal = userDetailsService.loadUserById(userId);
                } catch (Exception ex) {
                    log.debug("Không nạp được người dùng từ JWT: {}", ex.getMessage());
                    filterChain.doFilter(request, response);
                    return;
                }

                if (!principal.isEnabled()) {
                    // Dừng hẳn tại đây, không đi tiếp xuống chuỗi filter.
                    //
                    // Trước đây nhánh này chỉ *không* đặt authentication rồi cho
                    // request chạy tiếp, và đó chính là lỗi. Những đường đòi đăng
                    // nhập thì đúng là bị chặn — nhưng đường đọc truyện để
                    // permitAll ở tầng URL, nên người bị khóa lặng lẽ tụt xuống
                    // thành Khách và đọc tiếp như không có gì xảy ra. Tệ hơn:
                    // trình duyệt không nhận được tín hiệu nào nên vẫn vẽ giao
                    // diện đã đăng nhập, và người dùng không hiểu vì sao mọi thứ
                    // riêng tư của mình bỗng biến mất.
                    //
                    // Từ chối thẳng biến điều đó thành một câu trả lời rõ ràng mà
                    // frontend xử lý được đúng một lần, ở đúng một chỗ.
                    log.info("Từ chối request của tài khoản bị khóa (id={}) tới {}",
                            userId, request.getRequestURI());
                    errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                            AccountLockedException.CODE, AccountLockedException.MESSAGE);
                    return;
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
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
