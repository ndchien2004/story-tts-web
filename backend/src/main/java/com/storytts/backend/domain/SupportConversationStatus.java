package com.storytts.backend.domain;

/**
 * Giai đoạn của một luồng hỗ trợ.
 *
 * <h3>Ba giá trị, và vì sao không nhiều hơn</h3>
 * Đặc tả gợi ý bốn (thêm {@code ARCHIVED}), nhưng một trạng thái chỉ đáng tồn
 * tại khi có một quy tắc nghiệp vụ đổi theo nó. Ba giá trị dưới đây mỗi cái đổi
 * đúng một quy tắc:
 *
 * <pre>
 *   OPEN    → cả hai bên gửi được
 *   CLOSED  → cả hai bên vẫn gửi được, và một tin mới sẽ mở lại
 *   BLOCKED → người đọc không gửi được nữa; quản trị viên thì được
 * </pre>
 *
 * {@code ARCHIVED} không đổi quy tắc nào — nó chỉ là một cách lọc danh sách, mà
 * việc ấy đã có {@code CLOSED}. Thêm nó vào là thêm một nhánh mà mọi phép kiểm
 * về sau phải nhớ tới, đổi lấy không gì cả.
 *
 * <h3>{@code CLOSED} không phải ngõ cụt, và đó là chủ ý</h3>
 * Một người đọc gửi tiếp vào luồng đã đóng thì luồng mở lại, trong <i>cùng</i>
 * giao dịch ghi tin ấy. Đây là câu trả lời cho cuộc đua "quản trị viên bấm đóng
 * đúng lúc người ta đang gõ": kết cục xác định là tin nhắn được giữ và luồng
 * quay lại {@code OPEN}, chứ không phải một câu bị nuốt mất mà người gửi không
 * biết.
 *
 * <p>Hệ quả là {@code CLOSED} <b>không</b> phải công cụ chặn spam. Công cụ ấy là
 * {@link #BLOCKED}, và nó là thứ duy nhất từ chối một lượt gửi.
 *
 * @see com.storytts.backend.service.support.SupportMessageStore
 */
public enum SupportConversationStatus {

    /** Đang trao đổi. Trạng thái của một luồng vừa được tạo. */
    OPEN,

    /** Quản trị viên coi là đã xong. Một tin mới của bất kỳ bên nào cũng mở lại. */
    CLOSED,

    /** Quản trị viên chặn: người đọc xem được lịch sử nhưng không gửi được nữa. */
    BLOCKED
}
