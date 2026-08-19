package com.storytts.backend.dto.story;

import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
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
 * <p><b>{@code chapters} là một <i>trang</i>, không phải cả danh sách.</b> Trước
 * đây nó trả về toàn bộ chương của truyện, thứ không ai thấy vấn đề với sáu
 * truyện mẫu bốn chương nhưng là vài trăm KB JSON mỗi lần mở trang với một truyện
 * dịch nghìn chương. Các trang sau lấy qua {@code GET /api/stories/{id}/chapters},
 * đường vốn đã tồn tại. Tổng số chương đọc ở {@code chapters.totalElements()} —
 * và {@code readChapterIds} vẫn là <i>toàn bộ</i> chương đã đọc của truyện, không
 * bị cắt theo trang, vì thanh tiến độ nói về cả truyện.
 *
 * @param readChapterIds   các chương người dùng hiện tại đã đọc xong; rỗng với Khách
 * @param resumeChapterId  chương để nút "Đọc tiếp" mở, null nếu chưa từng đọc truyện này
 */
public record StoryDetailDto(
        StorySummaryDto story,
        PageResponse<ChapterSummaryDto> chapters,
        RatingSummaryDto rating,
        FavoriteStatusDto favorite,
        List<Long> readChapterIds,

        /** Số chương của truyện đang có audio nghe được — đếm trên cả truyện, không theo trang. */
        long audioChapterCount,

        Long resumeChapterId
) {
}
