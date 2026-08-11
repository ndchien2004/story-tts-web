package com.storytts.backend.dto.vip;

import com.storytts.backend.domain.User;

import java.time.Instant;

/**
 * Tình trạng VIP của người đang đăng nhập.
 *
 * <p>Tách {@code granted} khỏi {@code vipUntil} để giao diện nói đúng chuyện:
 * "VIP vĩnh viễn do quản trị viên cấp" và "VIP tới ngày…" là hai câu khác nhau,
 * và người đã được cấp tay thì không cần mời mua gói.
 */
public record VipStatusDto(
        boolean vip,
        boolean granted,
        Instant vipUntil,
        boolean paymentAvailable
) {

    public static VipStatusDto from(User user, boolean paymentAvailable) {
        return new VipStatusDto(
                user.isVip(),
                user.isVipGranted(),
                user.getVipUntil(),
                paymentAvailable);
    }
}
