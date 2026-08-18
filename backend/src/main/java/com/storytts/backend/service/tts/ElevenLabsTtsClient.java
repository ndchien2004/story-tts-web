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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ElevenLabs text-to-speech — the narration backend.
 *
 * The vendor returns the audio in the response body, so there is no polling.
 * Voices are account-specific rather than a fixed list, so they are fetched from
 * the API and cached; the configured voice id is the fallback when that call is
 * unavailable.
 *
 * Vietnamese needs one of the multilingual models — the monolingual ones read
 * the text as if it were English.
 *
 * The `speed` argument is ignored: this API has no speed control, and the
 * listener already has one on the player itself.
 *
 * <h3>Mốc thời gian</h3>
 * Đường gọi mặc định ở đây là {@code /with-timestamps}: cùng một lần tổng hợp,
 * cùng một khoản tiền, nhưng trả về thêm mốc thời gian của từng ký tự. Đó là thứ
 * duy nhất khiến trang đọc tô sáng theo giọng đọc được — không có nó thì phía
 * trình duyệt chỉ còn cách chia đều thời lượng cho số chữ, và một câu có dấu
 * lặng ở giữa là đủ để phần tô sáng trôi khỏi chỗ đang đọc.
 *
 * <p>Nếu tài khoản hoặc phiên bản API không có đường ấy, lớp này tự lùi về đường
 * cũ và ghi nhớ điều đó cho những lần sau. Một bản audio không có mốc thời gian
 * vẫn là một bản audio nghe được; mất hẳn giọng đọc mới là hỏng.
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

    /**
     * Còn hỏi mốc thời gian nữa hay thôi.
     *
     * <p>Hạ xuống một lần rồi thì thôi hẳn cho tới lần khởi động sau: một tài
     * khoản không có đường ấy sẽ không tự có, và thử lại mỗi đoạn chỉ tổ thêm
     * một vòng gọi hỏng cho mỗi lần tổng hợp.
     */
    private final AtomicBoolean timestampsAvailable = new AtomicBoolean(true);

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
    public String defaultVoiceCode() {
        TtsProperties.ElevenLabs config = properties.elevenlabs();
        if (config == null || config.voiceId() == null || config.voiceId().isBlank()) {
            return null;
        }
        return VOICE_PREFIX + config.voiceId().trim();
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
            //
            // Không ghi vào cache: danh sách dự phòng chỉ có một mục, và đóng
            // băng nó nửa tiếng nghĩa là một lần gọi hỏng thoáng qua cũng đủ
            // giấu hết giọng của tài khoản trong suốt nửa tiếng ấy. Hỏng thì
            // lần sau hỏi lại.
            log.warn("ElevenLabs: could not list voices ({}), using the configured default",
                    ex.getMessage());
            VoiceOptionDto fallback = defaultVoiceOption();
            return fallback == null ? List.of() : List.of(fallback);
        }

        voiceCache.set(new CachedVoices(fetched, Instant.now()));
        return fetched;
    }

    @Override
    public ProviderSpeech synthesize(String text, String voiceCode, int speed) {
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
        List<WordAligner.Segment> segments = new ArrayList<>(chunks.size());

        // Con trỏ chỉ tiến: {@link TextChunker} cắt gọn hai đầu mỗi đoạn, nên đoạn
        // nhận về là một khúc con của chương chứ không phải bản sao nguyên vẹn —
        // và đây là chỗ duy nhất còn biết khúc ấy vốn nằm ở đâu.
        int cursor = 0;

        for (String chunk : chunks) {
            int chunkStart = text.indexOf(chunk, cursor);
            if (chunkStart < 0) {
                chunkStart = cursor;
            }
            cursor = chunkStart + chunk.length();

            ChunkSpeech spoken = requestSpeech(chunk, voiceId, config);
            combined.writeBytes(spoken.audio());

            WordAligner.Segment segment = toSegment(chunk, chunkStart, spoken.alignment());
            if (segment != null) {
                segments.add(segment);
            }
        }

        byte[] audio = combined.toByteArray();
        if (audio.length == 0) {
            throw new TtsException("ElevenLabs trả về file rỗng. Vui lòng thử lại.");
        }

        // Hoặc đủ cả, hoặc bỏ hết. Thiếu mốc thời gian của một đoạn giữa chương
        // thì mọi đoạn sau nó lệch đi đúng bằng độ dài đoạn thiếu, và một bản tô
        // sáng lệch nửa phút còn tệ hơn hẳn một bản không tô sáng gì.
        List<WordTimestamp> words = List.of();
        if (segments.size() == chunks.size()) {
            words = WordAligner.align(segments);
        } else if (!segments.isEmpty()) {
            log.warn("ElevenLabs: chỉ {}/{} đoạn có mốc thời gian, bỏ toàn bộ phần tô sáng",
                    segments.size(), chunks.size());
        }

        log.info("ElevenLabs: {} bytes, {} chữ có mốc thời gian", audio.length, words.size());
        return new ProviderSpeech(audio, words);
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

    /**
     * Một đoạn văn bản, đọc thành tiếng.
     *
     * <p>Thử đường có mốc thời gian trước; nếu tài khoản không có đường ấy thì
     * lùi về đường thường và nhớ luôn, để cả những đoạn sau của chính chương này
     * không phải thử lại.
     */
    private ChunkSpeech requestSpeech(String text, String voiceId, TtsProperties.ElevenLabs config) {
        if (timestampsAvailable.get()) {
            try {
                return requestWithTimestamps(text, voiceId, config);
            } catch (TimestampsUnavailableException ex) {
                log.info("ElevenLabs: {} — từ đây chỉ lấy tiếng, không lấy mốc thời gian",
                        ex.getMessage());
                timestampsAvailable.set(false);
            }
        }
        return new ChunkSpeech(requestPlainSpeech(text, voiceId, config), null);
    }

    /**
     * Đường có mốc thời gian: trả về JSON, audio nằm trong đó dưới dạng base64.
     *
     * @throws TimestampsUnavailableException khi chính đường này không tồn tại,
     *         tức là nên lùi về đường thường chứ không phải báo hỏng cho người dùng
     */
    private ChunkSpeech requestWithTimestamps(String text, String voiceId,
                                              TtsProperties.ElevenLabs config) {
        String url = "%s/text-to-speech/%s/with-timestamps?output_format=mp3_44100_128"
                .formatted(trimTrailingSlash(config.endpoint()),
                        URLEncoder.encode(voiceId, StandardCharsets.UTF_8));

        HttpResponse<byte[]> response = send(url, requestBody(text, config), config, "application/json");
        int status = response.statusCode();

        // Chỉ hai mã này mới có nghĩa là "không có đường ấy". Mọi mã lỗi khác là
        // hỏng thật (sai key, hết hạn mức), và lùi về đường thường chỉ để nhận
        // đúng cái lỗi ấy một lần nữa.
        if (status == 404 || status == 405) {
            throw new TimestampsUnavailableException(
                    "API không có /with-timestamps (HTTP %d)".formatted(status));
        }
        failOnError(response);

        JsonNode json;
        try {
            json = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new TtsException("ElevenLabs trả về nội dung không đọc được.", ex);
        }

        String encoded = json.path("audio_base64").asText("");
        if (encoded.isBlank()) {
            throw new TtsException("ElevenLabs trả về nội dung rỗng.");
        }

        byte[] audio;
        try {
            audio = Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new TtsException("ElevenLabs trả về audio không giải mã được.", ex);
        }

        JsonNode alignment = json.path("alignment");
        return new ChunkSpeech(audio, alignment.isObject() ? alignment : null);
    }

    /** Đường cũ: thân phản hồi chính là file MP3. */
    private byte[] requestPlainSpeech(String text, String voiceId, TtsProperties.ElevenLabs config) {
        String url = "%s/text-to-speech/%s?output_format=mp3_44100_128"
                .formatted(trimTrailingSlash(config.endpoint()),
                        URLEncoder.encode(voiceId, StandardCharsets.UTF_8));

        HttpResponse<byte[]> response = send(url, requestBody(text, config), config, "audio/mpeg");
        failOnError(response);

        byte[] audio = response.body();
        if (audio == null || audio.length == 0) {
            throw new TtsException("ElevenLabs trả về nội dung rỗng.");
        }
        return audio;
    }

    private ObjectNode requestBody(String text, TtsProperties.ElevenLabs config) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("text", text);
        body.put("model_id", config.modelId());

        ObjectNode settings = body.putObject("voice_settings");
        settings.put("stability", 0.5);
        settings.put("similarity_boost", 0.75);

        return body;
    }

    private HttpResponse<byte[]> send(String url, ObjectNode body,
                                      TtsProperties.ElevenLabs config, String accept) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("xi-api-key", config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TtsException("Quá trình tạo audio bị gián đoạn.", ex);
        } catch (Exception ex) {
            throw new TtsException("Không kết nối được tới ElevenLabs. Vui lòng kiểm tra kết nối mạng.", ex);
        }
    }

    /**
     * Dịch mã lỗi HTTP thành câu người dùng đọc được, hoặc để yên nếu là 2xx.
     *
     * <p>Luôn kèm câu giải thích của nhà cung cấp. ElevenLabs trả 401 cho nhiều
     * chuyện khác hẳn nhau — key sai, key thiếu quyền, tài khoản bị khóa vì nghi
     * dùng VPN — và chỉ phần {@code detail.message} mới phân biệt được. Gộp hết
     * thành "key không hợp lệ" là đẩy người đọc log đi thay key, trong khi cái
     * key ấy vẫn tốt nguyên.
     */
    private void failOnError(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new TtsException("ElevenLabs từ chối yêu cầu (HTTP %d)%s."
                    .formatted(status, describeError(response.body())));
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
    }

    /**
     * Đổi khối alignment của nhà cung cấp thành đầu vào của {@link WordAligner}.
     *
     * @return null khi đoạn này không có mốc thời gian dùng được
     */
    private WordAligner.Segment toSegment(String chunk, int chunkStart, JsonNode alignment) {
        if (alignment == null) {
            return null;
        }

        List<String> characters = readStrings(alignment.path("characters"));
        List<Double> starts = readDoubles(alignment.path("character_start_times_seconds"));
        List<Double> ends = readDoubles(alignment.path("character_end_times_seconds"));

        if (characters.isEmpty() || starts.isEmpty() || ends.isEmpty()) {
            return null;
        }
        return new WordAligner.Segment(chunk, chunkStart, characters, starts, ends);
    }

    private List<String> readStrings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            values.add(node.asText(""));
        }
        return values;
    }

    private List<Double> readDoubles(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            values.add(node.asDouble(0));
        }
        return values;
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

        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        VoiceOptionDto fallback = defaultVoiceOption();
        return fallback == null ? List.of() : List.of(fallback);
    }

    /**
     * Mục dự phòng cho danh sách giọng: chính là giọng cấu hình trong .env.
     *
     * <p>Tên nói rõ đây là giọng nào để người chọn không tưởng đây là "giọng nào
     * đó của máy chủ" — trước kia phần dựng lấy phần tử đầu của danh sách nhà
     * cung cấp làm mặc định, nên cái nhãn "mặc định" từng trỏ vào một giọng
     * không ai cấu hình cả.
     */
    private VoiceOptionDto defaultVoiceOption() {
        String code = defaultVoiceCode();
        if (code == null) {
            return null;
        }
        return new VoiceOptionDto(
                code, "Giọng mặc định (.env)", "—", "Đa ngôn ngữ", ID, displayName());
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    /** Tiếng của một đoạn, kèm khối alignment nếu lần gọi ấy có trả về. */
    private record ChunkSpeech(byte[] audio, JsonNode alignment) {
    }

    /** Không phải lỗi để báo lên trên: là dấu hiệu để lùi về đường gọi cũ. */
    private static final class TimestampsUnavailableException extends RuntimeException {
        TimestampsUnavailableException(String message) {
            super(message);
        }
    }

    private record CachedVoices(List<VoiceOptionDto> voices, Instant fetchedAt) {
        boolean isFresh() {
            return Duration.between(fetchedAt, Instant.now()).compareTo(VOICE_CACHE_TTL) < 0;
        }
    }
}
