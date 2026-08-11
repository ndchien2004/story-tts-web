package com.storytts.backend.dto.catalog;

import com.storytts.backend.domain.Author;

/**
 * Một tác giả.
 *
 * @param storyCount số truyện của tác giả, {@code null} khi nơi gọi không đếm —
 *                   tác giả vừa tạo hay vừa sửa chẳng hạn. Null nghĩa là "chưa đếm",
 *                   khác hẳn 0 nghĩa là "đếm rồi, không có truyện nào".
 */
public record AuthorDto(Long id, String name, String bio, Long storyCount) {

    public static AuthorDto from(Author author) {
        if (author == null) {
            return null;
        }
        return new AuthorDto(author.getId(), author.getName(), author.getBio(), null);
    }

    public static AuthorDto from(Author author, long storyCount) {
        if (author == null) {
            return null;
        }
        return new AuthorDto(author.getId(), author.getName(), author.getBio(), storyCount);
    }
}
