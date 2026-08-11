package com.storytts.backend.domain;

/**
 * Vòng đời một đơn nâng cấp VIP. Tên trùng với trạng thái PayOS trả về, nên
 * việc đồng bộ chỉ là ánh xạ một-một chứ không phải dịch nghĩa.
 */
public enum VipOrderStatus {

    /** Đã tạo link thanh toán, đang chờ người dùng chuyển khoản. */
    PENDING,

    /** Đã nhận đủ tiền — đây là trạng thái duy nhất cộng hạn VIP. */
    PAID,

    /** Người dùng bấm hủy, hoặc Admin hủy đơn. */
    CANCELLED,

    /** Quá hạn thanh toán mà không có tiền về. */
    EXPIRED
}
