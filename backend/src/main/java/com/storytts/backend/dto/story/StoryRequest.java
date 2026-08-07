package com.storytts.backend.dto.story;

import com.storytts.backend.domain.StoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu Admin gửi lên khi tạo/sửa truyện. */
public record StoryRequest(

        @NotBlank(message = "Tên truyện không được để trống")
        @Size(max = 255)
        String title,

        /** Chọn tác giả có sẵn theo id, hoặc bỏ trống và nhập {@link #authorName} để tạo mới. */
        Long authorId,
        @Size(max = 150)
        String authorName,

        Long genreId,
        @Size(max = 100)
        String genreName,

        @Size(max = 500)
        String coverImage,

        String description,

        StoryStatus status
) {
}
