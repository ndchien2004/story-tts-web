package com.storytts.backend.service.tts;

import com.storytts.backend.dto.audio.VoiceOptionDto;

import java.util.List;

/**
 * One text-to-speech backend.
 *
 * Implementations hide whatever protocol their vendor uses — one answers with
 * audio directly, another hands back a URL that has to be polled — so callers
 * only ever deal with finished MP3 bytes.
 */
public interface TtsProvider {

    /** Stable identifier used in configuration and stored alongside the audio. */
    String id();

    /** Human-readable name, shown to the reader. */
    String displayName();

    /** False when the API key is missing, in which case the provider is skipped. */
    boolean isConfigured();

    /** Voices this provider can narrate with. Empty when it is not configured. */
    List<VoiceOptionDto> voices();

    /** True when {@code voiceCode} belongs to this provider. */
    boolean supportsVoice(String voiceCode);

    /**
     * Converts text to MP3 bytes.
     *
     * @param voiceCode a code from {@link #voices()}, or null for the default
     * @param speed     -3 (slowest) to 3 (fastest); providers without a speed
     *                  control ignore it
     * @throws com.storytts.backend.exception.TtsException on any vendor failure
     */
    byte[] synthesize(String text, String voiceCode, int speed);
}
