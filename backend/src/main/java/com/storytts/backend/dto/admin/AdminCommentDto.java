package com.storytts.backend.dto.admin;

import com.storytts.backend.domain.RatingComment;

import java.time.Instant;

/**
 * Một bình luận nhìn từ màn hình kiểm duyệt.
 *
 * <p>Khác {@code CommentDto} của trang truyện ở chỗ có kèm truyện: ở đây danh sách
 * trộn lẫn mọi truyện, nên không nói rõ bình luận thuộc truyện nào thì Admin không
 * thể nào duyệt được.
 */
public record AdminCommentDto(
        Long id,
        Long storyId,
        String storyTitle,
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        Integer rating,
        String comment,
        Instant createdAt
) {

    public static AdminCommentDto from(RatingComment entity) {
        var user = entity.getUser();
        var story = entity.getStory();
        return new AdminCommentDto(
                entity.getId(),
                story.getId(),
                story.getTitle(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                user.getAvatarUrl(),
                entity.getRating(),
                entity.getComment(),
                entity.getCreatedAt());
    }
}
