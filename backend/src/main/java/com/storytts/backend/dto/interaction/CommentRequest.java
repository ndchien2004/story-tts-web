package com.storytts.backend.dto.interaction;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Gửi đánh giá và/hoặc bình luận cho một truyện.
 * Cho phép chỉ chấm sao, chỉ bình luận, hoặc cả hai — service từ chối khi trống cả hai.
 */
public record CommentRequest(
        @Min(value = 1, message = "Điểm đánh giá từ 1 đến 5 sao.")
        @Max(value = 5, message = "Điểm đánh giá từ 1 đến 5 sao.")
        Integer rating,

        @Size(max = 2000, message = "Bình luận tối đa 2000 ký tự.")
        String comment
) {
}
