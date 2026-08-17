package com.storytts.backend.repository;

import com.storytts.backend.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Ví Xu.
 *
 * <h3>Vì sao cộng trừ Xu là câu UPDATE chứ không phải setter</h3>
 * Cách hiển nhiên là {@code wallet.setBalance(wallet.getBalance() - giá)}. Cách ấy
 * sai, và sai theo kiểu chỉ lộ ra khi có người dùng thật:
 *
 * <pre>
 *   Người dùng có 10 Xu, bấm mở hai chương 10 Xu gần như cùng lúc.
 *
 *   Request A: đọc số dư → 10
 *   Request B: đọc số dư → 10      ← chưa ai kịp ghi
 *   Request A: ghi 10 - 10 = 0
 *   Request B: ghi 10 - 10 = 0     ← đè lên, và mở được chương thứ hai miễn phí
 * </pre>
 *
 * Phép trừ ở đây gộp việc kiểm tra và việc ghi làm một câu lệnh. InnoDB khóa dòng
 * ví trong lúc chạy nó, nên request thứ hai phải chờ, rồi đọc lại số dư <i>đã
 * giảm</i> và thấy điều kiện {@code balance >= :amount} không còn đúng — nó nhận
 * về 0 dòng và bị từ chối. Số dư không thể âm, và không cần khóa nào do mã nguồn
 * tự đặt.
 *
 * <p>Chọn cách này thay vì khóa bi quan ({@code SELECT ... FOR UPDATE}) hay khóa
 * lạc quan ({@code @Version}): khóa bi quan cần thêm một vòng gọi và giữ khóa lâu
 * hơn cần thiết, còn khóa lạc quan đòi một vòng thử lại ở tầng trên cho một tình
 * huống mà ở đây không cần thử lại — hết Xu là hết Xu.
 */
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    @Query("SELECT w.balance FROM Wallet w WHERE w.user.id = :userId")
    Optional<Long> findBalanceByUserId(@Param("userId") Long userId);

    /**
     * Trừ Xu, và chỉ trừ khi còn đủ.
     *
     * <p>{@code now} được truyền vào chứ không lấy từ {@code CURRENT_TIMESTAMP}:
     * hàm ấy của JPQL trả về {@code java.sql.Timestamp}, không gán được vào một
     * trường {@code Instant}. Và {@code @PreUpdate} của entity cũng không cứu
     * được ở đây — câu UPDATE hàng loạt đi thẳng xuống cơ sở dữ liệu, không đi qua
     * vòng đời entity nào.
     *
     * @return 1 nếu đã trừ, 0 nếu không đủ số dư (hoặc chưa có ví)
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Wallet w
               SET w.balance = w.balance - :amount,
                   w.updatedAt = :now
             WHERE w.user.id = :userId
               AND w.balance >= :amount
            """)
    int debit(@Param("userId") Long userId, @Param("amount") long amount,
              @Param("now") Instant now);

    /**
     * Cộng Xu.
     *
     * <p>Không có điều kiện nào ngoài việc ví phải tồn tại: cộng thì không thể
     * làm số dư âm. Vẫn là một câu UPDATE chứ không phải đọc-rồi-ghi, vì hai lần
     * cộng đồng thời cũng đè lên nhau y hệt hai lần trừ.
     *
     * @return 1 nếu đã cộng, 0 nếu người này chưa có ví
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Wallet w
               SET w.balance = w.balance + :amount,
                   w.updatedAt = :now
             WHERE w.user.id = :userId
            """)
    int credit(@Param("userId") Long userId, @Param("amount") long amount,
               @Param("now") Instant now);
}
