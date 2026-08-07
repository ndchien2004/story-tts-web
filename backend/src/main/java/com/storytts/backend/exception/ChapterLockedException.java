package com.storytts.backend.exception;

import com.storytts.backend.domain.AccessLevel;
import lombok.Getter;

/**
 * Chương bị khóa và người dùng hiện tại không đủ quyền → HTTP 403 (mục 4.3 [BB] đề bài).
 * <p>
 * Kèm theo {@link #requiredAccessLevel} để React biết nên hiển thị màn hình
 * "yêu cầu đăng nhập" hay "nâng cấp VIP". Ngoại lệ này <b>không</b> chứa nội dung chương.
 */
@Getter
public class ChapterLockedException extends RuntimeException {

    private final AccessLevel requiredAccessLevel;
    private final boolean authenticated;

    public ChapterLockedException(AccessLevel requiredAccessLevel, boolean authenticated) {
        super(buildMessage(requiredAccessLevel, authenticated));
        this.requiredAccessLevel = requiredAccessLevel;
        this.authenticated = authenticated;
    }

    private static String buildMessage(AccessLevel level, boolean authenticated) {
        return switch (level) {
            case MEMBER -> "Chương này yêu cầu đăng nhập để đọc/nghe.";
            case VIP -> authenticated
                    ? "Chương này dành riêng cho thành viên VIP. Vui lòng liên hệ quản trị viên để nâng cấp."
                    : "Chương này dành riêng cho thành viên VIP. Vui lòng đăng nhập bằng tài khoản VIP.";
            case PUBLIC -> "Bạn không có quyền truy cập chương này.";
        };
    }
}
