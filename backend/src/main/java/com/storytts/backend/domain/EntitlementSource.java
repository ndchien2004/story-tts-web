package com.storytts.backend.domain;

/**
 * Vì sao một người có quyền mở một chương.
 *
 * <p>Đây là lý do {@link ChapterEntitlement} tách khỏi sổ cái ví: quyền không
 * nhất thiết đến từ tiền. Quản trị viên mở một chương cho ai đó — bồi thường một
 * sự cố, tặng một người đọc — không nên phải giả vờ có một giao dịch Xu chưa từng
 * xảy ra chỉ để cấp được quyền ấy.
 *
 * <p>Combo chương và mua trọn bộ, nếu có sau này, chỉ là những giá trị nữa ở đây:
 * chúng sinh ra cùng loại dòng quyền, chỉ khác đường đi tới.
 */
public enum EntitlementSource {

    /** Người đọc tự trả Xu để mở. */
    COIN_PURCHASE("Đã mở bằng Xu"),

    /** Quản trị viên cấp tay, không tốn Xu của ai. */
    ADMIN_GRANT("Quản trị viên cấp");

    private final String label;

    EntitlementSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
