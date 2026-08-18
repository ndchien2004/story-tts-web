package com.storytts.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ghi một lỗi API ra response khi Spring MVC chưa vào cuộc.
 *
 * <h3>Vì sao tồn tại</h3>
 * {@code GlobalExceptionHandler} chỉ chạy được cho ngoại lệ ném ra từ một
 * controller. Ở tầng filter và ở các bộ xử lý của Spring Security thì chưa có
 * controller nào, nên chỗ duy nhất còn lại là tự tuần tự hóa thân response.
 *
 * <p>Gom vào một bean vì có ba chỗ cần đúng việc ấy — điểm vào xác thực, bộ xử
 * lý từ chối quyền, và {@link JwtAuthenticationFilter}. Ba bản sao của cùng một
 * đoạn ghi JSON là ba cơ hội để chúng lệch nhau về hình dạng, mà hình dạng ấy
 * chính là thứ frontend đọc để quyết định đăng xuất hay không.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * @param code mã máy đọc được — đây mới là thứ frontend phân nhánh theo,
     *             không phải {@code message}
     */
    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        write(response, status, code, message, request.getRequestURI());
    }

    public void write(HttpServletResponse response, int status, String code,
                      String message, String path) throws IOException {
        // Đặt trước khi ghi thân: một khi luồng ra đã có byte đầu tiên thì mã
        // trạng thái và header không đổi được nữa.
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(status, code, message, path));
    }
}
