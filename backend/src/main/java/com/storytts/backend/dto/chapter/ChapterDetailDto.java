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

        /**
         * Phiên bản của {@link #content} ngay trên đây.
         *
         * <p>Hai trường này luôn đi cùng nhau và luôn đọc ra từ cùng một lần đọc
         * cơ sở dữ liệu, nên con số này mô tả đúng những chữ đang nằm trong response
         * — không phải phiên bản mới nhất của chương ở một thời điểm nào khác.
         *
         * <p>Trang đọc giữ lại nó để làm ba việc: biết bản audio nào còn hợp lệ,
         * nhận ra một thông báo cập nhật là mới hay là tiếng vọng của lần mình đã
         * xử lý, và vứt bỏ response về muộn của một lần tải cũ.
         */
        int contentVersion,

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
        Integer audioPositionSeconds,

        /**
         * Giá mở khóa bằng Xu; 0 nghĩa là không bán lẻ.
         *
         * <p>Có mặt ở đây vì form sửa chương của khu quản trị đọc nó ra để điền
         * vào ô giá — trước đây trường này vắng mặt, nên ô ấy luôn hiện 0 dù
         * chương đang có giá.
         */
        long coinPrice,

        /** {@code DRAFT} / {@code SCHEDULED} / {@code PUBLISHED}. */
        String publishState,

        /** Giờ đăng; null là bản nháp, mốc tương lai là đang chờ tới giờ. */
        Instant publishedAt
) {
}
