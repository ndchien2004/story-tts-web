package com.storytts.backend.service.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.TtsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FPT.AI text-to-speech, with Vietnamese voices.
 *
 * The vendor does not return audio inline. A request answers with a URL that
 * only becomes downloadable once generation finishes, so every synthesis is a
 * submit-then-poll cycle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FptAiTtsClient implements TtsProvider {

    public static final String ID = "fptai";

    /** Vendor limit per request, with headroom left for multi-byte characters. */
    private static final int MAX_CHARS_PER_REQUEST = 4500;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(3);

    private final TtsProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "FPT.AI";
    }

    @Override
    public boolean isConfigured() {
        TtsProperties.Fptai config = properties.fptai();
        return config != null && config.isConfigured();
    }

    @Override
    public List<VoiceOptionDto> voices() {
        if (!isConfigured()) {
            return List.of();
        }
        return Arrays.stream(FptVoice.values())
                .map(voice -> new VoiceOptionDto(
                        voice.getCode(), voice.getDisplayName(), voice.getGender(),
                        voice.getRegion(), ID, displayName()))
                .toList();
    }

    @Override
    public boolean supportsVoice(String voiceCode) {
        return FptVoice.fromCode(voiceCode).isPresent();
    }

    @Override
    public byte[] synthesize(String text, String voiceCode, int speed) {
        TtsProperties.Fptai config = properties.fptai();
        if (config == null || !config.isConfigured()) {
            throw new TtsException(
                    "Chưa cấu hình API key cho FPT.AI. "
                            + "Vui lòng đặt FPT_TTS_API_KEY trong file .env rồi khởi động lại máy chủ.");
        }

        String voice = FptVoice.fromCode(voiceCode)
                .or(() -> FptVoice.fromCode(config.defaultVoice()))
                .orElse(FptVoice.BANMAI)
                .getCode();

        List<String> chunks = TextChunker.split(text, MAX_CHARS_PER_REQUEST);
        log.info("FPT.AI: synthesising {} characters in {} request(s), voice={}, speed={}",
                text.length(), chunks.size(), voice, speed);

        // Submit every chunk first so the vendor can work on them in parallel,
        // then collect the results in order.
        List<String> downloadUrls = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            downloadUrls.add(submit(chunk, voice, speed, config));
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (String url : downloadUrls) {
            combined.writeBytes(downloadWhenReady(url));
        }

        byte[] audio = combined.toByteArray();
        if (audio.length == 0) {
            throw new TtsException("FPT.AI trả về file rỗng. Vui lòng thử lại.");
        }
        return audio;
    }

    /** Sends one chunk and returns the URL the audio will appear at. */
    private String submit(String text, String voice, int speed, TtsProperties.Fptai config) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("api-key", config.apiKey())
                .header("voice", voice)
                .header("speed", String.valueOf(speed))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TtsException("Quá trình tạo audio bị gián đoạn.", ex);
        } catch (Exception ex) {
            throw new TtsException("Không kết nối được tới FPT.AI. Vui lòng kiểm tra kết nối mạng.", ex);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new TtsException("API key của FPT.AI không hợp lệ hoặc đã hết hạn.");
        }
        if (status == 429) {
            throw new TtsException(describeRateLimit(response));
        }
        if (status < 200 || status >= 300) {
            throw new TtsException("FPT.AI trả về lỗi (HTTP %d).".formatted(status));
        }

        return extractDownloadUrl(response.body());
    }

    /**
     * Turns a 429 into a message that names the actual cause.
     *
     * The vendor throttles on two independent counters and reports both with
     * the same status code: a per-minute request rate, and the allowance of the
     * subscribed plan. They need different responses from the user — one is
     * worth retrying in a moment, the other is not — so the remaining-minute
     * header decides which of the two was hit.
     */
    private String describeRateLimit(HttpResponse<String> response) {
        Integer remainingThisMinute = response.headers()
                .firstValue("x-ratelimit-remaining-minute")
                .map(value -> {
                    try {
                        return Integer.valueOf(value.trim());
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .orElse(null);

        if (remainingThisMinute != null && remainingThisMinute <= 0) {
            return "Đang gọi FPT.AI quá nhanh. Vui lòng đợi khoảng một phút rồi thử lại.";
        }

        // Requests are still allowed this minute, so the plan allowance is what
        // ran out. Retrying will not help until the plan resets or is upgraded.
        String detail = extractProviderMessage(response.body());
        return "Tài khoản FPT.AI đã dùng hết hạn mức của gói hiện tại"
                + (detail == null ? "" : " (%s)".formatted(detail))
                + ". Thử lại ngay sẽ không có tác dụng — cần đợi gói làm mới hoặc nâng cấp gói.";
    }

    private String extractProviderMessage(String body) {
        try {
            String message = objectMapper.readTree(body).path("message").asText("");
            return message.isBlank() ? null : message;
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractDownloadUrl(String body) {
        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new TtsException("Không đọc được phản hồi từ FPT.AI.", ex);
        }

        // The vendor signals failures in the body with a non-zero `error`.
        int error = json.path("error").asInt(0);
        if (error != 0) {
            String message = json.path("message").asText("");
            throw new TtsException("FPT.AI báo lỗi: %s".formatted(
                    message.isBlank() ? "mã lỗi " + error : message));
        }

        String url = json.path("async").asText(null);
        if (url == null || url.isBlank()) {
            throw new TtsException("FPT.AI không trả về đường dẫn file audio.");
        }
        return url;
    }

    /** Polls the generated file until it exists, then returns its bytes. */
    private byte[] downloadWhenReady(String url) {
        long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            try {
                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200 && response.body().length > 0) {
                    return response.body();
                }
                // 404 simply means generation has not finished yet.
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TtsException("Quá trình tạo audio bị gián đoạn.", ex);
            } catch (Exception ex) {
                throw new TtsException("Không tải được file audio đã tạo.", ex);
            }
        }

        // The vendor accepts the request and hands back a URL even when the
        // account has no quota left; in that case the file never appears, so a
        // timeout here most often means the quota is exhausted.
        throw new TtsException(
                ("FPT.AI không trả về file sau %d phút. "
                        + "Nguyên nhân thường gặp là tài khoản đã hết lượt sử dụng — "
                        + "vui lòng kiểm tra hạn mức trong trang quản lý FPT.AI.")
                        .formatted(POLL_TIMEOUT.toMinutes()));
    }
}
