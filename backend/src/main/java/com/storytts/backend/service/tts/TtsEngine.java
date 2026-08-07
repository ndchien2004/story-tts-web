package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.TtsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks a provider for each synthesis and falls back when one fails.
 *
 * Order comes from {@code app.tts.providers}. A provider whose key is missing
 * is skipped outright, so adding a fallback is purely a matter of filling in
 * its key. If the requested voice belongs to a particular provider that one is
 * tried first, since the reader picked it deliberately.
 */
@Service
@Slf4j
public class TtsEngine {

    private final Map<String, TtsProvider> providersById = new LinkedHashMap<>();
    private final TtsProperties properties;

    public TtsEngine(List<TtsProvider> providers, TtsProperties properties) {
        this.properties = properties;

        // Registered in configured order so iteration is the fallback order.
        for (String id : configuredOrder(properties, providers)) {
            providers.stream()
                    .filter(provider -> provider.id().equalsIgnoreCase(id))
                    .findFirst()
                    .ifPresent(provider -> providersById.put(provider.id(), provider));
        }

        log.info("TTS providers in fallback order: {}", providersById.keySet());
    }

    private static List<String> configuredOrder(TtsProperties properties, List<TtsProvider> providers) {
        List<String> configured = properties.providers();
        if (configured != null && !configured.isEmpty()) {
            return configured.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        }
        // No explicit order: fall back to whatever is on the classpath.
        return providers.stream().map(TtsProvider::id).toList();
    }

    /** Providers that are actually usable right now. */
    public List<TtsProvider> availableProviders() {
        return providersById.values().stream().filter(TtsProvider::isConfigured).toList();
    }

    /** Every voice the reader can choose from, across all configured providers. */
    public List<VoiceOptionDto> availableVoices() {
        List<VoiceOptionDto> voices = new ArrayList<>();
        for (TtsProvider provider : availableProviders()) {
            voices.addAll(provider.voices());
        }
        return voices;
    }

    public boolean hasAnyProvider() {
        return !availableProviders().isEmpty();
    }

    /**
     * Synthesises text, trying each configured provider until one succeeds.
     *
     * @throws TtsException if none succeed; the message names every attempt so
     *                      the reader is not left guessing which key to check
     */
    public SynthesisResult synthesize(String text, String voiceCode, int speed) {
        if (!properties.enabled()) {
            throw new TtsException("Chức năng đọc bằng AI đang tạm tắt trên máy chủ.");
        }

        List<TtsProvider> candidates = orderedCandidates(voiceCode);
        if (candidates.isEmpty()) {
            throw new TtsException(
                    "Chưa cấu hình nhà cung cấp giọng đọc nào. "
                            + "Vui lòng đặt FPT_TTS_API_KEY hoặc ELEVENLABS_API_KEY trong file .env.");
        }

        List<String> failures = new ArrayList<>();

        for (TtsProvider provider : candidates) {
            // Only the first choice gets the requested voice; a stand-in uses
            // its own default, since voice codes do not carry across vendors.
            String voiceForProvider = provider.supportsVoice(voiceCode) ? voiceCode : null;

            try {
                byte[] audio = provider.synthesize(text, voiceForProvider, speed);
                if (!failures.isEmpty()) {
                    log.info("TTS fell back to {} after {} failed attempt(s)",
                            provider.id(), failures.size());
                }
                return new SynthesisResult(audio, provider.id(), provider.displayName());
            } catch (TtsException ex) {
                log.warn("TTS provider {} failed: {}", provider.id(), ex.getMessage());
                failures.add("%s: %s".formatted(provider.displayName(), ex.getMessage()));
            } catch (Exception ex) {
                log.error("TTS provider {} failed unexpectedly", provider.id(), ex);
                failures.add("%s: lỗi không xác định".formatted(provider.displayName()));
            }
        }

        throw new TtsException(String.join(" | ", failures));
    }

    /**
     * Configured providers, with the owner of the requested voice moved to the
     * front so an explicit choice is honoured before the configured order.
     */
    private List<TtsProvider> orderedCandidates(String voiceCode) {
        List<TtsProvider> available = availableProviders();
        if (voiceCode == null || voiceCode.isBlank()) {
            return available;
        }

        List<TtsProvider> ordered = new ArrayList<>(available.size());
        available.stream().filter(provider -> provider.supportsVoice(voiceCode)).forEach(ordered::add);
        available.stream().filter(provider -> !provider.supportsVoice(voiceCode)).forEach(ordered::add);
        return ordered;
    }
}
