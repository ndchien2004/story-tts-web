package com.storytts.backend.dto.support;

/**
 * Trợ lý hỗ trợ có dùng được không, và người đang hỏi còn bao nhiêu lượt.
 *
 * <h3>Vì sao trạng thái này tách khỏi {@code AssistantStatusDto}</h3>
 * Hai trợ lý có hai công tắc và hai bộ đếm riêng — xem
 * {@code SupportAssistantProperties} và {@code AiUsageKind#SUPPORT}. Dùng chung
 * một DTO sẽ là một lời hứa rằng hai con số ấy luôn bằng nhau.
 *
 * @param enabled   trợ lý có được bật và có khóa Gemini không. Sai thì bảng
 *                  chọn "AI hay tư vấn viên?" không hiện ra, và hộp thư chạy y
 *                  như trước V16 — người đọc nhắn thẳng cho tư vấn viên.
 * @param dailyLimit hạn mức trong ngày của người này; null khi không đặt trần
 * @param remaining số lượt còn lại; null khi không đặt trần
 */
public record SupportAssistantStatusDto(
        boolean enabled,
        Integer dailyLimit,
        Integer remaining
) {

    /** Trợ lý tắt: giao diện chỉ vẽ đường tới tư vấn viên. */
    public static SupportAssistantStatusDto off() {
        return new SupportAssistantStatusDto(false, null, null);
    }
}
