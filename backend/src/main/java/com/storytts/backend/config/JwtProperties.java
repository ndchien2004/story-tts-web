package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT, nạp từ application.properties / biến môi trường / file .env.
 * Không hardcode secret trong mã nguồn.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMs,
        String issuer
) {
}
