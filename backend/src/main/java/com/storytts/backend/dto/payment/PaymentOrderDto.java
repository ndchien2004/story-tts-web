package com.storytts.backend.dto.payment;

import com.storytts.backend.domain.PaymentOrder;
import com.storytts.backend.domain.PaymentOrderStatus;

import java.time.Instant;

/**
 * Một đơn mua bằng tiền thật — gói VIP hoặc gói Xu.
 *
 * <p>{@code checkoutUrl} chỉ có giá trị khi đơn còn chờ thanh toán; trang kết
 * quả dùng nó để mời người dùng trả lại nếu họ bỏ dở giữa chừng.
 *
 * <p>{@code months}/{@code vipUntilAfter} chỉ có ở đơn VIP, {@code coinsGranted}
 * chỉ có ở đơn nạp Xu; phía còn lại là null. Giao diện đọc {@code kind} để biết
 * nên hiện trường nào, thay vì đoán từ việc trường nào khác null.
 */
public record PaymentOrderDto(
        Long orderCode,
        String kind,
        String kindLabel,
        String itemName,
        Integer months,
        Long coinsGranted,
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

    public static PaymentOrderDto from(PaymentOrder order) {
        return new PaymentOrderDto(
                order.getOrderCode(),
                order.getKind().name(),
                order.getKind().getLabel(),
                order.getItemName(),
                order.getMonths(),
                order.getCoinsGranted(),
                order.getAmountVnd(),
                order.getStatus().name(),
                label(order),
                order.getStatus() == PaymentOrderStatus.PENDING ? order.getCheckoutUrl() : null,
                order.getUser().getUsername(),
                order.getUser().getDisplayName(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getVipUntilAfter());
    }

    private static String label(PaymentOrder order) {
        return switch (order.getStatus()) {
            case PENDING -> "Chờ thanh toán";
            case PAID -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
            case EXPIRED -> "Quá hạn";
        };
    }
}
