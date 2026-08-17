package com.storytts.backend.controller;

import com.storytts.backend.dto.payment.PaymentOrderDto;
import com.storytts.backend.dto.vip.VipPlanDto;
import com.storytts.backend.dto.vip.VipStatusDto;
import com.storytts.backend.service.PaymentOrderService;
import com.storytts.backend.service.VipPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Nâng cấp VIP từ phía người đọc.
 *
 * <p>Danh sách gói để công khai — giá là thứ người ta cần biết trước khi quyết
 * định có đăng ký tài khoản hay không. Mọi thứ còn lại đòi đăng nhập, vì đơn
 * hàng luôn thuộc về một người cụ thể.
 */
@RestController
@RequestMapping("/api/vip")
@RequiredArgsConstructor
@Tag(name = "VIP", description = "Gói VIP và thanh toán qua PayOS")
public class VipController {

    private final VipPlanService planService;
    private final PaymentOrderService orderService;

    @GetMapping("/plans")
    @Operation(summary = "Các gói VIP đang bán")
    public List<VipPlanDto> plans() {
        return planService.listActive();
    }

    @GetMapping("/me")
    @Operation(summary = "Tình trạng VIP của tài khoản đang đăng nhập")
    public VipStatusDto me() {
        return orderService.myStatus();
    }

    @GetMapping("/orders")
    @Operation(summary = "Các đơn nâng cấp gần đây của chính mình")
    public List<PaymentOrderDto> myOrders() {
        return orderService.myOrders();
    }

    @PostMapping("/orders")
    @Operation(summary = "Tạo đơn và lấy link thanh toán PayOS")
    public PaymentOrderDto createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createVipOrder(request.planId());
    }

    @GetMapping("/orders/{orderCode}")
    @Operation(summary = "Tra cứu một đơn; đơn còn treo sẽ được hỏi lại cổng thanh toán")
    public PaymentOrderDto checkOrder(@PathVariable Long orderCode) {
        return orderService.checkMyOrder(orderCode);
    }

    @PostMapping("/orders/{orderCode}/cancel")
    @Operation(summary = "Hủy một đơn chưa thanh toán")
    public PaymentOrderDto cancelOrder(@PathVariable Long orderCode) {
        return orderService.cancelMyOrder(orderCode);
    }

    /** Chỉ nhận id gói: số tháng và số tiền lấy từ máy chủ, không tin client. */
    public record CreateOrderRequest(@NotNull(message = "Thiếu gói cần mua") Long planId) {
    }
}
