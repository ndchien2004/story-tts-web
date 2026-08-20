package com.storytts.backend.dto.support;

import com.storytts.backend.domain.SupportConversationStatus;

/**
 * Trạng thái hộp thư hỗ trợ của một người, đủ để vẽ cái bong bóng ở góc màn hình.
 *
 * <h3>Nhẹ có chủ đích</h3>
 * Bốn trường, không tin nhắn nào, và <b>không tạo gì cả</b>. Đường gọi ra nó
 * chạy trên mọi trang của mọi người đã đăng nhập, nên nó phải rẻ tới mức không
 * ai phải nghĩ về việc gọi nó — một câu tra theo khóa duy nhất, cộng một câu
 * đếm trên chỉ mục khi luồng đã tồn tại.
 *
 * @param exists        người này đã từng mở luồng hỗ trợ chưa. Sai nghĩa là mọi
 *                      trường còn lại đều rỗng, và giao diện vẽ một cái bong
 *                      bóng trơn không có huy hiệu.
 * @param unread        số tin của phía hỗ trợ mà người này chưa đọc
 * @param status        để hộp thoại biết có nên khóa ô soạn tin không, ngay ở
 *                      lần vẽ đầu tiên thay vì sau khi mở ra và gọi thêm một
 *                      lượt nữa
 * @param lastMessageId mốc mới nhất máy chủ biết. Trình duyệt so nó với tin cuối
 *                      nó đang giữ để biết mình có bỏ lỡ gì không — rẻ hơn hẳn
 *                      việc tải lại cả trang lịch sử để phát hiện ra là không.
 */
public record SupportSummaryDto(
        boolean exists,
        long unread,
        SupportConversationStatus status,
        Long lastMessageId
) {

    /** Chưa có luồng nào — hoặc người gọi là quản trị viên, vốn không có luồng riêng. */
    public static SupportSummaryDto none() {
        return new SupportSummaryDto(false, 0L, null, null);
    }
}
