package com.storytts.backend.config;

import com.storytts.backend.domain.SupportMessage;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mọi con số điều chỉnh được của hộp thư hỗ trợ, ở đúng một chỗ.
 *
 * <h3>Vì sao không rải hằng số vào mã</h3>
 * Bảy con số dưới đây là bảy quyết định vận hành, không phải bảy chi tiết cài
 * đặt: siết trần tin nhắn, hạ mức tần suất, đổi nhịp tim khi đứng sau một proxy
 * khác — tất cả đều là thứ cần làm được bằng một biến môi trường lúc hai giờ
 * sáng, không phải bằng một lần biên dịch lại.
 *
 * @param maxMessageLength   trần độ dài một tin nhắn, tính bằng ký tự sau khi đã
 *                           chuẩn hóa. Luôn bị kẹp xuống {@link SupportMessage#CONTENT_LIMIT}
 *                           — trần của lược đồ là chốt chặn cuối, và một cấu
 *                           hình sai không được phép vượt qua nó.
 * @param historyPageSize    số tin một trang lịch sử. Cũng là trần cho tham số
 *                           {@code limit} mà trình duyệt gửi lên.
 * @param syncPageSize       số tin tối đa một lượt "lấy phần bỏ lỡ" trả về.
 *                           Rộng hơn trang lịch sử vì nó chạy một lần cho mỗi
 *                           lần nối lại chứ không chạy mỗi lần cuộn.
 * @param inboxPageSize      trần một trang hộp thư quản trị.
 * @param sendPerMinute      số tin một tài khoản gửi được trong một phút, tính
 *                           theo <i>người</i> chứ không theo kết nối — mở thêm
 *                           tab không được phép nhân đôi hạn mức.
 * @param maxSessionsPerUser số kết nối WebSocket cùng lúc của một tài khoản.
 *                           Nhiều tab, điện thoại và máy tính là chuyện bình
 *                           thường; vài chục kết nối thì không.
 * @param maxSessions        trần chung của cả máy chủ. Đây là chỗ chặn đường
 *                           một đợt nối lại dồn dập biến thành cạn bộ nhớ.
 * @param heartbeatInterval  nhịp ping giữ kết nối qua các lớp proxy. Phải ngắn
 *                           hơn hạn chờ nhàn rỗi của mọi proxy đứng trước.
 * @param idleTimeout        không nghe thấy gì từ một kết nối trong quãng này
 *                           thì coi như nó đã chết và đóng lại. Phải dài hơn vài
 *                           nhịp {@code heartbeatInterval}, nếu không một lần
 *                           mất gói sẽ giết một kết nối còn sống.
 * @param sessionMaxLifetime hạn sống tối đa của một kết nối đã xác thực, kể từ
 *                           lúc bắt tay. Kết nối được mở bằng một cái vé phát
 *                           cho một phiên còn hiệu lực, nhưng phiên ấy có hạn
 *                           còn socket thì không tự biết — thiếu con số này thì
 *                           "đã xác thực một lần" thành "được xác thực mãi
 *                           mãi". Nó không phải hàng rào chính: mọi thao tác có
 *                           đặc quyền đều nạp lại tài khoản từ cơ sở dữ liệu và
 *                           kiểm lại quyền, đúng như {@code JwtAuthenticationFilter}
 *                           làm cho mỗi request, nên khóa tài khoản hay hạ
 *                           quyền có hiệu lực ngay. Con số này chỉ đóng nốt
 *                           trường hợp một kết nối im lặng thuộc về một phiên
 *                           đã hết hạn từ lâu.
 */
@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(
        int maxMessageLength,
        int historyPageSize,
        int syncPageSize,
        int inboxPageSize,
        int sendPerMinute,
        int maxSessionsPerUser,
        int maxSessions,
        Duration heartbeatInterval,
        Duration idleTimeout,
        Duration sessionMaxLifetime
) {

    /**
     * Trần thật sự áp cho nội dung, đã kẹp xuống trần của lược đồ.
     *
     * <p>Hai tầng vì chúng trả lời hai câu khác nhau: cấu hình là chính sách sản
     * phẩm, siết lại được bất cứ lúc nào; {@code varchar(4000)} là chốt chặn
     * cuối không cho một cấu hình sai ghi được một hàng quá khổ — và cơ sở dữ
     * liệu sẽ từ chối bằng một lỗi thô, thứ không bao giờ nên là cách người dùng
     * biết mình gõ dài quá.
     */
    public int effectiveMaxMessageLength() {
        return Math.max(1, Math.min(maxMessageLength, SupportMessage.CONTENT_LIMIT));
    }

}
