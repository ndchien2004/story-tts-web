package com.storytts.backend.dto.ai;

/**
 * Câu trả lời của trợ lý, kèm số lượt còn lại.
 *
 * <p>Số lượt đi cùng câu trả lời chứ không phải một lời gọi riêng: nó vừa đổi
 * xong vì chính lời gọi này, và bắt trình duyệt hỏi lại để biết con số mới là
 * thêm một vòng mạng cho một thứ máy chủ đang cầm sẵn trong tay.
 *
 * @param message        lời đáp, dạng văn bản thuần
 * @param remainingToday số lượt hỏi còn lại hôm nay; {@code null} là không giới hạn
 * @param truncated      chương dài quá nên phần đưa vào ngữ cảnh đã bị cắt bớt.
 *                       Giao diện nói điều này ra, vì một câu trả lời thiếu ý ở
 *                       giữa chương mà không ai báo trước là một câu trả lời sai
 *                       một cách âm thầm.
 */
public record AssistantReplyDto(
        String message,
        Integer remainingToday,
        boolean truncated
) {
}
