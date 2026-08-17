package com.storytts.backend.domain;

/**
 * Thứ mà một đơn thanh toán đang mua.
 *
 * <p>Giá trị này quyết định việc gì xảy ra khi tiền về: cộng hạn VIP, hay cộng Xu
 * vào ví. Đó là toàn bộ khác biệt giữa hai loại đơn — phần tạo link, đối chiếu
 * chữ ký, hỏi lại cổng và chống cộng hai lần đều dùng chung.
 */
public enum PaymentOrderKind {

    /** Mua một gói VIP: cộng thêm số tháng vào hạn VIP. */
    VIP_PLAN("Gói VIP"),

    /** Nạp Xu: cộng Xu vào ví. */
    COIN_PACKAGE("Gói Xu");

    private final String label;

    PaymentOrderKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
