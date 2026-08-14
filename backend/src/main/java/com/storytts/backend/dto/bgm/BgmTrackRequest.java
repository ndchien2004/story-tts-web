package com.storytts.backend.dto.bgm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phần chữ nghĩa của một bản nhạc nền — dùng cho cả lúc tải lên và lúc sửa.
 *
 * <p>Lúc tải lên nó đi kèm file trong cùng một multipart, nên mọi trường đều có
 * thể để trống: không đặt tên thì tên file thành tên bản nhạc.
 */
public record BgmTrackRequest(

        @NotBlank(message = "Tên bản nhạc không được để trống.")
        @Size(max = 150, message = "Tên bản nhạc tối đa 150 ký tự.")
        String title,

        @Size(max = 255, message = "Dòng ghi công tối đa 255 ký tự.")
        String credit,

        /** Null nghĩa là giữ nguyên trạng thái hiện tại. */
        Boolean active,

        /** Null nghĩa là giữ nguyên thứ tự hiện tại. */
        Integer sortOrder
) {
}
