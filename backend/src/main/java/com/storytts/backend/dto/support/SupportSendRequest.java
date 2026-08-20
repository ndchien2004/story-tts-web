package com.storytts.backend.dto.support;

import com.storytts.backend.domain.SupportMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân của một lượt gửi tin — đường REST và khung WebSocket dùng chung.
 *
 * <h3>Hai trường, và danh sách những gì <b>không</b> có mới là phần đáng đọc</h3>
 * Không có {@code senderId}. Không có {@code senderRole}. Không có
 * {@code conversationId} khi người gọi là người đọc. Không có {@code createdAt},
 * {@code status}, hay bất cứ thứ gì về trạng thái đã đọc.
 *
 * <p>Đó không phải là "chúng ta sẽ bỏ qua nếu trình duyệt gửi lên" — chúng
 * <i>không có chỗ để nhận</i>. Một khung tin kèm {@code "senderRole": "ADMIN"}
 * không bị từ chối, nó đơn giản không được đọc tới: Jackson bỏ trường lạ, và
 * không có dòng mã nào trong đường gửi tra tới nó. Cách chắc chắn nhất để không
 * tin một giá trị của trình duyệt là không có biến nào giữ nó.
 *
 * <p>Danh tính người gửi đến từ phiên đã xác thực, vai trò đến từ
 * {@code users.role} vừa đọc lại từ cơ sở dữ liệu, mốc thời gian đến từ đồng hồ
 * máy chủ, và luồng đến từ quyền sở hữu — xem {@code SupportMessageService}.
 *
 * @param clientMessageId định danh của <i>một lần bấm gửi</i>, do trình duyệt
 *                        sinh (UUID v4). Bắt buộc, và cố ý không có đường lui
 *                        "để trống thì máy chủ tự sinh": một cái do máy chủ sinh
 *                        sẽ khác nhau ở mỗi lần thử lại, tức là chống trùng im
 *                        lặng ngừng hoạt động đúng lúc nó cần nhất — khi đường
 *                        mạng đứt sau lúc ghi xong và trước lúc báo nhận về tới.
 * @param content         văn bản thuần. Trần {@link Size} ở đây là trần của lược
 *                        đồ, chỉ để chặn một thân request khổng lồ trước khi nó
 *                        đi xa hơn; trần thật của sản phẩm nằm ở cấu hình và
 *                        được kiểm ở tầng service, nơi con số ấy đọc được.
 */
public record SupportSendRequest(

        @NotBlank(message = "Thiếu định danh tin nhắn.")
        @Size(max = SupportMessage.CLIENT_ID_LIMIT, message = "Định danh tin nhắn không hợp lệ.")
        String clientMessageId,

        @NotBlank(message = "Nội dung tin nhắn không được để trống.")
        @Size(max = SupportMessage.CONTENT_LIMIT, message = "Tin nhắn quá dài.")
        String content
) {
}
