package com.storytts.backend.exception;

/**
 * Tài khoản đã bị quản trị viên khóa → HTTP 401 kèm mã {@code ACCOUNT_LOCKED}.
 *
 * <h3>Vì sao cần một mã riêng thay vì dùng lại 401 thường</h3>
 * Trình duyệt phải phân biệt được ba chuyện hoàn toàn khác nhau mà trước đây
 * đều trả về đúng một câu 401 {@code UNAUTHORIZED}: chưa đăng nhập, token hết
 * hạn, và tài khoản bị khóa. Hai cái đầu có cùng cách xử lý — mời đăng nhập
 * lại. Cái thứ ba thì ngược hẳn: đăng nhập lại sẽ thất bại, nên mời họ thử là
 * đẩy người ta vào một vòng lặp không lối ra.
 *
 * <p>Mã máy đọc được là thứ frontend dựa vào, không phải câu chữ trong
 * {@code message} — xem {@code GlobalExceptionHandler} và
 * {@code AccountStatus.CODE}. Bắt frontend so khớp một chuỗi tiếng Việt để
 * quyết định đăng xuất là buộc phần bảo mật phụ thuộc vào một câu văn mà bất kỳ
 * ai cũng có thể sửa cho gọn hơn.
 *
 * <p>Ngoại lệ này chỉ dùng cho những đường đi qua Spring MVC — chủ yếu là đăng
 * nhập. Mọi request đã mang token thì bị chặn sớm hơn nhiều, ngay trong
 * {@code JwtAuthenticationFilter}, tức là trước khi có controller nào được gọi;
 * chỗ đó tự ghi thẳng response vì bộ xử lý ngoại lệ của MVC chưa tồn tại ở tầng
 * filter.
 */
public class AccountLockedException extends RuntimeException {

    /**
     * Mã máy đọc được, dùng chung cho mọi đường trả về trạng thái này.
     *
     * <p>Là hằng số chứ không phải chuỗi viết lặp ở từng chỗ: nó xuất hiện ở
     * tầng filter, ở bộ xử lý ngoại lệ, trong test và ở frontend. Gõ sai một
     * lần tại một trong số đó thì phần bị hỏng là phần đăng xuất người dùng bị
     * khóa — và nó hỏng im lặng.
     */
    public static final String CODE = "ACCOUNT_LOCKED";

    /** Câu nói với người dùng. Một chỗ định nghĩa, mọi đường dùng lại. */
    public static final String MESSAGE =
            "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";

    public AccountLockedException() {
        super(MESSAGE);
    }
}
