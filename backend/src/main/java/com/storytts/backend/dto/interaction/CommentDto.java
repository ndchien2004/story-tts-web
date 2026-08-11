package com.storytts.backend.dto.interaction;

import com.storytts.backend.domain.RatingComment;

import java.time.Instant;

/**
 * Một bình luận/đánh giá hiển thị dưới trang truyện.
 *
 * @param rating số sao 1-5, null nếu người dùng chỉ bình luận
 * @param mine   true nếu của chính người đang đăng nhập → frontend hiện nút xóa
 */
public record CommentDto(
        Long id,
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        Integer rating,
        String comment,
        Instant createdAt,
        boolean mine
) {

    public static CommentDto from(RatingComment entity, Long currentUserId) {
        var user = entity.getUser();
        return new CommentDto(
                entity.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                user.getAvatarUrl(),
                entity.getRating(),
                entity.getComment(),
                entity.getCreatedAt(),
                currentUserId != null && currentUserId.equals(user.getId()));
    }
}
