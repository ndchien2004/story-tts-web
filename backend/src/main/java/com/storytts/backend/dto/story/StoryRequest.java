package com.storytts.backend.dto.story;

import com.storytts.backend.domain.StoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

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

        StoryStatus status,

        /** Giữ truyện ở dạng bản nháp: chỉ quản trị viên thấy, kể cả chương đã đăng. */
        boolean draft,

        /** Giờ đăng khi không phải nháp; bỏ trống là đăng ngay (hoặc giữ giờ cũ). */
        Instant publishedAt
) {

    /** Xem {@link com.storytts.backend.dto.chapter.ChapterRequest#resolvePublishedAt}. */
    public Instant resolvePublishedAt(Instant current) {
        if (draft) {
            return null;
        }
        if (publishedAt != null) {
            return publishedAt;
        }
        return current != null ? current : Instant.now();
    }
}
