package com.storytts.backend.dto.chapter;

import com.storytts.backend.domain.AccessLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Dữ liệu Admin gửi lên khi tạo/sửa chương.
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
        AccessLevel accessLevel,

        /** Giữ chương ở dạng bản nháp: chỉ quản trị viên thấy. */
        boolean draft,

        /**
         * Giờ đăng, khi không phải bản nháp.
         *
         * <p>Bỏ trống là đăng ngay (và với chương đã đăng thì giữ nguyên giờ
         * đăng cũ). Mốc ở tương lai là hẹn giờ — xem
         * {@link com.storytts.backend.domain.Publishable}.
         */
        Instant publishedAt
) {

    /**
     * Giờ đăng sau khi gộp ba trường trên thành một giá trị duy nhất.
     *
     * <h3>Vì sao {@code draft} là một cờ riêng thay vì "gửi null là nháp"</h3>
     * JSON không phân biệt được "không gửi trường này" với "gửi giá trị null":
     * cả hai đều tới nơi là {@code null}. Nếu null có nghĩa là nháp thì một
     * client cũ — hoặc một lời gọi API chỉ định sửa cái tiêu đề — sẽ lặng lẽ gỡ
     * cả chương xuống. Một cờ riêng khiến việc gỡ xuống phải được nói ra.
     *
     * @param current giờ đăng đang lưu, để lần sửa không dời lịch của một chương
     *                đã đăng hay đang chờ tới giờ
     */
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
