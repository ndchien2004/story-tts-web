package com.storytts.backend.domain;

/**
 * Loại của thứ đã gây ra một dòng sổ cái — nửa đầu của cặp
 * {@code (reference_type, reference_id)}.
 *
 * <p>Không dùng khóa ngoại cho cặp ấy, và đó là chủ ý: đích đến nằm ở bảng khác
 * nhau tùy giá trị này, mà một dòng sổ cái còn phải sống sót qua việc chương bị
 * xóa. Lịch sử tiền bạc không được biến mất theo nội dung mà nó đã trả tiền cho.
 */
public enum WalletReferenceType {

    /** Đơn thanh toán qua cổng — dòng {@code DEPOSIT} trỏ về đây. */
    PAYMENT_ORDER,

    /** Chương vừa được mở — dòng {@code PURCHASE_CHAPTER} trỏ về đây. */
    CHAPTER,

    /** Người quản trị đã thao tác — {@code reference_id} là id của người ấy. */
    ADMIN
}
