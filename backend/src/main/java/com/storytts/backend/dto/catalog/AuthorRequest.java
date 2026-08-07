package com.storytts.backend.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorRequest(

        @NotBlank(message = "Tên tác giả không được để trống")
        @Size(max = 150)
        String name,

        String bio
) {
}
