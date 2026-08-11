package com.storytts.backend.dto.auth;

/**
 * Kết quả của bước đăng ký đầu tiên.
 *
 * <p>Đăng ký có thể kết thúc ở hai chỗ khác nhau, nên câu trả lời phải nói rõ
 * là chỗ nào. Bình thường máy chủ gửi mã về email và chưa tạo tài khoản, lúc đó
 * {@code session} còn trống và client chuyển sang màn hình nhập mã. Khi máy chủ
 * chưa cấu hình email thì không có đường nào gửi mã đi, tài khoản được tạo ngay
 * và {@code session} chính là phiên đăng nhập.
 *
 * @param expiresInMinutes hạn dùng của mã, null khi không phải xác thực
 */
public record RegisterResponse(
        boolean verificationRequired,
        String email,
        Integer expiresInMinutes,
        AuthResponse session
) {

    public static RegisterResponse awaitingCode(String email, int expiresInMinutes) {
        return new RegisterResponse(true, email, expiresInMinutes, null);
    }

    public static RegisterResponse completed(AuthResponse session) {
        return new RegisterResponse(false, session.user().email(), null, session);
    }
}
