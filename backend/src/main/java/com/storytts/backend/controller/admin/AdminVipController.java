package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.VipOrderStatus;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.vip.VipOrderDto;
import com.storytts.backend.dto.vip.VipPlanDto;
import com.storytts.backend.dto.vip.VipPlanRequest;
import com.storytts.backend.service.VipOrderService;
import com.storytts.backend.service.VipPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quản trị gói VIP và đơn hàng.
 *
 * <p>Giá và kỳ hạn là dữ liệu, không phải hằng số trong mã: Admin tự mở gói một
 * tháng, ba tháng hay một năm với giá tùy ý. Việc cấp VIP vĩnh viễn bằng tay
 * vẫn nằm ở màn hình thành viên như cũ.
 */
@RestController
@RequestMapping("/api/admin/vip")
@RequiredArgsConstructor
@Tag(name = "Admin - VIP", description = "Gói VIP và đơn nâng cấp")
public class AdminVipController {

    private final VipPlanService planService;
    private final VipOrderService orderService;

    /* ---------------------------- Gói ---------------------------- */

    @GetMapping("/plans")
    @Operation(summary = "Mọi gói, gồm cả gói đã tắt")
    public List<VipPlanDto> plans() {
        return planService.listAll();
    }

    @PostMapping("/plans")
    @Operation(summary = "Tạo gói mới")
    public VipPlanDto create(@Valid @RequestBody VipPlanRequest request) {
        return planService.create(request);
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Sửa một gói")
    public VipPlanDto update(@PathVariable Long id, @Valid @RequestBody VipPlanRequest request) {
        return planService.update(id, request);
    }

    @PatchMapping("/plans/{id}/active")
    @Operation(summary = "Bật/tắt bán một gói")
    public VipPlanDto setActive(@PathVariable Long id, @RequestBody FlagRequest request) {
        return planService.setActive(id, request.value());
    }

    /* --------------------------- Đơn hàng --------------------------- */

    @GetMapping("/orders")
    @Operation(summary = "Đơn nâng cấp của mọi thành viên, mới nhất trước")
    public PageResponse<VipOrderDto> orders(@RequestParam(required = false) VipOrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.listAll(status, PageRequest.of(page, size));
    }

    @PostMapping("/orders/{orderCode}/refresh")
    @Operation(summary = "Hỏi lại cổng thanh toán tình trạng của một đơn còn treo")
    public VipOrderDto refresh(@PathVariable Long orderCode) {
        return orderService.refresh(orderCode);
    }

    /** Dùng chung dạng thân yêu cầu với các công tắc khác trong bảng quản trị. */
    public record FlagRequest(boolean value) {
    }
}
