package com.storytts.backend.dto.interaction;

/**
 * Trạng thái yêu thích của một truyện với người dùng hiện tại.
 *
 * @param favorite true nếu người đang đăng nhập đã đánh dấu truyện này
 * @param count    tổng số người đã đánh dấu — dùng chung cho cả Khách
 */
public record FavoriteStatusDto(
        boolean favorite,
        long count
) {
}
