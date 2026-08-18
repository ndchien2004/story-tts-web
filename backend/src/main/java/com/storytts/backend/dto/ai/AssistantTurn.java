package com.storytts.backend.dto.ai;

/**
 * Một lượt trong hội thoại đã diễn ra, do trình duyệt gửi kèm câu hỏi mới.
 *
 * <p><b>Vì sao lịch sử đến từ client.</b> Không có bảng nào lưu nó, và phiên
 * bản đầu tiên cố ý không tạo bảng ấy — hội thoại chỉ sống trong lúc đọc một
 * chương, hết chương là hết chuyện. Máy chủ vì thế không có trí nhớ, và bên duy
 * nhất còn nhớ là trình duyệt.
 *
 * <p><b>Và vì sao điều đó không mở ra lỗ hổng nào.</b> Một người tự sửa lịch sử
 * hội thoại của chính mình chỉ làm hỏng câu trả lời của chính họ — thứ đáng giữ
 * ở đây là nội dung chương, mà nội dung chương không bao giờ đi qua đường này:
 * máy chủ tự đọc nó từ cơ sở dữ liệu sau khi xét quyền. Cái duy nhất phải chặn
 * là kích thước, vì mỗi ký tự ở đây là một ký tự phải trả tiền —
 * {@code StoryAssistantService} cắt cả số lượt lẫn độ dài từng lượt.
 *
 * @param role    {@code "user"} hoặc {@code "assistant"}; giá trị lạ bị bỏ qua
 * @param content lời của lượt ấy
 */
public record AssistantTurn(String role, String content) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public boolean isUser() {
        return ROLE_USER.equalsIgnoreCase(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equalsIgnoreCase(role);
    }

    /** Lượt rỗng hoặc mang vai lạ thì không có gì để gửi đi. */
    public boolean isUsable() {
        return content != null && !content.isBlank() && (isUser() || isAssistant());
    }
}
