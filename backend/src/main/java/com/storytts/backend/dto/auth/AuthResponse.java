package com.storytts.backend.dto.auth;

/**
 * Kết quả đăng nhập/đăng ký: React lưu {@code token} rồi đính vào header
 * {@code Authorization: Bearer <token>} ở mỗi request tiếp theo.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        UserDto user
) {

    public static AuthResponse of(String token, long expiresInMs, UserDto user) {
        return new AuthResponse(token, "Bearer", expiresInMs, user);
    }
}
