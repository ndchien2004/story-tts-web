package com.storytts.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "Tên đăng nhập chỉ gồm chữ, số và các ký tự . _ -")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
        String password,

        @Size(max = 100)
        String displayName
) {
}
