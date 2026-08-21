package com.storytts.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Một lời từ chối của hộp thư hỗ trợ, kèm mã máy đọc được.
 *
 * <h3>Vì sao một lớp mang một enum</h3>
 * Cùng lập luận với {@link GiftCodeException}: chín tình huống dưới đây khác
 * nhau đúng ở một chuỗi mã, một câu tiếng Việt và một mã HTTP. Không cái nào
 * chở theo dữ liệu riêng, và bên bắt không bao giờ cần phân biệt chúng bằng
 * kiểu — chỉ có đúng một {@code @ExceptionHandler} cho cả chín.
 *
 * <h3>Vì sao mã trạng thái HTTP nằm <i>trong</i> enum</h3>
 * Vì tính năng này có hai cửa vào — REST và WebSocket — và chúng phải từ chối
 * <b>giống hệt nhau</b>. Đặc tả nói rõ: một đường không được phép là lối vòng
 * qua phép kiểm của đường kia.
 *
 * <pre>
 *   REST      → 403 + {"error": "CONVERSATION_ACCESS_DENIED", ...}
 *   WebSocket → khung {"type": "error", "code": "CONVERSATION_ACCESS_DENIED", ...}
 * </pre>
 *
 * Cùng một hằng sinh ra cả hai, nên không tồn tại đường nào để hai cửa lệch
 * nhau về mã. Trình duyệt phân nhánh theo {@code code}, không theo câu chữ —
 * câu chữ là tiếng Việt và sẽ được sửa lại, mã thì không.
 *
 * <h3>Những gì cố ý không có trong câu trả lời</h3>
 * Không có tên bảng, tên lớp, câu SQL, hay id của tài nguyên không thuộc về
 * người gọi. {@link Reason#CONVERSATION_NOT_FOUND} và
 * {@link Reason#CONVERSATION_ACCESS_DENIED} nói hai chuyện khác nhau với
 * <i>chúng ta</i> nhưng người gọi không phân biệt được chúng bằng cách thử id
 * người khác — đường REST của người đọc không nhận {@code conversationId} nào
 * cả, xem {@code SupportController}.
 */
@Getter
public class SupportException extends RuntimeException {

    /** Vì sao bị từ chối. Tên hằng chính là mã trả về cho frontend. */
    public enum Reason {

        /** Không có luồng nào như vậy — hoặc người gọi không được phép biết là có. */
        CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND,
                "Không tìm thấy cuộc trò chuyện."),

        /** Luồng của người khác. Xem ghi chú ở đầu lớp về việc không rò rỉ sự tồn tại. */
        CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập cuộc trò chuyện này."),

        /**
         * Quản trị viên đã chặn luồng này.
         *
         * <p>409 chứ không phải 403: đây là câu trả lời về <i>trạng thái</i> của
         * một thứ người gọi vẫn có quyền xem, không phải về quyền của họ. Giao
         * diện vì thế vẫn hiện lịch sử, chỉ khóa ô soạn tin.
         */
        CONVERSATION_BLOCKED(HttpStatus.CONFLICT,
                "Cuộc trò chuyện này đã bị khóa. Vui lòng liên hệ quản trị viên."),

        /** Gõ xong toàn khoảng trắng, hoặc chỉ có ký tự điều khiển. */
        MESSAGE_EMPTY(HttpStatus.BAD_REQUEST,
                "Nội dung tin nhắn không được để trống."),

        /** Dài hơn trần cấu hình. Câu chữ được ghép kèm con số, xem {@link #tooLong}. */
        MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST,
                "Tin nhắn quá dài."),

        /**
         * Thiếu hoặc sai {@code clientMessageId}.
         *
         * <p>Từ chối thay vì tự sinh một cái: định danh ấy là thứ khiến một lần
         * thử lại không tạo ra tin nhắn thứ hai, và một cái do máy chủ sinh sẽ
         * khác nhau ở mỗi lần thử — tức là chống trùng im lặng ngừng hoạt động
         * đúng lúc nó cần nhất.
         */
        MESSAGE_INVALID(HttpStatus.BAD_REQUEST,
                "Yêu cầu gửi tin nhắn không hợp lệ."),

        /** Báo "đã đọc tới tin số N" với một N không thuộc luồng này. */
        INVALID_READ_TARGET(HttpStatus.BAD_REQUEST,
                "Tin nhắn được đánh dấu đã đọc không thuộc cuộc trò chuyện này."),

        /** Đổi trạng thái sang một giá trị không có, hoặc một bước không được phép. */
        INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT,
                "Không thể chuyển cuộc trò chuyện sang trạng thái này."),

        /**
         * Tài khoản quản trị viên không có luồng hỗ trợ của riêng mình.
         *
         * <p>Phía hỗ trợ là một phía <i>chung</i>, không phải một người — xem
         * {@code SupportConversation}. Cho một quản trị viên tự mở luồng là dựng
         * ra một cuộc trò chuyện mà cả hai đầu là cùng một người, và mọi phép
         * đếm chưa đọc từ đó trở đi đều vô nghĩa.
         */
        SUPPORT_NOT_FOR_ADMIN(HttpStatus.BAD_REQUEST,
                "Tài khoản quản trị viên dùng hộp thư hỗ trợ trong khu quản trị."),

        /** Xin trò chuyện với trợ lý trong khi một tư vấn viên đang phụ trách dở. */
        SUPPORT_HUMAN_IN_CHARGE(HttpStatus.CONFLICT,
                "Cuộc trò chuyện này đang được tư vấn viên phụ trách. "
                        + "Bạn cứ nhắn trực tiếp ở đây nhé."),

        /**
         * Trợ lý đang viết dở câu trả lời trước.
         *
         * <p>Đây là hàng rào giữ thứ tự: hai câu hỏi chạy song song thì hai câu
         * trả lời về không theo thứ tự nào cả, và một cuộc trò chuyện đọc ra
         * lộn xộn thì tệ hơn hẳn một lần phải chờ. Xem {@code SupportAssistant}.
         */
        SUPPORT_ASSISTANT_BUSY(HttpStatus.TOO_MANY_REQUESTS,
                "Trợ lý đang trả lời câu trước. Bạn chờ một chút nhé."),

        /** Luồng đã rời khỏi tay trợ lý — người đọc vừa xin gặp tư vấn viên. */
        SUPPORT_ASSISTANT_NOT_ACTIVE(HttpStatus.CONFLICT,
                "Cuộc trò chuyện này không còn do trợ lý AI phụ trách."),

        /**
         * Chiều ngược lại: gửi một tin thường vào luồng đang do trợ lý phụ trách.
         *
         * <p>Chốt chặn cho một trình duyệt đang mở từ trước khi chuyển chế độ,
         * và cho đường WebSocket — vốn không gọi trợ lý được, vì giữ một luồng
         * xử lý socket đứng chờ Gemini ba mươi giây là chuyện không làm.
         *
         * <p>Từ chối chứ không lặng lẽ ghi: một câu hỏi nằm trong luồng AI mà
         * không có câu trả lời nào bên dưới là thứ người đọc sẽ ngồi chờ mãi.
         */
        SUPPORT_ASSISTANT_IN_CHARGE(HttpStatus.CONFLICT,
                "Cuộc trò chuyện đang ở chế độ trợ lý AI. Vui lòng tải lại trang."),

        /** Trợ lý bị tắt trên máy chủ, hoặc chưa có khóa Gemini. */
        SUPPORT_ASSISTANT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE,
                "Trợ lý AI đang không khả dụng. Bạn có thể chat với tư vấn viên."),

        /**
         * Gửi quá nhanh.
         *
         * <p>Mã riêng chứ không dùng lại {@code RATE_LIMITED} của
         * {@code RateLimitFilter}: hàng rào kia đếm theo địa chỉ mạng và chặn
         * cả request, còn hàng rào này đếm theo <i>tài khoản</i> và chỉ chặn
         * đúng việc gửi tin. Trình duyệt xử lý hai chuyện ấy khác nhau — cái thứ
         * nhất là "trang đang bị chặn", cái thứ hai là "tin này chưa gửi được,
         * thử lại sau vài giây".
         */
        SUPPORT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
                "Bạn gửi tin quá nhanh. Vui lòng chờ một lát rồi thử lại.");

        private final HttpStatus status;
        private final String message;

        Reason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    private final Reason reason;

    public SupportException(Reason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }

    private SupportException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Quá dài, kèm con số để người gõ biết phải cắt bớt bao nhiêu.
     *
     * <p>Nói ra trần là an toàn: nó vốn đã nằm trong khung
     * {@code connection:ready} gửi lúc mở kết nối, vì ô soạn tin cần nó để đếm
     * ký tự tại chỗ thay vì để người ta gõ xong mới biết là thừa.
     */
    public static SupportException tooLong(int limit) {
        return new SupportException(Reason.MESSAGE_TOO_LONG,
                "Tin nhắn quá dài. Tối đa %d ký tự.".formatted(limit));
    }

    /** Mã máy đọc được — frontend dựa vào nó, không dựa vào câu chữ. */
    public String code() {
        return reason.name();
    }

    public HttpStatus status() {
        return reason.getStatus();
    }
}
