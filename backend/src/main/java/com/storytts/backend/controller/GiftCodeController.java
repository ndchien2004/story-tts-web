package com.storytts.backend.controller;

import com.storytts.backend.dto.gift.RedeemResultDto;
import com.storytts.backend.service.GiftCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Người đọc đổi gift code lấy Xu.
 *
 * <h3>Đúng một trường đi vào, và đó là điểm chính</h3>
 * Request chỉ mang {@code code}. Không có {@code userId} — người nhận là người
 * đang đăng nhập, đọc từ token trong {@code GiftCodeService}. Không có
 * {@code amount} — số Xu lấy từ dòng gift code trong cơ sở dữ liệu.
 *
 * <p>Nhận hai trường ấy từ client sẽ là hai cửa hậu: một cái cho phép cộng Xu vào
 * ví người khác, một cái cho phép tự chọn mình được bao nhiêu. Chúng không được
 * kiểm rồi bỏ qua — chúng không tồn tại trong hình dạng dữ liệu, nên không có
 * đường nào để quên kiểm.
 *
 * <p>Không nằm trong danh sách {@code permitAll} của {@code SecurityConfig}, nên
 * nó rơi vào {@code anyRequest().authenticated()}: request không mang token bị
 * chặn từ tầng filter, trước khi có controller nào chạy.
 *
 * <p>Đường này cũng nằm sau {@code RateLimitFilter} như mọi endpoint khác — đáng
 * kể ở đây vì gõ mò mã là một việc làm được bằng cách thử thật nhiều lần.
 */
@RestController
@RequestMapping("/api/gift-codes")
@RequiredArgsConstructor
@Tag(name = "Gift code", description = "Đổi gift code lấy Xu")
public class GiftCodeController {

    private final GiftCodeService giftCodeService;

    /**
     * Đổi một mã.
     *
     * <p>Thất bại trả về lỗi có mã riêng — {@code INVALID_GIFT_CODE},
     * {@code GIFT_CODE_EXPIRED}, {@code GIFT_CODE_ALREADY_REDEEMED}... — xem
     * {@code GiftCodeException} và {@code GlobalExceptionHandler}.
     */
    @PostMapping("/redeem")
    @Operation(summary = "Đổi gift code, cộng Xu vào ví của tài khoản đang đăng nhập")
    public RedeemResultDto redeem(@Valid @RequestBody RedeemRequest request) {
        return giftCodeService.redeem(request.code());
    }

    /** Chỉ mã: người nhận và số Xu đều do máy chủ quyết định. */
    public record RedeemRequest(
            @NotBlank(message = "Vui lòng nhập gift code")
            @Size(max = 64, message = "Gift code tối đa 64 ký tự")
            String code) {
    }
}
