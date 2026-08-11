package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình chuyển văn bản thành giọng nói.
 *
 * <p>API key đọc từ biến môi trường hoặc file .env, không bao giờ nằm trong mã nguồn.
 *
 * @param providers    id các nhà cung cấp theo thứ tự sẽ thử; cái nào chưa có
 *                     key thì tự động bị bỏ qua
 * @param defaultSpeed tốc độ dùng khi lời gọi không nêu, đồng thời là một phần
 *                     khóa cache của bản audio
 */
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
        boolean enabled,
        List<String> providers,
        int defaultSpeed,
        ElevenLabs elevenlabs
) {

    /**
     * @param voiceId giọng lấy từ tài khoản, dùng khi lời gọi không chỉ định giọng nào
     * @param modelId bắt buộc là model đa ngôn ngữ thì tiếng Việt mới đọc đúng
     */
    public record ElevenLabs(
            String endpoint,
            String apiKey,
            String voiceId,
            String modelId
    ) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
