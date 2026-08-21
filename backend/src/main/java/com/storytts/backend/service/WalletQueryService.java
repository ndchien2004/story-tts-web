package com.storytts.backend.service;

import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.wallet.WalletDto;
import com.storytts.backend.dto.wallet.WalletTransactionDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần chỉ đọc của ví, dành cho người đang đăng nhập.
 *
 * <p>Tách khỏi {@link WalletService} vì hai lớp phục vụ hai loại người gọi.
 * {@code WalletService} là viên gạch cộng trừ, được gọi từ bên trong những giao
 * dịch đang mở của phần mua chương và phần nạp tiền; nó không biết ai đang đăng
 * nhập và không nên biết. Lớp này thì ngược lại: nó chỉ phục vụ endpoint, và mọi
 * câu hỏi của nó bắt đầu bằng "của tôi".
 */
@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final WalletService walletService;
    private final WalletTransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public WalletDto myWallet() {
        return new WalletDto(walletService.balanceOf(currentUserId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionDto> myTransactions(int page, int size) {
        return PageResponse.from(
                transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                        currentUserId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))),
                WalletTransactionDto::from);
    }

    /** Id lấy từ token, không phải từ bảng {@code users} — không tốn câu SELECT nào. */
    private Long currentUserId() {
        return currentUserService.currentUserId()
                .orElseThrow(() -> new BadRequestException("Bạn cần đăng nhập."));
    }
}
