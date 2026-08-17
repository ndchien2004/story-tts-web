package com.storytts.backend.service;

/**
 * Kết quả của một lần xét quyền đọc chương — và <i>vì sao</i>.
 *
 * <p>Trả về lý do chứ không chỉ true/false, vì mỗi lý do dẫn tới một màn hình
 * khác nhau: mời đăng nhập, mời nâng cấp VIP, hay mời mở khóa bằng Xu. Một giá
 * trị boolean buộc tầng trên phải tự đoán lại điều mà lớp xét quyền vừa biết rõ.
 */
public enum ChapterAccessDecision {

    /** Quản trị viên xem được mọi chương để còn kiểm duyệt nội dung. */
    ALLOWED_ADMIN(true),

    /** Chương công khai. */
    ALLOWED_FREE(true),

    /** Chương chỉ cần đăng nhập, và người này đã đăng nhập. */
    ALLOWED_MEMBER(true),

    /** VIP còn hiệu lực — VIP đọc được mọi thứ, kể cả chương có giá Xu. */
    ALLOWED_VIP(true),

    /** Đã mở khóa chương này từ trước. */
    ALLOWED_PURCHASED(true),

    /** Chưa đăng nhập, mà chương này không dành cho khách. */
    DENIED_LOGIN_REQUIRED(false),

    /** Đã đăng nhập nhưng chương chỉ dành cho VIP và không bán lẻ bằng Xu. */
    DENIED_VIP_REQUIRED(false),

    /** Đã đăng nhập, chương có giá, chưa mua và chưa phải VIP. Mua được. */
    DENIED_COINS_REQUIRED(false);

    private final boolean allowed;

    ChapterAccessDecision(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean allowed() {
        return allowed;
    }

    /** Chỉ trạng thái này mới dẫn tới nút "Mở khóa bằng Xu". */
    public boolean purchasable() {
        return this == DENIED_COINS_REQUIRED;
    }
}
