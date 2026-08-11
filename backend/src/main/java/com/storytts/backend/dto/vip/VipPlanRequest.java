package com.storytts.backend.dto.vip;

import jakarta.validation.constraints.*;

/**
 * Admin tạo hoặc sửa một gói.
 *
 * <p>Số tháng để mở: gói một tháng và gói một năm là cùng một biểu mẫu, chỉ
 * khác con số — nên không có danh sách kỳ hạn cố định nào ở đây.
 */
public record VipPlanRequest(

        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 120, message = "Tên gói tối đa 120 ký tự")
        String name,

        @Min(value = 1, message = "Gói phải có ít nhất 1 tháng")
        @Max(value = 120, message = "Gói tối đa 120 tháng")
        int months,

        // PayOS không nhận đơn dưới 1.000đ, và chặn sớm thì thông báo rõ hơn là
        // để cổng thanh toán từ chối.
        @Min(value = 1000, message = "Giá tối thiểu là 1.000đ")
        @Max(value = 500_000_000L, message = "Giá tối đa là 500.000.000đ")
        long priceVnd,

        @Size(max = 300, message = "Mô tả tối đa 300 ký tự")
        String description,

        boolean active,

        @Min(value = 0, message = "Thứ tự không được âm")
        @Max(value = 999, message = "Thứ tự tối đa là 999")
        int sortOrder
) {
}
