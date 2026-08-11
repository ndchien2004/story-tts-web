package com.storytts.backend.dto.admin;

import com.storytts.backend.domain.Chapter;

/**
 * Một chương nhìn từ màn hình quản lý audio.
 *
 * @param hasAudio      đã có ít nhất một bản audio dùng được
 * @param processing    đang có bản audio tạo dở — bấm tạo thêm lúc này là thừa
 * @param failed        lần tạo gần nhất hỏng, nên thử lại
 * @param characters    độ dài nội dung, ước lượng thô cho chi phí gọi TTS
 */
public record ChapterAudioDto(
        Long chapterId,
        Integer chapterNumber,
        String title,
        Long storyId,
        String storyTitle,
        String accessLevel,
        boolean hasAudio,
        boolean processing,
        boolean failed,
        int characters
) {

    public static ChapterAudioDto from(Chapter chapter, boolean hasAudio,
                                       boolean processing, boolean failed) {
        var story = chapter.getStory();
        return new ChapterAudioDto(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                story.getId(),
                story.getTitle(),
                chapter.getAccessLevel() == null ? null : chapter.getAccessLevel().name(),
                hasAudio,
                processing,
                failed,
                chapter.getContent() == null ? 0 : chapter.getContent().length());
    }
}
