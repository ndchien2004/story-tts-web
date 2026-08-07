package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Cho phép React (chạy port khác) gọi được REST API. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
