package com.storytts.backend.domain;

/**
 * Thứ mà một thông báo đang nói về, khi có một thứ như vậy.
 *
 * <h3>Cặp {@code (type, id)} và cố ý không có khóa ngoại</h3>
 * Cùng lối với {@link WalletReferenceType}: đích đến nằm ở bảng khác nhau tùy
 * giá trị này, nên không có một khóa ngoại nào diễn tả được nó. Và ở đây còn
 * một lý do nặng hơn: phân nửa số thông báo dùng cặp này nói về những thứ
 * <b>vừa bị xóa</b>. Một khóa ngoại có {@code ON DELETE CASCADE} sẽ xóa mất
 * đúng lời báo "chương của bạn đã bị gỡ" ngay khi chương bị gỡ; một khóa ngoại
 * không cascade sẽ chặn luôn lệnh xóa.
 *
 * <p>Nên cặp này là một <i>gợi ý</i>, không phải một lời hứa. Trình duyệt phải
 * chịu được việc bấm vào và không thấy gì — xem {@code notificationRoutes.js} và
 * cách trang đích xử lý 404.
 */
public enum NotificationEntityType {

    STORY,

    CHAPTER,

    /** Đơn thanh toán, cho các thông báo về nạp Xu và mua VIP. */
    PAYMENT_ORDER
}
