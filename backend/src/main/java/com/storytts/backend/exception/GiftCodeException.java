package com.storytts.backend.exception;

import com.storytts.backend.domain.GiftCodeStatus;
import lombok.Getter;

/**
 * Một lời từ chối đổi gift code, kèm mã máy đọc được.
 *
 * <h3>Vì sao một lớp mang một enum, không phải sáu lớp</h3>
 * Sáu tình huống từ chối ở đây khác nhau đúng ở hai thứ: một chuỗi mã và một câu
 * tiếng Việt. Chúng không mang theo dữ liệu riêng nào (khác
 * {@link InsufficientCoinsException}, thứ phải chở theo giá và số dư để trang
 * đọc dựng được nút nạp Xu), và bên bắt không bao giờ cần phân biệt chúng bằng
 * kiểu — chỉ có đúng một {@code @ExceptionHandler} cho cả sáu.
 *
 * <p>Gom vào một enum còn khiến việc kiểm tra lúc đổi mã và việc suy ra tình
 * trạng để hiển thị không thể trôi xa nhau: {@link Reason#forStatus} là chỗ duy
 * nhất nối {@link GiftCodeStatus} với câu trả lời tương ứng, nên thêm một tình
 * trạng mới mà quên câu lỗi của nó là một lỗi biên dịch chứ không phải một chỗ
 * trống phát hiện được lúc chạy.
 */
@Getter
public class GiftCodeException extends RuntimeException {

    /** Vì sao bị từ chối. Tên hằng chính là mã trả về cho frontend. */
    public enum Reason {

        /** Không có mã nào như vậy. */
        INVALID_GIFT_CODE("Gift code không tồn tại."),

        /** Quản trị viên đã tắt mã này. */
        GIFT_CODE_DISABLED("Gift code hiện không sử dụng được."),

        /** Chưa tới giờ bắt đầu. */
        GIFT_CODE_NOT_STARTED("Gift code chưa đến thời gian sử dụng."),

        /** Đã qua hạn. */
        GIFT_CODE_EXPIRED("Gift code đã hết hạn."),

        /** Đã đủ số lượt tối đa. */
        GIFT_CODE_EXHAUSTED("Gift code đã hết lượt sử dụng."),

        /** Tài khoản này đã đổi mã ấy rồi. */
        GIFT_CODE_ALREADY_REDEEMED("Bạn đã sử dụng gift code này rồi.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        /**
         * Lời từ chối tương ứng với một tình trạng.
         *
         * <p>Gọi với {@link GiftCodeStatus#ACTIVE} là một lỗi lập trình, không
         * phải một trạng thái nghiệp vụ: mã đang chạy thì không có gì để từ
         * chối. Ném ra ở đây thay vì trả về một câu chung chung, vì một câu
         * chung chung sẽ đi thẳng tới người dùng và giấu mất chỗ hỏng.
         */
        public static Reason forStatus(GiftCodeStatus status) {
            return switch (status) {
                case DISABLED -> GIFT_CODE_DISABLED;
                case SCHEDULED -> GIFT_CODE_NOT_STARTED;
                case EXPIRED -> GIFT_CODE_EXPIRED;
                case EXHAUSTED -> GIFT_CODE_EXHAUSTED;
                case ACTIVE -> throw new IllegalStateException(
                        "Gift code đang hoạt động thì không có lý do từ chối nào");
            };
        }
    }

    private final Reason reason;

    public GiftCodeException(Reason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }

    /** Mã máy đọc được — frontend dựa vào nó, không dựa vào câu chữ. */
    public String code() {
        return reason.name();
    }
}
