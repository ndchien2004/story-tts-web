package com.storytts.backend.controller;

import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.payment.PaymentOrderDto;
import com.storytts.backend.dto.wallet.CoinPackageDto;
import com.storytts.backend.dto.wallet.WalletDto;
import com.storytts.backend.dto.wallet.WalletTransactionDto;
import com.storytts.backend.service.CoinPackageService;
import com.storytts.backend.service.PaymentOrderService;
import com.storytts.backend.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ví Xu từ phía người đọc: số dư, lịch sử, và nạp thêm.
 *
 * <p>Bảng giá gói nạp để công khai, cùng lý do với bảng giá VIP — giá là thứ
 * người ta cần biết trước khi quyết định có đăng ký tài khoản hay không. Mọi thứ
 * còn lại đòi đăng nhập, vì ví luôn thuộc về một người cụ thể.
 *
 * <p>Đường nạp Xu dùng lại nguyên vẹn cơ chế đơn hàng của VIP, nên đơn nạp trả về
 * cùng một {@link PaymentOrderDto} và trang kết quả thanh toán không cần biết
 * người dùng vừa mua gì.
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Ví Xu", description = "Số dư, lịch sử giao dịch và nạp Xu")
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final CoinPackageService coinPackageService;
    private final PaymentOrderService orderService;

    @GetMapping
    @Operation(summary = "Số dư Xu của tài khoản đang đăng nhập")
    public WalletDto myWallet() {
        return walletQueryService.myWallet();
    }

    @GetMapping("/transactions")
    @Operation(summary = "Lịch sử giao dịch Xu, mới nhất trước")
    public PageResponse<WalletTransactionDto> myTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletQueryService.myTransactions(page, size);
    }

    @GetMapping("/packages")
    @Operation(summary = "Các gói nạp Xu đang bán")
    public List<CoinPackageDto> packages() {
        return coinPackageService.listActive();
    }

    @PostMapping("/orders")
    @Operation(summary = "Tạo đơn nạp Xu và lấy link thanh toán PayOS")
    public PaymentOrderDto createOrder(@RequestBody CreateCoinOrderRequest request) {
        return orderService.createCoinOrder(request.packageId());
    }

    /** Chỉ nhận id gói: số Xu và số tiền lấy từ máy chủ, không tin client. */
    public record CreateCoinOrderRequest(
            @NotNull(message = "Thiếu gói nạp cần mua") Long packageId) {
    }
}
