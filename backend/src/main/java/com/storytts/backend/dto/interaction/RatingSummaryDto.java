package com.storytts.backend.dto.interaction;

/**
 * Tổng hợp điểm đánh giá của một truyện.
 *
 * @param average điểm trung bình, null khi chưa ai chấm sao
 * @param count   số lượt chấm sao (không tính bình luận không kèm sao)
 */
public record RatingSummaryDto(
        Double average,
        long count
) {

    public static RatingSummaryDto empty() {
        return new RatingSummaryDto(null, 0);
    }
}
