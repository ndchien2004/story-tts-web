package com.storytts.backend.dto.audio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Options for a synthesis request. Both fields are optional and fall back to
 * the server defaults.
 */
public record TtsRequest(

        String voice,

        @Min(value = -3, message = "Tốc độ đọc phải từ -3 đến 3")
        @Max(value = 3, message = "Tốc độ đọc phải từ -3 đến 3")
        Integer speed
) {
}
