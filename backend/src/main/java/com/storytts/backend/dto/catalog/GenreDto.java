package com.storytts.backend.dto.catalog;

import com.storytts.backend.domain.Genre;

/**
 * Một thể loại.
 *
 * @param storyCount số truyện thuộc thể loại, {@code null} khi nơi gọi không đếm —
 *                   thể loại vừa tạo hay vừa sửa chẳng hạn. Null nghĩa là "chưa đếm",
 *                   khác hẳn 0 nghĩa là "đếm rồi, không có truyện nào".
 */
public record GenreDto(Long id, String name, String description, Long storyCount) {

    public static GenreDto from(Genre genre) {
        if (genre == null) {
            return null;
        }
        return new GenreDto(genre.getId(), genre.getName(), genre.getDescription(), null);
    }

    public static GenreDto from(Genre genre, long storyCount) {
        if (genre == null) {
            return null;
        }
        return new GenreDto(genre.getId(), genre.getName(), genre.getDescription(), storyCount);
    }
}
