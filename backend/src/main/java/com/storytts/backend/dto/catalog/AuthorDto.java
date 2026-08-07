package com.storytts.backend.dto.catalog;

import com.storytts.backend.domain.Author;

public record AuthorDto(Long id, String name, String bio) {

    public static AuthorDto from(Author author) {
        if (author == null) {
            return null;
        }
        return new AuthorDto(author.getId(), author.getName(), author.getBio());
    }
}
