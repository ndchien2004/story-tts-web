package com.storytts.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * @param credential ID token do Google Identity Services trả cho trình duyệt
 */
public record GoogleLoginRequest(

        @NotBlank(message = "Thiếu thông tin đăng nhập từ Google")
        String credential
) {
}
