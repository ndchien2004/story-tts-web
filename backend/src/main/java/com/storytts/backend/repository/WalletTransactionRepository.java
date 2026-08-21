package com.storytts.backend.repository;

import com.storytts.backend.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /**
     * Lịch sử giao dịch của một người, mới nhất trước.
     *
     * <h3>Vì sao có {@code IdDesc} ở cuối tên</h3>
     * {@code created_at} là {@code datetime(6)}, và hai dòng sổ ghi trong cùng
     * một micro giây thì <i>bằng nhau</i> theo cột ấy. Khi đó thứ tự trả về do
     * cơ sở dữ liệu tự chọn — không có gì bảo đảm nó ổn định, và không có gì
     * bảo đảm nó đúng chiều thời gian.
     *
     * <p>Đó không phải một chuyện chỉ xảy ra trong bài kiểm. Nạp Xu rồi mua
     * ngay một chương là hai lần ghi cách nhau vài chục micro giây, và người
     * dùng sẽ thấy sổ cái của mình đảo ngược: dòng "trừ 30" nằm trên dòng
     * "cộng 100" đã sinh ra nó, với {@code balance_before}/{@code balance_after}
     * đọc ra như một chuỗi đứt gãy.
     *
     * <p>{@code id} là {@code bigint auto_increment}, nên nó phá được mọi thế
     * hòa và phá theo đúng chiều đã ghi. Cùng lập luận đã viết ở V15 cho
     * {@code support_messages}: thứ tự nằm ở {@code id}, không nằm ở đồng hồ.
     */
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

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
