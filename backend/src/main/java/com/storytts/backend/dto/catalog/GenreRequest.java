package com.storytts.backend.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(

        @NotBlank(message = "Tên thể loại không được để trống")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description
) {
}
