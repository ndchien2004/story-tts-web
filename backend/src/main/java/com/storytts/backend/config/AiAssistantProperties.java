package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình trợ lý đọc truyện.
 *
 * <p>API key đọc từ biến môi trường hoặc file {@code .env}, không bao giờ nằm
 * trong mã nguồn — cùng một nếp với {@link TtsProperties}.
 *
 * <p>Gộp cả ngưỡng chi phí lẫn thông tin nhà cung cấp vào một chỗ, vì cả hai
 * đều nói về cùng một câu hỏi: một lần bấm "Hỏi AI" tốn bao nhiêu, và được phép
 * tốn bao nhiêu lần một ngày. Hạ một con số ở đây là siết lại được ngay, không
 * phải sửa code.
 *
 * @param enabled         tắt hẳn tính năng mà phần còn lại của web chạy bình thường
 * @param dailyQuota      số câu hỏi một thành viên được gửi trong ngày;
 *                        -1 là không giới hạn, 0 là chặn hẳn
 * @param dailyQuotaVip   hạn mức riêng cho VIP
 * @param dailyQuotaGlobal trần chung cho toàn bộ người đọc trong ngày — cầu dao cuối
 * @param maxChapterChars phần nội dung chương tối đa được đưa vào một lời gọi;
 *                        chương dài hơn thì bị cắt, xem {@code ChapterContext}
 * @param maxQuestionChars trần độ dài một câu hỏi
 * @param maxHistoryTurns số lượt hội thoại cũ được gửi kèm; 0 là hỏi đáp một lượt
 * @param gemini          thông tin gọi nhà cung cấp
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiAssistantProperties(
        boolean enabled,
        int dailyQuota,
        int dailyQuotaVip,
        int dailyQuotaGlobal,
        int maxChapterChars,
        int maxQuestionChars,
        int maxHistoryTurns,
        Gemini gemini
) {

    /** Hạn mức mang nghĩa "không giới hạn". */
    public static final int UNLIMITED = -1;

    /**
     * Thiếu cả khối {@code app.ai} vẫn phải ra một bộ ngưỡng dùng được.
     *
     * <p>Cùng lý lẽ với {@code TtsProperties}: thiếu vài dòng properties không
     * được phép làm hỏng lúc khởi động. Tính năng vẫn tự tắt khi chưa có API
     * key, nên một bộ mặc định "bật sẵn" ở đây không tiêu đồng nào.
     *
     * <p>Cả bốn con số cùng bằng 0 mới được coi là "khối này vắng mặt". Xét
     * từng con số một thì hỏng mất một nghĩa đang dùng: {@code dailyQuota=0} là
     * <i>chặn hẳn</i>, một lựa chọn hợp lệ, và nó không được phép lặng lẽ bị
     * đổi thành hai chục lượt.
     */
    public AiAssistantProperties {
        gemini = gemini == null ? Gemini.defaults() : gemini;

        if (dailyQuota == 0 && dailyQuotaVip == 0 && dailyQuotaGlobal == 0 && maxChapterChars == 0) {
            dailyQuota = 20;
            dailyQuotaVip = 50;
            dailyQuotaGlobal = 500;
        }

        maxChapterChars = maxChapterChars <= 0 ? 24_000 : maxChapterChars;
        maxQuestionChars = maxQuestionChars <= 0 ? 500 : maxQuestionChars;
        maxHistoryTurns = maxHistoryTurns < 0 ? 6 : maxHistoryTurns;
    }

    /** Hạn mức trong ngày của một người, tuỳ họ có VIP hay không. */
    public int dailyQuotaFor(boolean vip) {
        return vip ? dailyQuotaVip : dailyQuota;
    }

    /**
     * Google Gemini.
     *
     * @param endpoint        gốc của API, không kèm tên model — model là một
     *                        phần đường dẫn nên nó được ghép vào lúc gọi
     * @param apiKey          để trống thì tính năng tự tắt
     * @param model           định danh model, ví dụ {@code gemini-3.1-flash-lite}
     * @param timeoutSeconds  chờ tối đa một lời gọi; quá thì bỏ, không treo luồng
     * @param maxOutputTokens trần độ dài câu trả lời — vừa là trần chi phí, vừa
     *                        là thứ giữ cho câu trả lời còn là một lời đáp trong
     *                        hộp chat chứ không thành một bài viết
     */
    public record Gemini(
            String endpoint,
            String apiKey,
            String model,
            int timeoutSeconds,
            int maxOutputTokens
    ) {
        public static Gemini defaults() {
            return new Gemini("https://generativelanguage.googleapis.com/v1beta",
                    "", "gemini-3.1-flash-lite", 30, 1024);
        }

        /** Không có key thì không có tính năng — và đó là một trạng thái hợp lệ. */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank()
                    && endpoint != null && !endpoint.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
