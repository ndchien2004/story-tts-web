package com.storytts.backend.dto.notification;

/**
 * Con số trên cái chuông, và bao nhiêu hàng vừa đổi trạng thái.
 *
 * <p>Một đối tượng chứ không phải một số trần: {@code 3} nằm một mình trong thân
 * response là thứ không thêm được trường nào về sau mà không phá bên gọi cũ. Nó
 * cũng là câu trả lời của <i>mọi</i> đường đổi trạng thái đọc — đánh dấu một
 * cái, đánh dấu tất cả — nên trình duyệt luôn nhận lại con số đúng từ máy chủ
 * thay vì tự trừ đi một và hy vọng.
 *
 * @param unread số thông báo còn chưa đọc, tính ngay trong giao dịch vừa ghi
 * @param marked số thông báo lần gọi này vừa chuyển sang đã đọc; 0 nghĩa là lệnh
 *               rỗng — đã đọc từ trước, hoặc chỉ là một câu hỏi
 */
public record UnreadCountDto(long unread, int marked) {

    /** Chỉ hỏi, không đổi gì. */
    public static UnreadCountDto of(long unread) {
        return new UnreadCountDto(unread, 0);
    }
}
