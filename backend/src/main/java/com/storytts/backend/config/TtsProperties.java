package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình dịch vụ Text-to-Speech (mục 4.5 đề bài).
 * API key đọc từ biến môi trường / file .env, không nằm trong Git (mục 5 đề bài).
 */
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
        boolean enabled,
        Fptai fptai
) {

    public record Fptai(
            String endpoint,
            String apiKey,
            String defaultVoice,
            int defaultSpeed
    ) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
