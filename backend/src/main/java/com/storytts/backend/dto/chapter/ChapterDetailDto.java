package com.storytts.backend.dto.chapter;

import java.time.Instant;

/**
 * Nội dung đầy đủ của một chương. Chỉ được tạo ra sau khi
 * {@code AccessControlService.requireAccess()} đã cho phép.
 */
public record ChapterDetailDto(
        Long id,
        Long storyId,
        String storyTitle,
        String title,
        String content,
        Integer chapterNumber,
        String accessLevel,
        String requirementLabel,
        long viewCount,
        Instant createdAt,

        /** Điều hướng chương trước/sau (mục 4.3 đề bài). Null nếu không còn chương. */
        Long previousChapterId,
        Long nextChapterId,

        /** Có audio do Admin upload sẵn hay không (mục 4.4 đề bài). */
        boolean hasUploadedAudio,
        /** Đã có bản TTS trong cache hay chưa (mục 4.5 đề bài). */
        boolean hasTtsAudio
) {
}
