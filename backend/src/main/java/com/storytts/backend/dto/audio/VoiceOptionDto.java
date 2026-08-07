package com.storytts.backend.dto.audio;

/**
 * A selectable narration voice.
 *
 * @param code     value sent back when requesting synthesis
 * @param provider which backend owns this voice, so the engine can route to it
 */
public record VoiceOptionDto(
        String code,
        String name,
        String gender,
        String region,
        String provider,
        String providerName
) {
}
