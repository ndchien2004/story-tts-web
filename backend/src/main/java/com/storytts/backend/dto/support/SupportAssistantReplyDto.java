package com.storytts.backend.dto.support;

/**
 * Kết quả một lượt hỏi trợ lý hỗ trợ.
 *
 * <h3>Vì sao câu hỏi cũng nằm trong câu trả lời</h3>
 * Vì máy chủ mới là nơi quyết định tin nhắn trông như thế nào: id của nó, mốc
 * thời gian của nó, và cả việc nó có phải một bản trùng của lần bấm trước hay
 * không. Trình duyệt vẽ một bong bóng lạc quan ngay lúc bấm, rồi thay nó bằng
 * {@link #message} khi câu trả lời về — cùng cơ chế với
 * {@link SupportSendResponse}, và cùng lý do.
 *
 * <h3>Vì sao {@link #reply} được phép null trong một lượt thành công</h3>
 * Ba cảnh, và cả ba đều không phải lỗi của người hỏi:
 *
 * <ul>
 *   <li>Gemini hỏng hoặc hết giờ chờ. Câu hỏi <i>vẫn được ghi</i> — đó là điều
 *       quan trọng nhất ở đây, vì nếu người đọc chuyển sang tư vấn viên ngay
 *       sau đó thì câu họ vừa gõ phải còn nguyên trong bản ghi.</li>
 *   <li>Người đọc bấm "Chat với tư vấn viên" trong lúc trợ lý đang viết. Câu
 *       trả lời lỗi thời bị bỏ — xem {@code SupportStore#appendAssistantReply}.</li>
 *   <li>Trợ lý trả về câu rỗng.</li>
 * </ul>
 *
 * Trong cả ba, {@link #notice} mang một câu tử tế để hiện lên, và
 * {@link #suggestHandoff} bật. Không có cảnh nào mà giao diện phải hiện một lỗi
 * kỹ thuật, và không có cảnh nào mà câu vừa gõ biến mất.
 *
 * @param message        câu hỏi như máy chủ đã ghi
 * @param reply          câu trả lời của trợ lý, hoặc null — xem trên
 * @param conversation   trạng thái luồng sau lượt này, đã tính lại số chưa đọc
 * @param duplicate      lần bấm gửi này đã được ghi từ trước; không có gì mới
 *                       được thêm vào
 * @param suggestHandoff nên làm nổi bật nút "Chat với tư vấn viên". Đây là một
 *                       <i>gợi ý</i>, không phải một lệnh: nút ấy vốn đã luôn
 *                       hiện, vì người đọc không bao giờ được kẹt lại với máy.
 * @param notice         câu giải thích cho người đọc khi không có câu trả lời.
 *                       Luôn là lời của chúng ta, không bao giờ là lỗi thô của
 *                       nhà cung cấp.
 * @param remaining      số lượt còn lại trong ngày; null khi không đặt hạn mức
 */
public record SupportAssistantReplyDto(
        SupportMessageDto message,
        SupportMessageDto reply,
        SupportConversationDto conversation,
        boolean duplicate,
        boolean suggestHandoff,
        String notice,
        Integer remaining
) {
}
