package com.storytts.backend.dto.chapter;

import com.storytts.backend.domain.AccessLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu Admin gửi lên khi tạo/sửa chương (mục 4.2 đề bài).
 * Trường {@link #accessLevel} chính là thao tác "khóa chương".
 */
public record ChapterRequest(

        @NotBlank(message = "Tiêu đề chương không được để trống")
        @Size(max = 255)
        String title,

        @NotBlank(message = "Nội dung chương không được để trống")
        String content,

        /** Bỏ trống thì hệ thống tự đánh số tiếp theo trong truyện. */
        @Min(value = 1, message = "Số thứ tự chương phải lớn hơn 0")
        Integer chapterNumber,

        @NotNull(message = "Vui lòng chọn mức truy cập cho chương")
        AccessLevel accessLevel
) {
}
