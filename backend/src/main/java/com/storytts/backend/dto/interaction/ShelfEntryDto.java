package com.storytts.backend.dto.interaction;

import com.storytts.backend.domain.ReadingProgress;

import java.time.Instant;

/**
 * Một truyện trong tủ truyện của người dùng.
 *
 * <p>Khác {@link ProgressDto} ở chỗ đây là một dòng cho mỗi <em>truyện</em>, không
 * phải mỗi chương: chỗ đang đọc dở, kèm số chương đã đọc xong trên tổng số chương.
 * Nhờ hai con số đó, "đã đọc xong" mới có nghĩa là đọc hết truyện — chứ không phải
 * chỉ đọc hết cái chương mở gần nhất.
 *
 * @param readChapters  số chương đã đọc xong của truyện này
 * @param totalChapters tổng số chương hiện có
 * @param finished      đã đọc hết mọi chương
 */
public record ShelfEntryDto(
        Long storyId,
        String storyTitle,
        String storyCoverImage,
        Long chapterId,
        String chapterTitle,
        Integer chapterNumber,
        long readChapters,
        long totalChapters,
        boolean finished,
        Instant updatedAt
) {

    public static ShelfEntryDto from(ReadingProgress latest, long readChapters, long totalChapters) {
        var chapter = latest.getChapter();
        var story = chapter.getStory();
        return new ShelfEntryDto(
                story.getId(),
                story.getTitle(),
                story.getCoverImage(),
                chapter.getId(),
                chapter.getTitle(),
                chapter.getChapterNumber(),
                readChapters,
                totalChapters,
                totalChapters > 0 && readChapters >= totalChapters,
                latest.getUpdatedAt());
    }
}
