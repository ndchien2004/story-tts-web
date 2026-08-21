package com.storytts.backend.dto.support;

import jakarta.validation.constraints.Size;

/**
 * Xin chuyển cuộc trò chuyện cho tư vấn viên.
 *
 * <h3>Không có {@code conversationId} ở đây, và đó là một quyết định bảo mật</h3>
 * Một người đọc có đúng một luồng — {@code UNIQUE (user_id)}, V15 — nên máy chủ
 * tra ra nó từ phiên đăng nhập. Nhận id từ trình duyệt sẽ là mở một tham số cần
 * kiểm quyền sở hữu, tức là mở một chỗ có thể quên kiểm. Không nhận thì không
 * có gì để giả mạo. Cùng lối với {@code SupportSendRequest}.
 *
 * @param reason lý do người đọc nêu, tùy chọn. Nó đi vào <i>nội dung</i> tin hệ
 *               thống đánh dấu việc chuyển giao chứ không vào một cột riêng:
 *               đây là một câu cho người trực đọc, không phải một thứ có ai truy
 *               vấn. Trần 500 ký tự vì nó là một dòng ghi chú, không phải một
 *               tin nhắn.
 */
public record SupportHandoffRequest(
        @Size(max = 500, message = "Lý do quá dài. Tối đa 500 ký tự.")
        String reason
) {
}
