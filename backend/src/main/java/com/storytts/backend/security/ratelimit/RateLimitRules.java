package com.storytts.backend.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Bảng tần suất của cả API, xếp theo thứ tự xét.
 *
 * <h3>Thứ tự là một phần của luật</h3>
 * Luật đầu tiên khớp là luật được dùng. Nhóm hẹp đứng trước nhóm rộng, nên cửa
 * đăng nhập lấy mức của riêng nó chứ không rơi vào mức chung của mọi lời gọi
 * ghi. Đổi thứ tự ở đây là đổi luật, không phải sắp xếp lại cho gọn.
 *
 * <h3>Vì sao những con số này</h3>
 * Chúng được chọn để <b>không một người dùng thật nào chạm tới</b>, còn kẻ dò
 * thì chạm ngay ở giây thứ hai:
 *
 * <ul>
 *   <li><b>Đăng nhập, 10 lượt / 5 phút.</b> Người quên mật khẩu thử ba bốn lần
 *       rồi đi bấm "quên mật khẩu"; không ai gõ sai mười lần trong năm phút. Với
 *       kẻ dò thì đây là mức biến việc thử một triệu mật khẩu thành việc của một
 *       năm. Cửa này còn đắt về CPU: mỗi lần thử là một phép BCrypt, nên không
 *       chặn ở đây thì nó vừa là lỗ dò mật khẩu vừa là cách rẻ nhất để bào hết
 *       hai mươi luồng Tomcat.</li>
 *   <li><b>Gửi thư, 5 lượt / giờ.</b> Đăng ký và quên mật khẩu đều gửi thư thật
 *       qua hộp Gmail có hạn 500 lá một ngày. Trần 60 giây sẵn có chỉ tính theo
 *       địa chỉ email, nên đổi email là gửi tiếp — mức này tính theo IP nên nó
 *       chặn đúng thứ kia bỏ lọt.</li>
 *   <li><b>Nhập mã, 20 lượt / giờ.</b> Mã OTP và liên kết đặt lại mật khẩu đều
 *       có bộ đếm số lần thử của riêng chúng, nhưng bộ đếm ấy tính theo <i>một
 *       lượt đăng ký</i>; mức này chặn việc mở hàng nghìn lượt đăng ký để lấy
 *       mỗi lượt năm lần đoán.</li>
 *   <li><b>Gọi AI, 10 lượt / phút.</b> Hạn mức trong ngày đã có, nhưng nó tính
 *       theo ngày — mức này giữ cho một vòng lặp không tiêu sạch phần của cả
 *       ngày trong ba giây.</li>
 *   <li><b>Ghi dữ liệu, 60 lượt / phút.</b> Bình luận, chấm sao, đánh dấu yêu
 *       thích. Người đọc nhanh nhất cũng không tới một lượt mỗi giây.</li>
 *   <li><b>Mọi lời gọi, 600 lượt / phút.</b> Cầu dao cuối, đặt cao hơn hẳn nhịp
 *       của một trình phát đang tua (mỗi lần tua là một request Range) để nó
 *       không bao giờ chạm vào người đang nghe thật.</li>
 * </ul>
 *
 * <p>Khu quản trị cố ý không có mức ghi riêng: nó vẫn nằm dưới cầu dao cuối,
 * nhưng một lô hai mươi chương hay một loạt thao tác sửa nhanh không đáng bị
 * cùng cái trần với ô bình luận.
 */
@Component
public class RateLimitRules {

    private final List<RateLimitRule> rules;

    public RateLimitRules(
            @Value("${app.ratelimit.login-per-5min:10}") int login,
            @Value("${app.ratelimit.mail-per-hour:5}") int mail,
            @Value("${app.ratelimit.code-per-hour:20}") int code,
            @Value("${app.ratelimit.ai-per-minute:10}") int ai,
            @Value("${app.ratelimit.write-per-minute:60}") int write,
            @Value("${app.ratelimit.any-per-minute:600}") int any) {

        this.rules = List.of(
                new RateLimitRule("dang-nhap",
                        Set.of(HttpMethod.POST),
                        List.of("/api/auth/login", "/api/auth/google"),
                        login, Duration.ofMinutes(5)),

                new RateLimitRule("gui-thu",
                        Set.of(HttpMethod.POST),
                        List.of("/api/auth/register", "/api/auth/register/resend",
                                "/api/auth/forgot-password"),
                        mail, Duration.ofHours(1)),

                new RateLimitRule("nhap-ma",
                        Set.of(HttpMethod.POST),
                        List.of("/api/auth/register/verify", "/api/auth/reset-password"),
                        code, Duration.ofHours(1)),

                new RateLimitRule("goi-ai",
                        Set.of(HttpMethod.POST),
                        List.of("/api/chapters/*/tts", "/api/ai/story-assistant"),
                        ai, Duration.ofMinutes(1)),

                new RateLimitRule("ghi-du-lieu",
                        Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE),
                        List.of("/api/**"),
                        write, Duration.ofMinutes(1)),

                new RateLimitRule("moi-loi-goi",
                        Set.of(),
                        List.of("/**"),
                        any, Duration.ofMinutes(1)));
    }

    /**
     * Luật áp cho một request, hoặc null nếu không luật nào khớp.
     *
     * <p>Không bao giờ trả null trên thực tế — luật cuối khớp mọi thứ — nhưng
     * bên gọi vẫn kiểm, để việc xóa luật ấy đi không biến thành một
     * {@code NullPointerException} ở tầng filter.
     */
    public RateLimitRule ruleFor(String method, String path) {
        for (RateLimitRule rule : rules) {
            if (rule.matches(method, path)) {
                return rule;
            }
        }
        return null;
    }
}
