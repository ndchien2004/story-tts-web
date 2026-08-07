package com.storytts.backend.dto.chapter;

import java.time.Instant;

/**
 * Một dòng trong danh sách chương ở trang chi tiết truyện (mục 4.3 đề bài).
 * <p>
 * Cố ý <b>không</b> chứa trường {@code content}: danh sách chương hiển thị được cho mọi người,
 * nhưng nội dung chương bị khóa không bao giờ rời khỏi server.
 *
 * @param locked        true nếu người dùng hiện tại KHÔNG đủ quyền → frontend hiện icon 🔒
 * @param requirementLabel nhãn tiếng Việt để hiện kèm icon, ví dụ "Yêu cầu VIP"
 */
public record ChapterSummaryDto(
        Long id,
        Long storyId,
        String title,
        Integer chapterNumber,
        String accessLevel,
        String requirementLabel,
        boolean locked,
        boolean hasAudio,
        long viewCount,
        Instant createdAt
) {
}
