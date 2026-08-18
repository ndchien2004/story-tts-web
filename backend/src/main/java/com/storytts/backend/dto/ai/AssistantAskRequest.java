package com.storytts.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Một câu hỏi gửi cho trợ lý đọc truyện.
 *
 * <p><b>Chỉ có {@code chapterId}, không có nội dung chương.</b> Đó là điều quan
 * trọng nhất về hình dạng này. Nếu trình duyệt được phép gửi kèm nội dung
 * chương, thì bất kỳ ai cũng có thể dán nội dung một chương VIP chưa mua vào
 * đây — hoặc dán bất cứ thứ gì khác — và máy chủ sẽ ngoan ngoãn trả tiền để
 * Gemini đọc nó. Máy chủ tự tra chương từ cơ sở dữ liệu sau khi xét quyền, nên
 * cửa quyền đọc chương cũng chính là cửa quyền hỏi AI.
 *
 * @param chapterId chương đang đọc
 * @param message   câu hỏi của người đọc
 * @param history   các lượt đã trao đổi trong cùng chương, cũ trước mới sau;
 *                  bỏ trống thì thành hỏi đáp một lượt. Xem {@link AssistantTurn}.
 */
public record AssistantAskRequest(
        @NotNull(message = "Thiếu chương đang đọc.")
        Long chapterId,

        @NotBlank(message = "Bạn chưa nhập câu hỏi.")
        String message,

        List<AssistantTurn> history
) {

    /** Không gửi lịch sử cũng hợp lệ, và là trường hợp của câu hỏi đầu tiên. */
    public AssistantAskRequest {
        history = history == null ? List.of() : history;
    }
}
