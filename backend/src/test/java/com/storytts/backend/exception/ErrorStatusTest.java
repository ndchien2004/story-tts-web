package com.storytts.backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Một yêu cầu hỏng phải nói ĐÚNG nó hỏng vì cái gì.
 *
 * <h3>Bài kiểm này ra đời từ một sự cố thật, và nó tồn tại để sự cố ấy không
 * lặp lại</h3>
 * Bản backend trên Render còn cũ hơn bản frontend trên Vercel, nên
 * {@code POST /api/support/ws-ticket} không tồn tại ở đó. Câu trả lời đúng là
 * <b>404</b> — một câu chỉ thẳng vào việc phải làm: triển khai lại backend.
 *
 * <p>Câu đi ra lại là <b>500 INTERNAL_ERROR</b>, vì
 * {@link GlobalExceptionHandler} có một bộ bắt {@code Exception.class} và
 * không có gì hẹp hơn cho {@code NoResourceFoundException}. Từ con số ấy, cuộc
 * điều tra đi tìm lỗi trong cấu hình WebSocket, danh sách CORS, proxy của
 * Render, biến môi trường và chuyện {@code ws} với {@code wss} — bốn chỗ không
 * có gì. Một mã trạng thái sai không chỉ là một con số sai; nó là một tấm biển
 * chỉ đường sai, và người đọc nó đi đúng theo hướng ấy.
 *
 * <p>Nên bốn phép kiểm dưới đây không kiểm một tính năng nào của sản phẩm.
 * Chúng kiểm rằng <i>máy chủ nói thật về lỗi của chính nó</i> — thứ mà không
 * bài kiểm nào khác trong dự án này chạm tới, và thứ mà mọi lần gỡ lỗi về sau
 * đều dựa vào.
 *
 * <p>Chạy bằng {@code @SpringBootTest} chứ không {@code standaloneSetup}, và đó
 * là điều kiện: {@code NoResourceFoundException} do bộ xử lý tài nguyên tĩnh
 * của Spring Boot ném ra, và bộ ấy chỉ tồn tại trong một context thật. Một
 * MockMvc dựng tay sẽ xanh mà không chứng minh được gì.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Đúng hình dạng của sự cố đã xảy ra: frontend gọi một đường mà bản máy chủ
     * này không có, và nó cầm theo một token hợp lệ.
     *
     * <p>Token là chi tiết quyết định. Không có nó thì chuỗi lọc trả 401 và yêu
     * cầu không bao giờ tới được phần dò đường — nên lỗi này chỉ lộ ra với
     * người đã đăng nhập, tức là đúng với mọi người dùng thật.
     */
    @Test
    @DisplayName("Đường không tồn tại → 404 NOT_FOUND, không phải 500")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(post("/api/support/duong-nay-khong-ton-tai")
                        .with(user("nguoi-doc").roles("MEMBER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    /**
     * Cùng một đường, nhưng ở khu quản trị.
     *
     * <p>Tách riêng vì hai nhóm đi qua hai luật khác nhau trong
     * {@code SecurityConfig}: {@code /api/admin/**} đòi {@code hasRole('ADMIN')}
     * ở tầng URL. Một quản trị viên hợp lệ gọi một đường không có phải nhận 404,
     * chứ không phải 403 (nghe như bị cấm) và càng không phải 500.
     */
    @Test
    @DisplayName("Đường quản trị không tồn tại → 404, không phải 403 hay 500")
    void unknownAdminPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/support/duong-nay-khong-ton-tai")
                        .with(user("quan-tri").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    /**
     * Đường có thật, phương thức sai.
     *
     * <p>{@code /api/support/ws-ticket} chỉ nhận POST. Gọi bằng GET là lỗi của
     * máy khách, và 405 nói ra được điều đó; 500 thì không.
     */
    @Test
    @DisplayName("Sai phương thức → 405, không phải 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/support/ws-ticket")
                        .with(user("nguoi-doc").roles("MEMBER")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * Chưa đăng nhập thì vẫn là 401, và thứ tự ấy phải giữ nguyên.
     *
     * <p>Phép kiểm này canh chừng chiều ngược lại của lần sửa trên: bộ bắt lỗi
     * mới <b>không</b> được biến một đường bị chặn thành một đường "không tồn
     * tại". Nói cho người lạ biết đường nào có và đường nào không là một cách rò
     * rỉ sơ đồ API, và {@code .anyRequest().authenticated()} vốn đã chặn trước
     * khi phần dò đường chạy.
     */
    @Test
    @DisplayName("Chưa đăng nhập → vẫn 401, bộ bắt lỗi mới không nới cửa nào")
    void anonymousStillGetsUnauthorised() throws Exception {
        mockMvc.perform(post("/api/support/duong-nay-khong-ton-tai"))
                .andExpect(status().isUnauthorized());
    }
}
