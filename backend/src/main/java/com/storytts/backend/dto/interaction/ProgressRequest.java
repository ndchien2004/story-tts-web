package com.storytts.backend.dto.interaction;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ghi nhận tiến độ đọc/nghe một chương.
 *
 * @param lastPosition          phần trăm đã đọc (0-100); 100 nghĩa là đã đọc xong
 * @param audioPositionSeconds  giây đang nghe dở, để lần sau nghe tiếp
 */
public record ProgressRequest(
        @Min(value = 0, message = "Vị trí đọc nằm trong khoảng 0-100.")
        @Max(value = 100, message = "Vị trí đọc nằm trong khoảng 0-100.")
        Integer lastPosition,

        @PositiveOrZero(message = "Vị trí nghe không được âm.")
        Integer audioPositionSeconds
) {
}
