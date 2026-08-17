package com.storytts.backend.repository;

import com.storytts.backend.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /** Lịch sử giao dịch của một người, mới nhất trước. */
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Tổng số Xu đã đi qua sổ cái của một người.
     *
     * <p>Đây là vế thứ hai của bất biến {@code SUM(amount) = wallets.balance}.
     * Vì {@code amount} có dấu, phép kiểm chỉ là một câu tổng — không phải cộng
     * phần dương rồi trừ phần âm theo từng loại giao dịch.
     *
     * <p>Dùng để đối chiếu, không dùng để quyết định có đủ tiền hay không: câu
     * hỏi ấy đi qua {@code wallets.balance}, thứ có chỉ mục và không phải quét
     * toàn bộ lịch sử.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t WHERE t.user.id = :userId")
    long sumAmountByUserId(@Param("userId") Long userId);
}
