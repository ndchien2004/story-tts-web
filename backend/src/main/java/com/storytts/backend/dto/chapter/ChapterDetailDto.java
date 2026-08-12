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

        /** Điều hướng chương trước/sau. Null nếu không còn chương. */
        Long previousChapterId,
        Long nextChapterId,

        /** Có audio do Admin upload sẵn hay không. */
        boolean hasUploadedAudio,
        /** Đã có bản TTS trong cache hay chưa. */
        boolean hasTtsAudio,

        /**
         * Giây đang nghe dở của người dùng hiện tại (mục 4.4).
         *
         * <p>Null với Khách và với người chưa từng nghe chương này — để trình phát
         * phân biệt được "chưa nghe" với "đã nghe và đang ở giây 0".
         */
        Integer audioPositionSeconds
) {
}
