package com.storytts.backend.dto.interaction;

import com.storytts.backend.domain.ReadingProgress;

import java.time.Instant;

/**
 * Tiến độ của một chương, cũng dùng làm một dòng trong "Đang đọc dở".
 *
 * @param completed đã đọc xong chương này chưa
 */
public record ProgressDto(
        Long chapterId,
        Long storyId,
        String storyTitle,
        String storyCoverImage,
        String chapterTitle,
        Integer chapterNumber,
        int lastPosition,
        int audioPositionSeconds,
        boolean completed,
        Instant updatedAt
) {

    public static ProgressDto from(ReadingProgress progress, boolean completed) {
        var chapter = progress.getChapter();
        var story = chapter.getStory();
        return new ProgressDto(
                chapter.getId(),
                story.getId(),
                story.getTitle(),
                story.getCoverImage(),
                chapter.getTitle(),
                chapter.getChapterNumber(),
                progress.getLastPosition(),
                progress.getAudioPositionSeconds(),
                completed,
                progress.getUpdatedAt());
    }
}
