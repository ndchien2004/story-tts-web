package com.storytts.backend.security;

import com.storytts.backend.exception.AccountLockedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

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

    /** Tên tham số dành riêng cho những thẻ media không gửi được header. */
    private static final String TOKEN_PARAM = "access_token";

    /**
     * Đúng những đường mà một thẻ {@code <audio>} trỏ tới — không thêm đường nào.
     *
     * <p>Danh sách này là phạm vi của cả ngoại lệ "token trên URL". Thêm một mẫu
     * vào đây là mở rộng chỗ mà một phiên đăng nhập có thể rò ra ngoài, nên nó
     * đáng được đọc lại mỗi lần dài thêm một dòng.
     */
    private static final List<String> STREAM_PATHS = List.of(
            "/api/chapters/*/audio/*",
            "/api/bgm/*/stream");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
        if (!allowsQueryToken(request)) {
            return null;
        }
        String param = request.getParameter(TOKEN_PARAM);
        return (param != null && !param.isBlank()) ? param : null;
    }

    /**
     * Request này có được phép mang token trên URL không.
     *
     * <h3>Vì sao phải hỏi câu này</h3>
     * Thẻ {@code <audio>} của trình duyệt không gửi được header tuỳ chỉnh, nên
     * đường phát buộc phải nhận token qua query param. Trước đây ngoại lệ ấy
     * được viết ra cho luồng stream nhưng lại có hiệu lực ở <b>mọi</b> đường —
     * ghi chú nói "riêng cho stream audio", mã thì không giới hạn gì.
     *
     * <p>Khác biệt không nhỏ: token nằm trên URL là token đi vào access log của
     * nhà cung cấp, vào header {@code Referer} khi trang có liên kết ra ngoài,
     * vào lịch sử trình duyệt, và vào ảnh chụp màn hình người dùng gửi cho nhau.
     * Một phiên thọ 24 giờ không nên có mặt ở những chỗ đó, và càng không nên có
     * mặt ở đó vì một tính năng không cần tới nó.
     *
     * <p>Nên ngoại lệ được thu về đúng phạm vi nó tồn tại để phục vụ: GET, và
     * đúng những đường mà một thẻ media trỏ tới. Mọi đường khác quay lại chỉ
     * nhận header — kể cả khi kẻ gọi cố tình gắn thêm tham số.
     */
    private boolean allowsQueryToken(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return STREAM_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
