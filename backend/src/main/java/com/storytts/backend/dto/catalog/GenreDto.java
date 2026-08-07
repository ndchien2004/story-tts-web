package com.storytts.backend.dto.catalog;

import com.storytts.backend.domain.Genre;

public record GenreDto(Long id, String name, String description) {

    public static GenreDto from(Genre genre) {
        if (genre == null) {
            return null;
        }
        return new GenreDto(genre.getId(), genre.getName(), genre.getDescription());
    }
}
