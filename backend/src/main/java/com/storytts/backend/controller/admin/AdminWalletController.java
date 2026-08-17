package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.wallet.CoinPackageDto;
import com.storytts.backend.dto.wallet.CoinPackageRequest;
import com.storytts.backend.service.CoinPackageService;
import com.storytts.backend.service.WalletAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Khu quản trị của ví Xu: bảng giá gói nạp, và chỉnh Xu bằng tay.
 *
 * <p>Đường chỉnh tay tồn tại vì mọi hệ thống có tiền đều cần nó: một người mất Xu
 * do sự cố, một lần hoàn tiền đã thỏa thuận qua điện thoại, một khoản đền bù. Nó
 * đi qua đúng sổ cái như mọi giao dịch khác — không có cửa sau nào sửa thẳng số
 * dư mà không để lại dòng nào.
 */
@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
@Tag(name = "Admin - Ví Xu", description = "Gói nạp Xu và điều chỉnh số dư")
public class AdminWalletController {

    private final CoinPackageService packageService;
    private final WalletAdminService walletAdminService;

    /* ----- Gói nạp ----- */

    @GetMapping("/packages")
    @Operation(summary = "Toàn bộ gói nạp Xu, gồm cả gói đã tắt")
    public List<CoinPackageDto> packages() {
        return packageService.listAll();
    }

    @PostMapping("/packages")
    @Operation(summary = "Tạo gói nạp Xu")
    public CoinPackageDto create(@Valid @RequestBody CoinPackageRequest request) {
        return packageService.create(request);
    }

    @PutMapping("/packages/{id}")
    @Operation(summary = "Sửa gói nạp Xu")
    public CoinPackageDto update(@PathVariable Long id,
                                 @Valid @RequestBody CoinPackageRequest request) {
        return packageService.update(id, request);
    }

    /** Ngừng bán mà vẫn giữ nguyên các đơn đã phát sinh từ gói này. */
    @PatchMapping("/packages/{id}/active")
    @Operation(summary = "Bật/tắt một gói nạp Xu")
    public CoinPackageDto setActive(@PathVariable Long id,
                                    @Valid @RequestBody ActiveRequest request) {
        return packageService.setActive(id, request.active());
    }

    /* ----- Điều chỉnh số dư ----- */

    /**
     * Cộng hoặc trừ Xu của một tài khoản.
     *
     * <p>{@code amount} có dấu: dương là cộng, âm là trừ. Lý do bắt buộc phải
     * nhập, và nó đi thẳng vào dòng sổ cái mà người dùng đọc được — người bị trừ
     * Xu có quyền biết vì sao.
     */
    @PostMapping("/users/{userId}/adjust")
    @Operation(summary = "Cộng/trừ Xu cho một tài khoản, có ghi sổ cái")
    public AdjustResponse adjust(@PathVariable Long userId,
                                 @Valid @RequestBody AdjustRequest request) {
        long balance = walletAdminService.adjust(userId, request.amount(), request.reason());
        return new AdjustResponse(userId, balance);
    }

    public record ActiveRequest(@NotNull(message = "Thiếu trạng thái") Boolean active) {
    }

    public record AdjustRequest(
            @NotNull(message = "Vui lòng nhập số Xu") Long amount,
            @Size(max = 200, message = "Lý do tối đa 200 ký tự") String reason) {
    }

    public record AdjustResponse(Long userId, long balance) {
    }
}
