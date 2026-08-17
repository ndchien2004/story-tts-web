package com.storytts.backend.repository;

import com.storytts.backend.domain.PaymentOrder;
import com.storytts.backend.domain.PaymentOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderCode(Long orderCode);

    boolean existsByOrderCode(Long orderCode);

    List<PaymentOrder> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    /** Bảng quản trị: mọi đơn, lọc được theo trạng thái. */
    @Query("""
            select o from PaymentOrder o
              join fetch o.user u
             where (:status is null or o.status = :status)
             order by o.createdAt desc
            """)
    Page<PaymentOrder> search(@Param("status") PaymentOrderStatus status, Pageable pageable);

    long countByStatus(PaymentOrderStatus status);

    @Query("select coalesce(sum(o.amountVnd), 0) from PaymentOrder o where o.status = :status")
    long sumAmountByStatus(@Param("status") PaymentOrderStatus status);

    /**
     * Giành quyền cộng hạn VIP cho một đơn — thành công đúng một lần.
     *
     * <p>Webhook của PayOS và trang kết quả (người mua bấm quay lại) là hai đường
     * độc lập cùng dẫn tới lượt cộng hạn, và chúng chạy được đồng thời. Đọc trạng
     * thái rồi mới ghi thì cả hai đều kịp thấy PENDING và cộng hạn hai lần.
     *
     * <p>Câu UPDATE này gộp việc kiểm tra và việc ghi làm một. MySQL khóa dòng
     * cho tới hết giao dịch đang gọi, nên luồng thứ hai phải chờ, rồi thấy PAID và
     * nhận về 0. Bên gọi chỉ cộng hạn khi nhận về 1.
     *
     * <p>Cố ý <b>không</b> có {@code clearAutomatically}. Dọn persistence context ở
     * đây sẽ tách luôn cái đơn mà bên gọi đang cầm, và {@code order.getUser()} của
     * nó là một proxy chưa nạp — lần chạm kế tiếp ném {@code LazyInitializationException}
     * ngay giữa lúc cộng tiền. Bên gọi tự chép trạng thái mới vào bản trong bộ nhớ,
     * nên không cần dọn gì.
     *
     * @return 1 nếu giành được, 0 nếu đơn đã PAID từ trước
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            update PaymentOrder o
               set o.status = com.storytts.backend.domain.PaymentOrderStatus.PAID,
                   o.paidAt = :now
             where o.id = :id
               and o.status <> com.storytts.backend.domain.PaymentOrderStatus.PAID
            """)
    int markPaidIfPending(@Param("id") Long id, @Param("now") Instant now);
}
