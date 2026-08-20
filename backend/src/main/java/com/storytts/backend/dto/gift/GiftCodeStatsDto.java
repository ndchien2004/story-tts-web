package com.storytts.backend.dto.gift;

/**
 * Bốn con số tóm tắt toàn bộ gift code, cho đầu trang quản trị.
 *
 * <p>Cùng hình dạng và cùng nguyên tắc với {@code AdminStatsDto}: gộp trong một
 * lần gọi thay vì để giao diện ghép từ nhiều endpoint, vì mỗi con số là một câu
 * COUNT hoặc SUM trên chỉ mục — rẻ hơn nhiều so với thêm một vòng gọi mạng.
 *
 * @param totalCodes       tổng số mã đã từng tạo, kể cả mã đã tắt và đã hết hạn
 * @param activeCodes      số mã đổi được ngay lúc này, tính bằng đúng bộ điều
 *                         kiện mà đường đổi mã dùng
 * @param totalRedemptions tổng số lượt đổi của mọi mã
 * @param totalCoins       tổng Xu đã phát ra, cộng từ sổ đổi mã chứ không nhân
 *                         mệnh giá với số lượt
 */
public record GiftCodeStatsDto(
        long totalCodes,
        long activeCodes,
        long totalRedemptions,
        long totalCoins
) {
}
