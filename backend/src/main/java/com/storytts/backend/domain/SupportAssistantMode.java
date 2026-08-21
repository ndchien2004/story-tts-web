package com.storytts.backend.domain;

/**
 * Ai đang nợ câu trả lời trong một luồng hỗ trợ: trợ lý AI, hay người thật.
 *
 * <h3>Vì sao đây không phải {@link SupportConversationStatus}</h3>
 * Hai enum trả lời hai câu khác nhau, và cả hai câu đều phải trả lời được cùng
 * lúc:
 *
 * <pre>
 *   status → người đọc có gửi được không, quản trị viên đã coi là xong chưa
 *   mode   → ai là bên trả lời
 * </pre>
 *
 * Một luồng {@code CLOSED} mà {@code mode = AI} là chuyện có thật và hợp lệ:
 * quản trị viên đã đóng hồ sơ, người đọc quay lại hỏi trợ lý một câu chung
 * chung. Gộp hai enum làm một sẽ mất đúng những tổ hợp như thế.
 *
 * <h3>Ba giá trị, và trạng thái thứ tư đã có sẵn tên khác</h3>
 * Đặc tả nói tới bốn trạng thái — {@code AI_ACTIVE}, {@code HANDOFF_REQUESTED},
 * {@code ADMIN_ACTIVE}, {@code RESOLVED}. Ba cái đầu là ba giá trị dưới đây.
 * Cái thứ tư đã tồn tại từ V15 dưới tên {@link SupportConversationStatus#CLOSED}
 * và không cần dựng lại: hai cột luôn phải bằng nhau là hai cột có thể lệch
 * nhau — cùng lập luận đã dùng ở {@code notifications} khi từ chối
 * {@code is_read}.
 *
 * <p>"Người đọc chưa chọn gì" cũng không có mặt ở đây, dù giao diện cần phân
 * biệt nó. Nó đọc được từ {@code lastMessageId == null}: luồng đã tạo, chưa ai
 * nói câu nào. Không có quy tắc nào ở phía máy chủ đổi theo nó, nên nó không
 * đáng một giá trị enum — xem {@link SupportConversation#awaitingFirstWord()}.
 *
 * <h3>Chiều đi của trạng thái</h3>
 * <pre>
 *   (luồng mới)  --người đọc chọn AI-->      AI
 *   (luồng mới)  --người đọc chọn người-->   HANDOFF
 *   AI           --xin gặp tư vấn viên-->    HANDOFF
 *   AI           --quản trị viên gửi tin-->  HUMAN
 *   HANDOFF      --quản trị viên nhận-->     HUMAN
 *   HUMAN/HANDOFF --người đọc chọn lại AI--> AI   (chỉ khi status = CLOSED)
 * </pre>
 *
 * Chiều {@code HUMAN → AI} là chiều duy nhất cần điều kiện, và điều kiện ấy là
 * bắt buộc: một cuộc trao đổi đang dở với người thật không được phép bị trợ lý
 * giành lấy sau lưng cả hai bên. Nhưng cấm hẳn cũng sai — luồng ở đây sống
 * suốt đời tài khoản, nên "đã từng gặp tư vấn viên một lần hồi tháng Giêng"
 * mà cấm vĩnh viễn thì tính năng này coi như không tồn tại với người ấy.
 * {@code CLOSED} là ranh giới đúng: hồ sơ cũ đã khép, và người đọc tự bấm chọn.
 */
public enum SupportAssistantMode {

    /**
     * Trợ lý AI đang phụ trách.
     *
     * <p>Đây là giá trị duy nhất mà trợ lý được phép trả lời, và cũng là giá
     * trị duy nhất mà tin nhắn của người đọc <b>không</b> bật huy hiệu đỏ của
     * quản trị viên. Cả tính năng tồn tại vì điều thứ hai.
     */
    AI,

    /**
     * Đã xin gặp người thật, chưa ai nhận.
     *
     * <p>Trợ lý im từ khoảnh khắc này — xem {@link #answeredByAssistant()}. Một
     * luồng ở đây <i>tự nó</i> đã là một việc đang chờ, nên nó bật huy hiệu mà
     * không cần đợi có tin chưa đọc: người bấm "Chat với tư vấn viên" rồi ngồi
     * chờ chưa gõ câu nào vẫn phải nhìn thấy được.
     */
    HANDOFF,

    /**
     * Một quản trị viên đã nhận luồng.
     *
     * <p>Mặc định của mọi hàng có từ trước V16, và đó là chủ ý: nó giữ nguyên
     * hành vi cũ tới từng chi tiết. Cũng là hàng rào cuối cho một đường ghi nào
     * đó quên đặt mode — hỏng về phía làm phiền người trực, không hỏng về phía
     * bỏ rơi người hỏi.
     */
    HUMAN;

    /** Trợ lý có được phép sinh câu trả lời tự động cho luồng này không. */
    public boolean answeredByAssistant() {
        return this == AI;
    }

    /**
     * Luồng này có thuộc về hàng đợi của quản trị viên không.
     *
     * <p>Một chỗ duy nhất cho câu hỏi ấy, vì nó được hỏi ở ba nơi: phép đếm chờ
     * trả lời, số chưa đọc từng dòng trong hộp thư, và số chưa đọc của một
     * luồng đang mở.
     */
    public boolean needsHumanAttention() {
        return this != AI;
    }
}
