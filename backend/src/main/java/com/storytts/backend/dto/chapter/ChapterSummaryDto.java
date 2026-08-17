package com.storytts.backend.dto.chapter;

import java.time.Instant;

/**
 * Một dòng trong danh sách chương ở trang chi tiết truyện.
 * <p>
 * Cố ý <b>không</b> chứa trường {@code content}: danh sách chương hiển thị được cho mọi người,
 * nhưng nội dung chương bị khóa không bao giờ rời khỏi server.
 *
 * @param locked        true nếu người dùng hiện tại KHÔNG đủ quyền → frontend hiện icon 🔒
 * @param requirementLabel nhãn tiếng Việt để hiện kèm icon, ví dụ "Yêu cầu VIP"
 * @param purchasable   khóa này mở được bằng Xu → frontend hiện nút thay vì chỉ cái ổ khóa.
 *                      Chỉ đúng khi {@code locked} cũng đúng.
 * @param coinPrice     giá mở khóa; 0 nghĩa là chương không bán lẻ
 */
public record ChapterSummaryDto(
        Long id,
        Long storyId,
        String title,
        Integer chapterNumber,
        String accessLevel,
        String requirementLabel,
        boolean locked,
        boolean purchasable,
        long coinPrice,
        boolean hasAudio,
        long viewCount,
        Instant createdAt
) {
}
