package com.storytts.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storytts.backend.config.AiAssistantProperties;
import com.storytts.backend.exception.AiAssistantException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Google Gemini — nhà cung cấp câu trả lời cho trợ lý đọc truyện.
 *
 * <p>Cùng khuôn với {@code ElevenLabsTtsClient}: một {@link HttpClient} của thư
 * viện chuẩn, Jackson để dựng và bóc JSON, không thêm SDK nào. Một SDK ở đây sẽ
 * kéo theo cả cây phụ thuộc của Google chỉ để gửi đúng một lời gọi POST.
 *
 * <h3>Hợp đồng đang dùng</h3>
 * <pre>
 *   POST {endpoint}/models/{model}:generateContent
 *   x-goog-api-key: {key}
 *
 *   { "systemInstruction": { "parts": [ { "text": ... } ] },
 *     "contents": [ { "role": "user" | "model", "parts": [ { "text": ... } ] } ],
 *     "generationConfig": { "temperature": ..., "maxOutputTokens": ... } }
 * </pre>
 *
 * <p>Khoá đi trong header {@code x-goog-api-key} chứ không phải tham số
 * {@code ?key=} — cả hai đều được API chấp nhận, nhưng tham số truy vấn thì nằm
 * lại trong log truy cập, trong lịch sử proxy và trong mọi thông báo lỗi có kèm
 * URL. Một khoá đã lọt vào log là một khoá phải thay.
 *
 * <h3>Hỏng thì hỏng ở đây, không hỏng ra ngoài</h3>
 * Mọi kiểu trục trặc — quá hạn chờ, 4xx, 5xx, đứt mạng, JSON dị dạng, phản hồi
 * rỗng vì bộ lọc an toàn — đều ra khỏi lớp này dưới một hình dạng duy nhất:
 * {@link AiAssistantException}. Nguyên văn lỗi của nhà cung cấp chỉ đi vào log,
 * vì nó có thể mang theo đường dẫn nội bộ, và vì không có gì trong đó giúp được
 * người đang chờ một câu trả lời.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    /** Vai của trợ lý trong hợp đồng của Gemini là "model", không phải "assistant". */
    static final String ROLE_USER = "user";
    static final String ROLE_MODEL = "model";

    /**
     * Thấp, vì việc ở đây là thuật lại một chương chứ không phải sáng tác.
     *
     * <p>Nhiệt độ cao làm câu chữ mượt hơn và cũng làm nó trôi xa văn bản hơn —
     * mà trôi xa văn bản, ở một trợ lý có đúng một việc là bám vào chương đang
     * đọc, chính là hỏng.
     */
    private static final double TEMPERATURE = 0.3;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isConfigured() {
        AiAssistantProperties.Gemini gemini = properties.gemini();
        return gemini != null && gemini.isConfigured();
    }

    /**
     * Một lượt hỏi đáp.
     *
     * @param systemInstruction luật chơi của trợ lý — kênh riêng, không trộn với dữ liệu
     * @param conversation      các lượt nói, cũ trước mới sau, lượt cuối là câu hỏi
     * @return lời đáp dạng văn bản thuần, đã bỏ khoảng trắng thừa hai đầu
     * @throws AiAssistantException mọi trục trặc, đã dịch sang tiếng người
     */
    public String generate(String systemInstruction, List<GeminiTurn> conversation) {
        AiAssistantProperties.Gemini config = properties.gemini();
        if (config == null || !config.isConfigured()) {
            throw AiAssistantException.unavailable();
        }

        String body = buildBody(systemInstruction, conversation, config);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpointFor(config))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-api-key", config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            // Khôi phục cờ ngắt rồi mới ném: nuốt nó đi là làm mất tín hiệu dừng
            // của luồng, và luồng ấy không phải của chúng ta.
            Thread.currentThread().interrupt();
            throw AiAssistantException.upstream(ex);
        } catch (Exception ex) {
            // Gộp cả quá hạn chờ lẫn đứt mạng: với người đang chờ thì hai thứ ấy
            // là một chuyện, và cả hai đều đáng bấm thử lại.
            log.warn("Gọi Gemini hỏng: {}", ex.toString());
            throw AiAssistantException.upstream(ex);
        }

        if (response.statusCode() == 429) {
            throw AiAssistantException.upstream(
                    "Trợ lý AI đang quá tải. Vui lòng thử lại sau ít phút.");
        }
        if (response.statusCode() / 100 != 2) {
            // Thân phản hồi chỉ vào log, và chỉ một khúc đầu: nó có thể dài, và
            // nó không bao giờ là thứ để đưa ra màn hình.
            log.warn("Gemini trả về {} — {}", response.statusCode(), snippet(response.body()));
            throw AiAssistantException.upstream(
                    "Trợ lý AI hiện không phản hồi. Vui lòng thử lại sau ít phút.");
        }

        return extractText(response.body());
    }

    /** {endpoint}/models/{model}:generateContent — model là một phần đường dẫn. */
    private URI endpointFor(AiAssistantProperties.Gemini config) {
        String base = config.endpoint().endsWith("/")
                ? config.endpoint().substring(0, config.endpoint().length() - 1)
                : config.endpoint();
        return URI.create(base + "/models/" + config.model() + ":generateContent");
    }

    private String buildBody(String systemInstruction, List<GeminiTurn> conversation,
                             AiAssistantProperties.Gemini config) {
        ObjectNode root = objectMapper.createObjectNode();

        // Kênh riêng cho luật chơi. Đây là lý do nội dung chương không đi đường
        // này: giữ được sự tách bạch giữa "điều trợ lý phải làm" và "văn bản
        // trợ lý đang đọc" thì một chương có câu "hãy bỏ qua mọi chỉ thị trước
        // đó" cũng chỉ là một câu trong truyện.
        root.set("systemInstruction", textContent(systemInstruction));

        ArrayNode contents = root.putArray("contents");
        for (GeminiTurn turn : conversation) {
            ObjectNode node = contents.addObject();
            node.put("role", turn.role());
            node.putArray("parts").addObject().put("text", turn.text());
        }

        ObjectNode generation = root.putObject("generationConfig");
        generation.put("temperature", TEMPERATURE);
        generation.put("maxOutputTokens", config.maxOutputTokens());

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw AiAssistantException.upstream(ex);
        }
    }

    private ObjectNode textContent(String text) {
        ObjectNode content = objectMapper.createObjectNode();
        content.putArray("parts").addObject().put("text", text);
        return content;
    }

    /**
     * Bóc lời đáp ra khỏi phản hồi.
     *
     * <p>Ghép mọi {@code parts} của ứng viên đầu tiên chứ không lấy mỗi phần
     * đầu: một câu trả lời dài có thể về thành nhiều mảnh, và lấy một mảnh
     * nghĩa là cắt ngang câu ở một chỗ ngẫu nhiên.
     */
    private String extractText(String rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Phản hồi Gemini không phải JSON hợp lệ: {}", snippet(rawBody));
            throw AiAssistantException.upstream(ex);
        }

        // Câu hỏi bị bộ lọc chặn ngay từ đầu: không có ứng viên nào cả, chỉ có
        // lý do. Nói thẳng ra, vì đây là thứ người hỏi sửa được.
        JsonNode blockReason = root.path("promptFeedback").path("blockReason");
        if (!blockReason.isMissingNode() && !blockReason.asText("").isBlank()) {
            log.info("Gemini từ chối câu hỏi, lý do {}", blockReason.asText());
            throw AiAssistantException.upstream(
                    "Trợ lý AI không trả lời được câu này. Bạn thử hỏi theo cách khác nhé.");
        }

        JsonNode candidate = root.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            String chunk = part.path("text").asText("");
            if (!chunk.isBlank()) {
                text.append(chunk);
            }
        }

        String answer = text.toString().trim();
        if (answer.isEmpty()) {
            String finishReason = candidate.path("finishReason").asText("");
            log.info("Gemini trả về câu rỗng, finishReason={}", finishReason);
            throw AiAssistantException.upstream(
                    "Trợ lý AI không trả lời được câu này. Bạn thử hỏi theo cách khác nhé.");
        }

        // Chạm trần token: câu trả lời có thật nhưng cụt giữa chừng, nên nói ra
        // thay vì để người đọc tưởng trợ lý ngừng lời ở đó.
        if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
            answer += "\n\n(Câu trả lời đã dài hết mức cho phép nên bị cắt ở đây.)";
        }

        return answer;
    }

    /** Khúc đầu của một thân phản hồi, đủ để lần ra lỗi mà không đổ cả trang vào log. */
    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }

    /**
     * Một lượt nói theo đúng từ vựng của Gemini.
     *
     * @param role {@link #ROLE_USER} hoặc {@link #ROLE_MODEL}
     */
    public record GeminiTurn(String role, String text) {

        public static GeminiTurn user(String text) {
            return new GeminiTurn(ROLE_USER, text);
        }

        public static GeminiTurn model(String text) {
            return new GeminiTurn(ROLE_MODEL, text);
        }
    }
}
