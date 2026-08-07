package com.storytts.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Cho phép đăng nhập bằng username hoặc email. */
public record LoginRequest(

        @NotBlank(message = "Vui lòng nhập tên đăng nhập hoặc email")
        String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password
) {
}
