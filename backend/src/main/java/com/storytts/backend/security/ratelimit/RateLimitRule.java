package com.storytts.backend.security.ratelimit;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Một nhóm endpoint và mức tần suất cho phép của nhóm ấy.
 *
 * <h3>Vì sao chia thành nhóm, không đặt một con số cho cả API</h3>
 * Một mức duy nhất buộc phải lấy theo đường bận nhất — mà đường bận nhất là
 * đường phát audio, thứ một trình phát gọi hàng chục lần cho một chương. Đặt
 * trần chung ở đó thì cửa đăng nhập được đi kèm một hạn mức rộng gấp trăm lần
 * chỗ nó cần, tức là không còn là hàng rào nữa.
 *
 * <p>Nên mỗi nhóm mang mức của riêng nó, và mỗi nhóm cũng là một gáo riêng: dò
 * mật khẩu tới cạn phần của cửa đăng nhập không được phép làm người khác mất
 * quyền đọc truyện.
 *
 * @param name     tên nhóm, đi vào khóa gáo và vào nhật ký
 * @param methods  phương thức thuộc nhóm; rỗng nghĩa là mọi phương thức
 * @param patterns mẫu đường dẫn theo cú pháp Ant
 * @param capacity số lượt cho phép trong một {@code window}
 * @param window   quãng thời gian rót lại đủ {@code capacity} token
 */
public record RateLimitRule(
        String name,
        Set<HttpMethod> methods,
        List<String> patterns,
        int capacity,
        Duration window
) {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public boolean matches(String method, String path) {
        if (!methods.isEmpty() && !methods.contains(HttpMethod.valueOf(method))) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** Câu nói với người bị chặn — không nêu con số nội bộ, chỉ nêu việc cần làm. */
    public String message() {
        return "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.";
    }
}
