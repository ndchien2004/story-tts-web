package com.storytts.backend.dto.gift;

import com.storytts.backend.domain.GiftCodeRedemption;

import java.time.Instant;

/**
 * Một dòng trong danh sách "ai đã đổi mã này".
 *
 * <p>Chỉ có id, tên hiển thị và username của người đổi — không có email. Khu
 * quản trị đã có trang thành viên để tra một tài khoản cụ thể; nhân bản địa chỉ
 * thư của mọi người vào một bảng mà công dụng chính là đếm lượt chỉ làm rộng
 * thêm mặt tiếp xúc của dữ liệu cá nhân mà không trả lời thêm câu hỏi nào.
 *
 * @param coinAmount số Xu lượt này thật sự đã phát, không phải mệnh giá hiện tại
 */
public record GiftCodeRedemptionDto(
        Long id,
        Long userId,
        String username,
        String displayName,
        long coinAmount,
        Instant redeemedAt
) {

    public static GiftCodeRedemptionDto from(GiftCodeRedemption redemption) {
        var user = redemption.getUser();
        return new GiftCodeRedemptionDto(
                redemption.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                redemption.getCoinAmount(),
                redemption.getCreatedAt());
    }
}
