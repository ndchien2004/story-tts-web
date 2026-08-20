package com.storytts.backend.service.notification;

import java.util.List;

/**
 * Một người vừa đánh dấu đã đọc — ở một trong những cửa sổ của họ.
 *
 * <h3>Vì sao việc này cũng phải đi ra ngoài</h3>
 * Thông báo thuộc về <i>người</i>, không thuộc về cái tab. Mở hai tab rồi bấm
 * vào một thông báo ở tab A thì tab B đang hiện đúng thông báo ấy dưới dạng
 * chưa đọc, và cái chuông của nó vẫn đếm nó. Không nói gì thì hai cửa sổ của
 * cùng một tài khoản mâu thuẫn nhau cho tới lần tải trang tiếp theo — và trên
 * điện thoại thì "lần tải trang tiếp theo" có thể là ngày mai.
 *
 * <p>Luồng SSE đã đánh khóa theo người, nên nó gửi được tin này tới mọi tab và
 * mọi thiết bị của cùng tài khoản mà không cần thêm gì. Đây chính là lý do khóa
 * là người chứ không phải là phiên.
 *
 * <p>Cùng quy tắc {@code AFTER_COMMIT} với {@link NotificationCreated}: một lệnh
 * đánh dấu bị cuộn ngược không được phép làm cái chuông ở máy khác tụt số.
 *
 * @param userId người vừa đọc
 * @param ids    những thông báo vừa chuyển trạng thái; rỗng nghĩa là
 *               <b>tất cả</b> — xem {@link #all}
 * @param unread số chưa đọc còn lại, tính trong chính giao dịch vừa ghi
 */
public record NotificationsRead(Long userId, List<Long> ids, long unread) {

    /** Một thông báo lẻ. */
    public static NotificationsRead one(Long userId, Long notificationId, long unread) {
        return new NotificationsRead(userId, List.of(notificationId), unread);
    }

    /**
     * "Đánh dấu tất cả".
     *
     * <p>Không liệt kê id, và đó là chủ ý: danh sách ấy có thể dài hàng nghìn
     * phần tử, mà bên nhận không cần từng cái — nó chỉ cần biết rằng từ giờ
     * không còn gì chưa đọc. {@code unread} bằng 0 nói đủ điều đó.
     */
    public static NotificationsRead all(Long userId) {
        return new NotificationsRead(userId, List.of(), 0L);
    }

    /** Lệnh này nói về cả hộp thư hay chỉ vài dòng. */
    public boolean everything() {
        return ids.isEmpty();
    }
}
