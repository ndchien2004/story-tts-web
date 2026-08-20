package com.storytts.backend.dto.support;

import com.storytts.backend.domain.SupportConversationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Lệnh đổi trạng thái một luồng — chỉ quản trị viên gọi được.
 *
 * <h3>Vì sao là một trường {@code status} chứ không phải ba đường riêng</h3>
 * {@code /close}, {@code /reopen}, {@code /block} là ba đường cho ba lệnh mà cả
 * ba làm đúng một việc: ghi một giá trị vào một cột, trong một giao dịch, kèm
 * một tin hệ thống. Ba đường sẽ là ba chỗ phải nhớ kiểm quyền, ba chỗ phải nhớ
 * ghi nhật ký, và ba chỗ để chúng lệch nhau.
 *
 * <p>Đổi lại, giá trị đến từ trình duyệt — nên nó được kiểm ở tầng service chứ
 * không được tin: một giá trị không có trong enum bị Jackson từ chối ngay lúc
 * đọc thân request, và một bước chuyển không hợp lệ nhận
 * {@code INVALID_STATUS_TRANSITION}. Cái mà trình duyệt <i>không</i> quyết định
 * được là nó có quyền gửi lệnh này hay không: đường này nằm dưới
 * {@code /api/admin/**}, vốn đã có {@code hasRole('ADMIN')} ở tầng URL, và tầng
 * service kiểm lại một lần nữa từ tài khoản vừa đọc lại từ cơ sở dữ liệu.
 *
 * @param status trạng thái đích. {@code OPEN} vừa là "mở lại" vừa là "bỏ chặn" —
 *               cùng một đích, và bước xuất phát không đổi việc phải làm.
 */
public record SupportStatusRequest(

        @NotNull(message = "Thiếu trạng thái cần chuyển.")
        SupportConversationStatus status
) {
}
