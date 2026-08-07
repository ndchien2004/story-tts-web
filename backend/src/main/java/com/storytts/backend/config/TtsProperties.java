package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Text-to-speech configuration.
 *
 * API keys are read from the environment or the .env file, never from source.
 *
 * @param providers provider ids in the order they should be attempted; the
 *                  first configured one that succeeds wins
 */
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
        boolean enabled,
        List<String> providers,
        Fptai fptai,
        ElevenLabs elevenlabs
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

    /**
     * @param voiceId a voice from the account, used when no specific voice is
     *                requested — for instance when this provider is standing in
     *                for one that failed
     * @param modelId must be a multilingual model for Vietnamese to be read
     *                correctly
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
