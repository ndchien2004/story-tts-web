package com.storytts.backend.dto.support;

/**
 * Một dòng trong hộp thư hỗ trợ của quản trị viên.
 *
 * <p>Ba mảnh, và mỗi mảnh đến từ một chỗ khác nhau vì lý do khác nhau:
 * {@code conversation} là trạng thái luồng nhìn từ phía hỗ trợ,
 * {@code user} là chủ luồng (lấy sẵn bằng {@code JOIN FETCH}, không phải một câu
 * truy vấn cho mỗi dòng), và {@code lastMessagePreview} là bộ nhớ đệm đọc thẳng
 * từ cột — xem {@code SupportConversation}.
 *
 * <p>Cả ba cùng đến từ một câu truy vấn duy nhất cho cả trang, cộng thêm đúng
 * một câu nữa để đếm chưa đọc cho tất cả các dòng. Ba mươi dòng vì thế là hai
 * lượt đi cơ sở dữ liệu, không phải sáu mươi.
 *
 * @param lastMessagePreview null khi luồng vừa tạo và chưa ai nói gì
 */
public record SupportInboxItemDto(
        SupportConversationDto conversation,
        SupportUserSummaryDto user,
        String lastMessagePreview
) {
}
