package com.storytts.backend.dto.audio;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Bản đọc của một chương: audio nào, và chữ nào rơi vào giây nào của nó.
 *
 * <p>Gắn với một bản audio cụ thể chứ không gắn với chương. Một chương có thể có
 * bản admin thu sẵn lẫn bản máy đọc, hai bản dài ngắn khác nhau, nên mốc thời
 * gian của bản này đặt lên bản kia là tô sáng sai từ chữ đầu tiên.
 *
 * <p>{@code contentHash} là dấu vân tay của nội dung chương lúc bản audio được
 * dựng. Chương sửa chữ sau đó thì mốc cũ không còn khớp; trang đọc so chỗ này
 * để biết nên tô sáng hay nên im lặng bỏ qua.
 *
 * @param audioUrl   đường dẫn tương đối tới chính file audio này, để bên gọi
 *                   không phải ghép lại từ hai lời gọi khác nhau
 * @param timestamps theo đúng thứ tự đọc, không chồng lấn
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChapterTranscriptDto(
        Long storyId,
        Long chapterId,
        Long audioId,
        String audioUrl,
        String contentHash,
        int wordCount,
        List<WordTimestampDto> timestamps
) {
}
