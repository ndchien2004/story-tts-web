package com.storytts.backend.service.support;

import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportInboxItemDto;
import com.storytts.backend.dto.support.SupportMessageDto;

/**
 * Một tin nhắn vừa được ghi và giao dịch vừa commit.
 *
 * <h3>Vì sao sự kiện chở sẵn DTO chứ không chở id</h3>
 * Vì bên nhận chạy ở {@code AFTER_COMMIT}, và ở thời điểm ấy giao dịch đã đóng
 * — kết nối cơ sở dữ liệu đã trả về pool (xem
 * {@code hibernate.connection.handling_mode} trong {@code application.properties}).
 * Một sự kiện chỉ mang id sẽ buộc bên nhận mở một giao dịch <i>mới</i> chỉ để
 * đọc lại thứ vừa ghi, tức là mỗi tin nhắn tốn thêm một kết nối và vài câu truy
 * vấn, và tất cả nằm trên đường đẩy tin thời gian thực — đúng chỗ không được
 * phép chậm.
 *
 * <p>Nên mọi thứ cần thiết được dựng <i>bên trong</i> giao dịch, lúc các thực
 * thể còn gắn với phiên làm việc, và sự kiện đi ra ngoài đã là dữ liệu thuần.
 * Cùng cách làm với {@code NotificationCreated}.
 *
 * <h3>Vì sao có hai dạng của cùng một tin nhắn</h3>
 * Người đọc và quản trị viên nhìn thấy danh tính người gửi khác nhau — xem
 * {@link SupportMessageDto}. Dựng cả hai ở đây, một lần, thay vì để tầng đẩy tin
 * phải quyết định: tầng ấy không có gì để dựng lại DTO từ nữa, và một phép chọn
 * sai ở đó là tên thật của quản trị viên đi thẳng tới người lạ.
 *
 * @param ownerUserId chủ luồng. Đây là khóa định tuyến: khung tin đi tới mọi
 *                    cửa sổ của <i>người này</i>, không tới cửa sổ của ai khác.
 * @param userState   trạng thái luồng nhìn từ phía người đọc, kèm số chưa đọc
 *                    của họ. Đi cùng tin nhắn chứ không để trình duyệt tự cộng
 *                    thêm một: một khung tin bị bỏ lỡ sẽ khiến phép cộng ở
 *                    trình duyệt lệch mãi mãi. Cùng lý lẽ với
 *                    {@code UserEventStream.NotificationFrame}.
 * @param adminState  cùng luồng ấy nhìn từ phía hỗ trợ, kèm chủ luồng và bản xem
 *                    trước — vừa đủ để một dòng trong hộp thư quản trị cập nhật
 *                    tại chỗ mà không phải tải lại cả danh sách.
 */
public record SupportMessageCreated(
        Long conversationId,
        Long ownerUserId,
        SupportMessageDto userView,
        SupportMessageDto adminView,
        SupportConversationDto userState,
        SupportInboxItemDto adminState
) {
}
