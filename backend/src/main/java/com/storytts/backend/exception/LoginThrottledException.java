package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Tài khoản đang trong quãng nghỉ vì gõ sai mật khẩu quá nhiều lần → HTTP 429.
 *
 * <h3>Vì sao nói thẳng ra rằng đang bị nghỉ</h3>
 * Trả về "sai thông tin đăng nhập" cho gọn thì kín hơn, nhưng nó bỏ rơi đúng
 * người dùng thật: họ gõ đúng mật khẩu ở lần thứ mười một và vẫn bị từ chối, mà
 * không có cách nào hiểu vì sao. Chỗ này đánh đổi một mẩu thông tin — "tài khoản
 * này vừa bị thử nhiều lần" — lấy một lời giải thích và một con số phút.
 *
 * <p>Mẩu thông tin ấy rẻ: kẻ tấn công vốn đã biết họ vừa thử mười lần. Còn với
 * người bị người khác cố tình khóa, đây chính là lời nhắc rằng nên đổi mật khẩu.
 */
@Getter
public class LoginThrottledException extends RuntimeException {

    public static final String CODE = "LOGIN_THROTTLED";

    private final long retryAfterSeconds;

    public LoginThrottledException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String message(long retryAfterSeconds) {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        return ("Tài khoản này vừa bị nhập sai mật khẩu quá nhiều lần. "
                + "Vui lòng thử lại sau %d phút, hoặc dùng chức năng \"Quên mật khẩu\" "
                + "để đặt lại ngay.").formatted(minutes);
    }
}
