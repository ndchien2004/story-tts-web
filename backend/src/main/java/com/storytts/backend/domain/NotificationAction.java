package com.storytts.backend.domain;

/**
 * Chỗ nên đi tiếp sau khi đọc một thông báo — nói bằng ý định, không bằng đường dẫn.
 *
 * <h3>Vì sao backend không lưu URL</h3>
 * Đường dẫn của trang đọc là chuyện của trang đọc: {@code /tai-khoan?tab=wallet}
 * hôm nay có thể thành một trang riêng ngày mai. Lưu URL vào cơ sở dữ liệu là
 * đóng băng một quyết định của giao diện vào những hàng không bao giờ được sửa
 * lại — đổi đường dẫn một lần là mọi thông báo cũ trỏ vào hư không.
 *
 * <p>Nên thứ được lưu là <i>ý định</i>, và việc đổi ý định thành đường dẫn xảy
 * ra đúng một lần, ở trình duyệt, tại {@code notificationRoutes.js}. Đó cũng là
 * hàng rào an toàn: không có chuỗi URL nào từ cơ sở dữ liệu đi thẳng vào thuộc
 * tính {@code href}, nên không có đường cho một {@code javascript:} lọt vào.
 *
 * <p>Nút thứ hai (khi có) không nằm ở đây mà suy ra từ {@code relatedEntityType}
 * cộng {@code relatedEntityId} — xem {@link Notification}. Một thông báo "chương
 * đã bị gỡ" vì thế mời được người đọc đi hai nơi: xem lại sổ Xu, và quay về
 * danh sách chương của truyện vẫn còn sống.
 */
public enum NotificationAction {

    /** Sổ Xu của chính người nhận — nơi con số hoàn tiền là con số thật. */
    VIEW_REFUND_HISTORY,

    /** Ví Xu: số dư và các khoản đã vào ra. */
    VIEW_WALLET,

    /** Đơn đã mua. */
    VIEW_ORDERS,

    /** Trang truyện, cũng chính là danh sách chương. */
    VIEW_STORY,

    /** Một chương cụ thể. */
    VIEW_CHAPTER,

    /** Trang quyền lợi và hạn VIP. */
    VIEW_VIP
}
