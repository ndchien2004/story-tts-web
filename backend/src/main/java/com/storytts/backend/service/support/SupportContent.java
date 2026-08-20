package com.storytts.backend.service.support;

import com.storytts.backend.exception.SupportException;

import java.text.Normalizer;

/**
 * Biến một chuỗi do người lạ gõ thành một chuỗi an toàn để lưu và để hiển thị.
 *
 * <h3>Vì sao đây là một lớp riêng, không phải ba dòng trong service</h3>
 * Vì nó có <b>hai</b> bên gọi — đường REST và khung WebSocket — và cả hai phải
 * làm giống hệt nhau. Đặc tả nói rõ điều đó: một cửa vào không được phép là lối
 * vòng qua phép kiểm của cửa kia. Hai bản sao của bốn bước dưới đây là hai bản
 * có thể sửa lệch nhau, và cái sửa thiếu sẽ là cái ít được nhìn tới.
 *
 * <h3>Bốn bước, theo đúng thứ tự này</h3>
 * <pre>
 *   1. chuẩn hóa Unicode về NFC
 *   2. gộp xuống dòng về '\n'
 *   3. bỏ ký tự điều khiển, giữ lại '\n' và '\t'
 *   4. cắt khoảng trắng hai đầu, rồi mới đo độ dài
 * </pre>
 *
 * Thứ tự không đổi chỗ được. Đo độ dài <i>trước</i> khi cắt sẽ để một chuỗi hai
 * nghìn dấu cách bị từ chối vì "quá dài" thay vì vì "để trống", và chuẩn hóa
 * <i>sau</i> khi đo sẽ khiến con số đã đo không còn đúng với chuỗi được lưu.
 *
 * <h3>Vì sao NFC</h3>
 * Tiếng Việt viết được bằng hai cách cho cùng một chữ: "ế" là một điểm mã, hoặc
 * là "e" cộng hai dấu rời. Trình duyệt và bàn phím khác nhau gửi lên khác nhau,
 * và nếu không gộp về một dạng thì cùng một câu sẽ có hai độ dài khác nhau và
 * hai kết quả tìm kiếm khác nhau. Frontend đã gộp mọi phản hồi về NFC
 * ({@code normalizeDeep}); đây là đầu còn lại của cùng quy tắc ấy.
 *
 * <h3>Vì sao bỏ ký tự điều khiển</h3>
 * Không phải vì XSS — chống XSS nằm ở chỗ giao diện dựng nội dung bằng text
 * node chứ không bằng HTML, và đó là hàng rào thật. Đây là chuyện khác: ký tự
 * đảo chiều văn bản (U+202E) và các ký tự vô hình khác cho phép một tin nhắn
 * hiển thị ra một câu khác hẳn với câu được lưu, tức là quản trị viên đọc một
 * đằng còn nhật ký ghi một nẻo.
 *
 * <p>{@code \n} và {@code \t} được giữ vì chúng là <i>nội dung</i>: một người
 * dán vào đây thông báo lỗi họ gặp phải, và một khối văn bản bị ép thành một
 * dòng thì khó đọc hơn hẳn.
 */
final class SupportContent {

    /**
     * Trần số dòng.
     *
     * <p>Không phải chuyện thẩm mỹ: một tin nhắn hai nghìn ký tự toàn xuống dòng
     * là hai nghìn phần tử trong danh sách chat và một bong bóng cao bằng cả màn
     * hình của người nhận. Trần độ dài không chặn được việc ấy vì nó đếm ký tự,
     * còn đây đếm dòng.
     */
    private static final int MAX_LINES = 40;

    private SupportContent() {
    }

    /**
     * Làm sạch và kiểm tra, hoặc ném ra một lời từ chối có mã.
     *
     * @param limit trần độ dài thật sự áp — lấy từ cấu hình, xem
     *              {@code SupportProperties.effectiveMaxMessageLength()}
     * @return chuỗi đã sẵn sàng để ghi xuống cột {@code content}
     * @throws SupportException {@code MESSAGE_EMPTY} hoặc {@code MESSAGE_TOO_LONG}
     */
    static String sanitise(String raw, int limit) {
        if (raw == null) {
            throw new SupportException(SupportException.Reason.MESSAGE_EMPTY);
        }

        String normalised = Normalizer.normalize(raw, Normalizer.Form.NFC);

        StringBuilder cleaned = new StringBuilder(normalised.length());
        int lines = 1;
        for (int i = 0; i < normalised.length(); i++) {
            char c = normalised.charAt(i);

            // Gộp mọi kiểu xuống dòng về '\n': Windows gửi "\r\n", vài trình
            // soạn thảo cũ gửi "\r" một mình. Ba cách viết cho một ý nghĩa sẽ
            // làm phép đếm dòng bên dưới và cách hiển thị lệch nhau.
            if (c == '\r') {
                if (i + 1 < normalised.length() && normalised.charAt(i + 1) == '\n') {
                    continue;
                }
                c = '\n';
            }

            if (c == '\n') {
                if (++lines > MAX_LINES) {
                    // Cắt thay vì từ chối: phần đã gõ vẫn tới nơi, và đó là ứng
                    // xử đúng với một người đang cần giúp đỡ. Cùng lập luận với
                    // NotificationService.clamp.
                    break;
                }
                cleaned.append(c);
                continue;
            }

            if (c == '\t') {
                cleaned.append(c);
                continue;
            }

            // Bỏ mọi ký tự điều khiển và mọi ký tự định dạng vô hình. Xem ghi
            // chú ở đầu lớp về lý do.
            int type = Character.getType(c);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.SURROGATE || type == Character.UNASSIGNED) {
                continue;
            }

            cleaned.append(c);
        }

        String trimmed = cleaned.toString().strip();

        if (trimmed.isEmpty()) {
            throw new SupportException(SupportException.Reason.MESSAGE_EMPTY);
        }
        if (trimmed.length() > limit) {
            throw SupportException.tooLong(limit);
        }
        return trimmed;
    }

    /**
     * Định danh lần bấm gửi, đã kiểm.
     *
     * <p>Chỉ nhận chữ, số, gạch ngang và gạch dưới — vừa đủ cho một UUID và
     * không đủ cho một chỗ nhét dữ liệu. Đây không phải phòng chống SQL injection
     * (câu lệnh đã tham số hóa), mà là giữ cho một cột 64 ký tự không trở thành
     * một kênh phụ chở nội dung tùy ý qua ràng buộc {@code UNIQUE}.
     */
    static String requireClientId(String raw, int limit) {
        if (raw == null) {
            throw new SupportException(SupportException.Reason.MESSAGE_INVALID);
        }
        String value = raw.strip();
        if (value.isEmpty() || value.length() > limit) {
            throw new SupportException(SupportException.Reason.MESSAGE_INVALID);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                throw new SupportException(SupportException.Reason.MESSAGE_INVALID);
            }
        }
        return value;
    }
}
