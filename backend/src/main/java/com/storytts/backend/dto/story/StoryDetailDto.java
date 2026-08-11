package com.storytts.backend.dto.story;

import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.interaction.FavoriteStatusDto;
import com.storytts.backend.dto.interaction.RatingSummaryDto;

import java.util.List;

/**
 * Trang chi tiết truyện: thông tin truyện + danh sách chương kèm trạng thái khóa,
 * kèm luôn phần tương tác của người đang xem.
 *
 * <p>Tiến độ đọc đi kèm dưới dạng danh sách id thay vì thêm một cờ vào từng chương,
 * để {@link ChapterSummaryDto} giữ nguyên hình dạng ở cả những nơi không có người dùng
 * (trang quản trị chẳng hạn).
 *
 * @param readChapterIds   các chương người dùng hiện tại đã đọc xong; rỗng với Khách
 * @param resumeChapterId  chương để nút "Đọc tiếp" mở, null nếu chưa từng đọc truyện này
 */
public record StoryDetailDto(
        StorySummaryDto story,
        List<ChapterSummaryDto> chapters,
        RatingSummaryDto rating,
        FavoriteStatusDto favorite,
        List<Long> readChapterIds,
        Long resumeChapterId
) {
}
