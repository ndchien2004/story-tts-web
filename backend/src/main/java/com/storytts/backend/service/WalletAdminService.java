package com.storytts.backend.service;

import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản trị viên cộng hoặc trừ Xu bằng tay.
 *
 * <h3>Vì sao đường này tồn tại</h3>
 * Mọi hệ thống có tiền đều cần nó: một người mất Xu vì sự cố, một lần hoàn tiền
 * đã thỏa thuận qua điện thoại, một khoản đền bù. Không có đường chính thức thì
 * việc ấy vẫn xảy ra — chỉ là bằng một câu UPDATE gõ tay vào cơ sở dữ liệu, không
 * ai ghi lại và không ai tra được.
 *
 * <h3>Vì sao nó không phải một cửa sau</h3>
 * Nó đi qua đúng {@link WalletService} như mọi giao dịch khác, nên nó cũng để lại
 * đúng một dòng sổ cái với số dư trước/sau. Không có đường nào trong hệ thống này
 * sửa được {@code wallets.balance} mà không sinh ra dòng giải thích đi kèm — kể cả
 * đường của quản trị viên.
 *
 * <h3>Vì sao chưa có REFUND riêng</h3>
 * Hoàn tiền là một quy tắc nghiệp vụ chứ không phải một thao tác kỹ thuật, và quy
 * tắc ấy chưa được định ra: hoàn Xu mua chương thì có thu lại quyền đọc không, có
 * hạn bao lâu, ai được duyệt. Chừng nào chưa trả lời, {@code ADMIN_ADJUSTMENT}
 * kèm lý do viết tay là cách trung thực nhất để ghi lại việc đã làm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletAdminService {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    /**
     * @param amount có dấu — dương là cộng, âm là trừ
     * @param reason lý do, đi thẳng vào dòng sổ cái người dùng đọc được
     * @return số dư sau điều chỉnh
     */
    @Transactional
    public long adjust(Long userId, long amount, String reason) {
        if (amount == 0) {
            throw new BadRequestException("Số Xu điều chỉnh phải khác 0.");
        }
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("người dùng", userId);
        }

        // Id của người thao tác được ghi vào reference_id, nên mỗi lần chỉnh tay
        // đều truy được về một tài khoản quản trị cụ thể.
        Long actorId = currentUserService.currentUserId().orElse(null);
        String description = describe(reason);

        long balance = amount > 0
                ? walletService.credit(userId, amount, WalletTransactionType.ADMIN_ADJUSTMENT,
                        WalletReferenceType.ADMIN, actorId, description)
                : walletService.debit(userId, -amount, WalletTransactionType.ADMIN_ADJUSTMENT,
                        WalletReferenceType.ADMIN, actorId, description);

        log.info("Quản trị viên {} điều chỉnh {} Xu cho người dùng {} — còn {} Xu",
                actorId, amount, userId, balance);
        return balance;
    }

    private String describe(String reason) {
        return reason == null || reason.isBlank()
                ? "Điều chỉnh từ quản trị viên"
                : reason.trim();
    }
}
