package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hàng rào của trợ lý AI trong hộp thư hỗ trợ.
 *
 * <h3>Vì sao là một tiền tố riêng, không phải một nhánh của {@code app.ai}</h3>
 * {@code app.ai.*} thuộc về trợ lý đọc truyện: hạn mức của nó tính theo số
 * chương đem đi tóm tắt, và trần ký tự của nó là trần của một chương. Hai con
 * số ấy không nói gì về một câu hỏi hỗ trợ dài hai dòng.
 *
 * <p>Quan trọng hơn là cái công tắc: {@code app.ai.enabled=false} phải tắt được
 * trợ lý đọc truyện mà <b>không</b> tắt đường hỗ trợ, và ngược lại. Chung một
 * khóa thì hai quyết định vận hành khác hẳn nhau bị buộc vào nhau.
 *
 * <p>Phần <i>kết nối</i> tới Gemini thì dùng chung, và cố ý dùng chung:
 * {@code app.ai.gemini.*} — khóa, endpoint, model, timeout — vẫn là nguồn duy
 * nhất, và {@link com.storytts.backend.service.ai.GeminiClient} vẫn là client
 * duy nhất. Một bộ khóa thứ hai cho cùng một nhà cung cấp là một chỗ để quên
 * xoay khóa.
 *
 * @param enabled          công tắc riêng. Tắt thì hộp thư hỗ trợ chạy y như
 *                         trước V16: người đọc nhắn thẳng cho tư vấn viên, và
 *                         bảng chọn "AI hay người thật" không hiện ra.
 * @param dailyQuota       số câu hỏi một người được gửi trong ngày
 * @param dailyQuotaVip    hạn mức của tài khoản VIP
 * @param dailyQuotaGlobal trần chung của cả hệ thống trong ngày — hàng rào chi
 *                         phí cuối cùng, và là thứ duy nhất chặn được một buổi
 *                         chiều bị dội request
 * @param maxHistoryTurns  số lượt hội thoại cũ đem theo làm ngữ cảnh. Đây vừa
 *                         là trần chi phí vừa là trần riêng tư: càng ít lượt cũ
 *                         thì càng ít thứ rời khỏi máy chủ.
 * @param maxReplyChars    trần độ dài câu trả lời, tính bằng ký tự sau khi đã
 *                         nhận về. Luôn nhỏ hơn
 *                         {@code SupportMessage.CONTENT_LIMIT}, vì một câu trả
 *                         lời quá khổ phải bị cắt chứ không được làm hỏng cả
 *                         lượt ghi.
 */
@ConfigurationProperties(prefix = "app.support.ai")
public record SupportAssistantProperties(
        boolean enabled,
        int dailyQuota,
        int dailyQuotaVip,
        int dailyQuotaGlobal,
        int maxHistoryTurns,
        int maxReplyChars
) {

    /** Cùng quy ước với {@code AiAssistantProperties}: -1 nghĩa là không chặn. */
    public static final int UNLIMITED = -1;

    /**
     * Mặc định cho mọi khóa không được đặt.
     *
     * <p>Cùng lối với {@code AiAssistantProperties}: một bản triển khai không
     * khai báo gì vẫn phải chạy được, và chạy với những con số có nghĩa chứ
     * không phải với số không — {@code dailyQuota = 0} sẽ là "trợ lý bật nhưng
     * từ chối mọi câu hỏi", trạng thái tệ nhất trong ba khả năng.
     */
    public SupportAssistantProperties {
        if (dailyQuota == 0 && dailyQuotaVip == 0 && dailyQuotaGlobal == 0) {
            // Rộng hơn hẳn hạn mức của trợ lý đọc truyện, và có lý do: một câu
            // hỏi hỗ trợ ngắn hơn một chương truyện đem đi tóm tắt tới hai bậc
            // độ lớn, còn cái giá của việc từ chối thì cao hơn — người bị từ
            // chối ở đây là người đang cần giúp.
            dailyQuota = 40;
            dailyQuotaVip = 80;
            dailyQuotaGlobal = 1_000;
        }
        maxHistoryTurns = maxHistoryTurns <= 0 ? 10 : maxHistoryTurns;
        maxReplyChars = maxReplyChars <= 0 ? 1_500 : maxReplyChars;
    }

    public int dailyQuotaFor(boolean vip) {
        return vip ? dailyQuotaVip : dailyQuota;
    }
}
