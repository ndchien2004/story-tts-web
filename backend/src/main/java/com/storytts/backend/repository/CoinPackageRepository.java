package com.storytts.backend.repository;

import com.storytts.backend.domain.CoinPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoinPackageRepository extends JpaRepository<CoinPackage, Long> {

    /** Những gói đang bán, theo thứ tự hiển thị quản trị viên đặt. */
    List<CoinPackage> findByActiveTrueOrderBySortOrderAscIdAsc();

    /** Cả gói đã tắt — khu quản trị cần thấy chúng để bật lại. */
    List<CoinPackage> findAllByOrderBySortOrderAscIdAsc();
}
