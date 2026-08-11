package com.storytts.backend.repository;

import com.storytts.backend.domain.VipPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VipPlanRepository extends JpaRepository<VipPlan, Long> {

    /** Danh sách bán ra: chỉ gói đang bật, theo thứ tự Admin đã sắp. */
    List<VipPlan> findByActiveTrueOrderBySortOrderAscPriceVndAsc();

    /** Bảng quản trị thấy cả gói đã tắt. */
    List<VipPlan> findAllByOrderBySortOrderAscPriceVndAsc();

    boolean existsByNameIgnoreCase(String name);
}
