package com.storytts.backend.dto.story;

import com.storytts.backend.domain.Story;

/**
 * Một dòng trong bảng xếp hạng "nghe nhiều nhất" ở trang chủ.
 *
 * <p>Không dùng lại {@link StorySummaryDto}: bảng xếp hạng chỉ là một cột hẹp bên
 * cạnh trang, cần đúng cái tên, cái bìa và con số — còn mô tả truyện thì nó không
 * có chỗ để hiển thị, mà đó lại là trường nặng nhất trong bản tóm tắt kia.
 *
 * @param listenCount số lượt trong đúng khoảng thời gian được hỏi, không phải tổng
 *                    cộng dồn từ trước tới nay
 */
public record StoryRankDto(
        Long id,
        String title,
        String coverImage,
        String genreName,
        String authorName,
        long chapterCount,
        long listenCount
) {

    public static StoryRankDto from(Story story, long chapterCount, long listenCount) {
        return new StoryRankDto(
                story.getId(),
                story.getTitle(),
                story.getCoverImage(),
                story.getGenre() == null ? null : story.getGenre().getName(),
                story.getAuthor() == null ? null : story.getAuthor().getName(),
                chapterCount,
                listenCount);
    }
}
