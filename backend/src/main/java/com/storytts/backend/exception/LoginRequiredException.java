package com.storytts.backend.exception;

/**
 * Thao tác này phải đăng nhập mới làm được → HTTP 401.
 *
 * <p>Dành cho những chỗ mà tầng URL cố ý để mở cho Khách, nhưng bên trong lại có
 * một nhánh chỉ dành cho người đã đăng nhập — ví dụ nút "Nghe bằng AI", nơi việc
 * bắt đăng nhập là một tùy chọn cấu hình chứ không phải một luật cứng.
 *
 * <p>Không dùng {@code CurrentUserService.requireCurrentUser()} cho việc này:
 * nó ném {@link ResourceNotFoundException} và ra HTTP 404, sai nghĩa.
 */
public class LoginRequiredException extends RuntimeException {

    public LoginRequiredException(String message) {
        super(message);
    }
}
