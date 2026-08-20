package com.storytts.backend.dto.support;

import com.storytts.backend.domain.User;

/**
 * Vài dòng về chủ luồng, cho danh sách hộp thư của quản trị viên.
 *
 * <h3>Vì sao không dùng lại {@code UserDto}</h3>
 * {@code UserDto} là câu trả lời cho "tài khoản của tôi" và cho bảng quản lý
 * thành viên; nó chở theo email, hạn VIP, mốc tạo tài khoản, số lần đăng nhập
 * sai. Không màn hình nào trong hộp thư hỗ trợ cần những thứ ấy, và mỗi trường
 * thừa trong một danh sách ba mươi dòng là một trường có mặt ở một chỗ nó không
 * cần có mặt.
 *
 * <p>Bốn trường dưới đây là đúng những gì một dòng trong hộp thư vẽ ra: ảnh, một
 * cái tên, một cái nhãn VIP, và một dấu hiệu tài khoản đã bị khóa — cái cuối
 * quan trọng vì nó giải thích vì sao người này im lặng, và vì trả lời một tài
 * khoản đã bị khóa là việc làm không tới đâu.
 *
 * <p>Lớp này <b>chỉ</b> xuất hiện ở phía quản trị. Người đọc không bao giờ nhận
 * được nó — họ đã biết mình là ai, và phía hỗ trợ thì cố ý vô danh với họ, xem
 * {@link SupportMessageDto}.
 */
public record SupportUserSummaryDto(
        Long id,
        String displayName,
        String avatarUrl,
        boolean vip,
        boolean enabled
) {

    public static SupportUserSummaryDto from(User user) {
        String name = user.getDisplayName();
        return new SupportUserSummaryDto(
                user.getId(),
                (name == null || name.isBlank()) ? user.getUsername() : name,
                user.getAvatarUrl(),
                user.isVip(),
                user.isEnabled());
    }
}
