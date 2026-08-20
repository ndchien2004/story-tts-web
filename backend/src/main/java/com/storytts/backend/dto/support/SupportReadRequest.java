package com.storytts.backend.dto.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * "Tôi đã đọc tới tin số này."
 *
 * <h3>Một mốc, không phải một danh sách id</h3>
 * Trình duyệt báo tin <i>cuối cùng</i> nó đã hiện ra, và máy chủ đẩy mốc lên
 * tới đó. Gửi từng id một sẽ là một khung tin cho mỗi dòng cuộn qua, và nó cũng
 * mở ra một trạng thái không có nghĩa: "đã đọc tin 5 và 7 nhưng chưa đọc tin 6"
 * không phải điều gì xảy ra được trong một luồng chat mà người ta cuộn từ trên
 * xuống.
 *
 * <h3>Máy chủ vẫn kiểm giá trị này</h3>
 * Ba phép, ở {@code SupportConversationService}: tin ấy có thuộc luồng này
 * không (một id của luồng người khác không được phép đẩy mốc), người gọi có
 * phải chủ luồng hay quản trị viên không, và mốc mới có thật sự lớn hơn mốc cũ
 * không. Phép thứ ba là thứ khiến hai tab bấm lệch nhịp không kéo con số chưa
 * đọc quay lại.
 *
 * @param lastMessageId id tin nhắn cuối đã hiện. Bắt buộc và phải dương — mốc 0
 *                      là giá trị khởi tạo của một luồng chưa ai đọc, không phải
 *                      thứ trình duyệt gửi lên được.
 */
public record SupportReadRequest(

        @NotNull(message = "Thiếu id tin nhắn đã đọc.")
        @Positive(message = "Id tin nhắn không hợp lệ.")
        Long lastMessageId
) {
}
