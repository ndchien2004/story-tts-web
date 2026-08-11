package com.storytts.backend.dto.vip;

import com.storytts.backend.domain.VipOrder;

import java.time.Instant;

/**
 * Một đơn nâng cấp.
 *
 * <p>{@code checkoutUrl} chỉ có giá trị khi đơn còn chờ thanh toán; trang kết
 * quả dùng nó để mời người dùng trả lại nếu họ bỏ dở giữa chừng.
 */
public record VipOrderDto(
        Long orderCode,
        String planName,
        int months,
        long amountVnd,
        String status,
        String statusLabel,
        String checkoutUrl,
        String username,
        String displayName,
        Instant createdAt,
        Instant paidAt,
        Instant vipUntilAfter
) {

    public static VipOrderDto from(VipOrder order) {
        return new VipOrderDto(
                order.getOrderCode(),
                order.getPlanName(),
                order.getMonths(),
                order.getAmountVnd(),
                order.getStatus().name(),
                label(order),
                order.getStatus() == com.storytts.backend.domain.VipOrderStatus.PENDING
                        ? order.getCheckoutUrl()
                        : null,
                order.getUser().getUsername(),
                order.getUser().getDisplayName(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getVipUntilAfter());
    }

    private static String label(VipOrder order) {
        return switch (order.getStatus()) {
            case PENDING -> "Chờ thanh toán";
            case PAID -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
            case EXPIRED -> "Quá hạn";
        };
    }
}
