package com.storytts.backend.service.support;

import com.storytts.backend.domain.SupportSenderRole;

/**
 * Một phía vừa đẩy mốc đã đọc của mình lên, và giao dịch vừa commit.
 *
 * <h3>Vì sao việc này phải đi ra ngoài, tới hai nơi khác nhau</h3>
 * Một lần đánh dấu đã đọc đổi hai thứ ở hai màn hình khác nhau:
 *
 * <pre>
 *   những cửa sổ khác của chính người đọc → con số chưa đọc phải tụt xuống
 *   những cửa sổ của bên kia               → dấu "đã xem" phải hiện lên
 * </pre>
 *
 * Không có khung tin thứ nhất thì mở hai tab là hai con số khác nhau, và cái tab
 * không bấm sẽ giữ con số cũ cho tới lần tải trang kế tiếp — đúng lỗi mà
 * {@code NotificationsRead} sinh ra để tránh. Không có khung tin thứ hai thì
 * "đã xem" chỉ xuất hiện khi người gửi tình cờ tải lại trang.
 *
 * <h3>Chỉ mang những con số, không mang tin nhắn nào</h3>
 * Cả hai đầu đã có sẵn danh sách tin; thứ vừa đổi là hai con số. Gửi lại cả
 * luồng ở đây sẽ là một khung tin lớn cho mỗi lần ai đó cuộn xuống đáy — và
 * việc ấy xảy ra thường xuyên hơn hẳn việc gửi tin.
 *
 * @param reader            phía vừa đọc. Máy chủ tự xác định từ phiên đã xác
 *                          thực; không bao giờ đến từ trình duyệt.
 * @param lastReadMessageId mốc mới. Đây là thứ bên kia dùng để vẽ "đã xem" cho
 *                          mọi tin có id nhỏ hơn hoặc bằng nó — một con số cho
 *                          cả luồng thay vì một trạng thái lưu cho từng tin.
 * @param readerUnread      số chưa đọc còn lại của chính người đọc, theo máy
 *                          chủ. Bằng 0 trong hầu hết trường hợp, nhưng không
 *                          phải luôn luôn: một tin mới có thể vừa tới trong
 *                          cùng khoảnh khắc ấy, và trình duyệt không có cách nào
 *                          tự biết điều đó.
 */
public record SupportReadUpdated(
        Long conversationId,
        Long ownerUserId,
        SupportSenderRole reader,
        Long lastReadMessageId,
        long readerUnread
) {
}
