package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tài khoản Admin được tạo tự động lần đầu chạy ứng dụng. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String username,
        String email,
        String password
) {
}
