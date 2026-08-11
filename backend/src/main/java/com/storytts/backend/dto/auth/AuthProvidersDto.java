package com.storytts.backend.dto.auth;

/**
 * Những cách đăng nhập mà máy chủ này đang bật.
 *
 * <p>Frontend hỏi trước khi dựng trang để không hiện một nút chỉ báo lỗi khi
 * bấm. Client ID cũng trả về ở đây thay vì khai lại trong cấu hình frontend,
 * nhờ vậy chỉ có đúng một nơi khai báo ứng dụng Google.
 */
public record AuthProvidersDto(
        boolean googleEnabled,
        String googleClientId,
        boolean passwordResetEnabled
) {
}
