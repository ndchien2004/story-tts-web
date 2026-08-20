package com.storytts.backend.dto.gift;

import com.storytts.backend.domain.GiftCode;

import java.time.Instant;

/**
 * Một gift code mở ra xem đầy đủ: cấu hình của nó, kèm ba con số về việc nó đã
 * phát ra những gì.
 *
 * <p>Ba con số ấy đến từ cơ sở dữ liệu chứ không từ phép nhân
 * {@code coinAmount × usedCount}. Phép nhân sai ngay khi mệnh giá được sửa giữa
 * chừng — xem {@code GiftCodeRedemption}. {@code totalCoins} là một câu SUM trên
 * sổ đổi mã; {@code redemptionCount} là một câu COUNT trên cùng bảng, và nó phải
 * luôn bằng {@code giftCode.usedCount}. Hai con số ấy được trả về cạnh nhau một
 * cách cố ý: chúng lệch nhau là bằng chứng của một bất biến đã vỡ, và bảng quản
 * trị là nơi sớm nhất nhìn thấy điều đó.
 */
public record GiftCodeDetailDto(
        GiftCodeDto giftCode,

        /** COUNT trên sổ đổi mã. Phải bằng {@code giftCode.usedCount}. */
        long redemptionCount,

        /** SUM số Xu thật sự đã phát. */
        long totalCoins,

        /** Bằng {@code redemptionCount == giftCode.usedCount}; xem ghi chú ở đầu lớp. */
        boolean consistent
) {

    public static GiftCodeDetailDto of(GiftCode code, long redemptionCount, long totalCoins,
                                       Instant now) {
        return new GiftCodeDetailDto(
                GiftCodeDto.from(code, now),
                redemptionCount,
                totalCoins,
                redemptionCount == code.getUsedCount());
    }
}
