package com.storytts.backend.repository;

import com.storytts.backend.domain.VipOrder;
import com.storytts.backend.domain.VipOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VipOrderRepository extends JpaRepository<VipOrder, Long> {

    Optional<VipOrder> findByOrderCode(Long orderCode);

    boolean existsByOrderCode(Long orderCode);

    List<VipOrder> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    /** Bảng quản trị: mọi đơn, lọc được theo trạng thái. */
    @Query("""
            select o from VipOrder o
              join fetch o.user u
             where (:status is null or o.status = :status)
             order by o.createdAt desc
            """)
    Page<VipOrder> search(@Param("status") VipOrderStatus status, Pageable pageable);

    long countByStatus(VipOrderStatus status);

    @Query("select coalesce(sum(o.amountVnd), 0) from VipOrder o where o.status = :status")
    long sumAmountByStatus(@Param("status") VipOrderStatus status);
}
