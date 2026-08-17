package com.storytts.backend.dto.wallet;

import com.storytts.backend.domain.WalletTransaction;

import java.time.Instant;

/**
 * Một dòng trong trang lịch sử giao dịch Xu.
 *
 * <p>{@code amount} giữ nguyên dấu như trong sổ cái, nên giao diện chỉ cần so với
 * 0 để chọn màu và dấu cộng/trừ, không phải tra bảng theo {@code type}.
 *
 * <p>Cặp {@code balanceBefore}/{@code balanceAfter} được đưa ra ngoài chứ không
 * giấu đi: người dùng nhìn thấy số dư đổi từ đâu sang đâu thì tự đối chiếu được,
 * và một câu hỏi tự trả lời được là một câu hỏi không đến hộp thư hỗ trợ.
 */
public record WalletTransactionDto(
        Long id,
        String type,
        String typeLabel,
        long amount,
        long balanceBefore,
        long balanceAfter,
        String description,
        Instant createdAt
) {

    public static WalletTransactionDto from(WalletTransaction tx) {
        return new WalletTransactionDto(
                tx.getId(),
                tx.getType().name(),
                tx.getType().getLabel(),
                tx.getAmount(),
                tx.getBalanceBefore(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getCreatedAt());
    }
}
