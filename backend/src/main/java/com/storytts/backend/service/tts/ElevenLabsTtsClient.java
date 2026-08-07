package com.storytts.backend.service.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.TtsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ElevenLabs text-to-speech, used as a stand-in when the primary provider is
 * unavailable.
 *
 * Unlike FPT.AI this vendor returns the audio in the response body, so there is
 * no polling. Voices are account-specific rather than a fixed list, so they are
 * fetched from the API and cached; the configured voice id is the fallback when
 * that call is unavailable.
 *
 * Vietnamese needs one of the multilingual models — the monolingual ones read
 * the text as if it were English.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElevenLabsTtsClient implements TtsProvider {

    public static final String ID = "elevenlabs";

    /** Voice codes are prefixed so the engine can tell them from other vendors'. */
    private static final String VOICE_PREFIX = "el:";

    /** Comfortably inside the per-request limit of the multilingual models. */
    private static final int MAX_CHARS_PER_REQUEST = 4500;

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration VOICE_CACHE_TTL = Duration.ofMinutes(30);

    private final TtsProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Cached voice listing; the account's voices rarely change. */
    private final AtomicReference<CachedVoices> voiceCache = new AtomicReference<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "ElevenLabs";
    }

    @Override
    public boolean isConfigured() {
        TtsProperties.ElevenLabs config = properties.elevenlabs();
        return config != null && config.isConfigured();
    }

    @Override
    public boolean supportsVoice(String voiceCode) {
        return voiceCode != null && voiceCode.startsWith(VOICE_PREFIX);
    }

    @Override
    public List<VoiceOptionDto> voices() {
        if (!isConfigured()) {
            return List.of();
        }

        CachedVoices cached = voiceCache.get();
        if (cached != null && cached.isFresh()) {
            return cached.voices();
        }

        List<VoiceOptionDto> fetched;
        try {
            fetched = fetchVoices();
        } catch (Exception ex) {
            // Listing is a convenience; losing it must not stop synthesis, so
            // fall back to advertising just the configured default voice.
            log.warn("ElevenLabs: could not list voices ({}), using the configured default",
                    ex.getMessage());
            fetched = List.of(defaultVoiceOption());
        }

        voiceCache.set(new CachedVoices(fetched, Instant.now()));
        return fetched;
    }

    @Override
    public byte[] synthesize(String text, String voiceCode, int speed) {
        TtsProperties.ElevenLabs config = properties.elevenlabs();
        if (config == null || !config.isConfigured()) {
            throw new TtsException(
                    "Chưa cấu hình API key cho ElevenLabs. "
                            + "Vui lòng đặt ELEVENLABS_API_KEY trong file .env rồi khởi động lại máy chủ.");
        }

        String voiceId = resolveVoiceId(voiceCode, config);
        List<String> chunks = TextChunker.split(text, MAX_CHARS_PER_REQUEST);
        log.info("ElevenLabs: synthesising {} characters in {} request(s), voice={}, model={}",
                text.length(), chunks.size(), voiceId, config.modelId());

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (String chunk : chunks) {
            combined.writeBytes(requestSpeech(chunk, voiceId, config));
        }

        byte[] audio = combined.toByteArray();
        if (audio.length == 0) {
            throw new TtsException("ElevenLabs trả về file rỗng. Vui lòng thử lại.");
        }
        return audio;
    }

    /** Strips the prefix from a code of ours; anything else uses the default. */
    private String resolveVoiceId(String voiceCode, TtsProperties.ElevenLabs config) {
        if (voiceCode != null && voiceCode.startsWith(VOICE_PREFIX)) {
            String id = voiceCode.substring(VOICE_PREFIX.length()).trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        if (config.voiceId() == null || config.voiceId().isBlank()) {
            throw new TtsException(
                    "Chưa cấu hình giọng đọc cho ElevenLabs. "
                            + "Vui lòng đặt ELEVENLABS_VOICE_ID trong file .env.");
        }
        return config.voiceId();
    }

    private byte[] requestSpeech(String text, String voiceId, TtsProperties.ElevenLabs config) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        body.put("model_id", config.modelId());

        ObjectNode settings = body.putObject("voice_settings");
        settings.put("stability", 0.5);
        settings.put("similarity_boost", 0.75);

        String url = "%s/text-to-speech/%s?output_format=mp3_44100_128"
                .formatted(trimTrailingSlash(config.endpoint()),
                        URLEncoder.encode(voiceId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("xi-api-key", config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TtsException("Quá trình tạo audio bị gián đoạn.", ex);
        } catch (Exception ex) {
            throw new TtsException("Không kết nối được tới ElevenLabs. Vui lòng kiểm tra kết nối mạng.", ex);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new TtsException("API key của ElevenLabs không hợp lệ hoặc không đủ quyền.");
        }
        if (status == 429) {
            throw new TtsException(
                    "Tài khoản ElevenLabs đã hết hạn mức ký tự hoặc đang gọi quá nhanh. "
                            + "Vui lòng kiểm tra hạn mức trong trang quản lý ElevenLabs.");
        }
        if (status < 200 || status >= 300) {
            throw new TtsException("ElevenLabs trả về lỗi (HTTP %d)%s."
                    .formatted(status, describeError(response.body())));
        }

        byte[] audio = response.body();
        if (audio == null || audio.length == 0) {
            throw new TtsException("ElevenLabs trả về nội dung rỗng.");
        }
        return audio;
    }

    /** Error bodies arrive as JSON even though success is binary audio. */
    private String describeError(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode detail = json.path("detail");
            String message = detail.isTextual() ? detail.asText() : detail.path("message").asText("");
            return message.isBlank() ? "" : ": " + message;
        } catch (Exception ex) {
            return "";
        }
    }

    private List<VoiceOptionDto> fetchVoices() throws Exception {
        TtsProperties.ElevenLabs config = properties.elevenlabs();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(config.endpoint()) + "/voices"))
                .timeout(Duration.ofSeconds(20))
                .header("xi-api-key", config.apiKey())
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TtsException("HTTP %d".formatted(response.statusCode()));
        }

        JsonNode voices = objectMapper.readTree(response.body()).path("voices");
        List<VoiceOptionDto> result = new ArrayList<>();

        for (JsonNode voice : voices) {
            String voiceId = voice.path("voice_id").asText("");
            if (voiceId.isBlank()) {
                continue;
            }
            JsonNode labels = voice.path("labels");
            result.add(new VoiceOptionDto(
                    VOICE_PREFIX + voiceId,
                    voice.path("name").asText(voiceId),
                    labels.path("gender").asText("—"),
                    labels.path("accent").asText("Đa ngôn ngữ"),
                    ID,
                    displayName()));
        }

        return result.isEmpty() ? List.of(defaultVoiceOption()) : List.copyOf(result);
    }

    private VoiceOptionDto defaultVoiceOption() {
        String voiceId = properties.elevenlabs().voiceId();
        return new VoiceOptionDto(
                VOICE_PREFIX + voiceId, "Giọng mặc định", "—", "Đa ngôn ngữ", ID, displayName());
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private record CachedVoices(List<VoiceOptionDto> voices, Instant fetchedAt) {
        boolean isFresh() {
            return Duration.between(fetchedAt, Instant.now()).compareTo(VOICE_CACHE_TTL) < 0;
        }
    }
}
