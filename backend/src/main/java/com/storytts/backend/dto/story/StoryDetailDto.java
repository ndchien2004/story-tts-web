package com.storytts.backend.dto.story;

import com.storytts.backend.dto.chapter.ChapterSummaryDto;

import java.util.List;

/** Trang chi tiết truyện: thông tin truyện + danh sách chương kèm trạng thái khóa. */
public record StoryDetailDto(
        StorySummaryDto story,
        List<ChapterSummaryDto> chapters
) {
}
