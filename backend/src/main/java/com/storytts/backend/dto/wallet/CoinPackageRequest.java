package com.storytts.backend.dto.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Quản trị viên tạo hoặc sửa một gói nạp Xu.
 *
 * <p>{@code coins} phải dương: một gói không cho Xu nào là một cách thu tiền mà
 * không giao hàng, và chặn nó ở đây rẻ hơn nhiều so với phát hiện ra sau khi đã
 * có người mua.
 */
public record CoinPackageRequest(

        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 120, message = "Tên gói tối đa 120 ký tự")
        String name,

        @NotNull(message = "Vui lòng nhập giá")
        @Min(value = 1000, message = "Giá tối thiểu là 1.000đ")
        Long priceVnd,

        @NotNull(message = "Vui lòng nhập số Xu")
        @Min(value = 1, message = "Gói phải cho ít nhất 1 Xu")
        Long coins,

        @Min(value = 0, message = "Xu tặng thêm không được âm")
        Long bonusCoins,

        @Size(max = 300, message = "Mô tả tối đa 300 ký tự")
        String description,

        Boolean active,

        Integer sortOrder
) {
}
