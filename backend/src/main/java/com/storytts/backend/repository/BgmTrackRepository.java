package com.storytts.backend.repository;

import com.storytts.backend.domain.BgmTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BgmTrackRepository extends JpaRepository<BgmTrack, Long> {

    /** Những bản người nghe được chọn, theo đúng thứ tự hiện trong ô chọn. */
    List<BgmTrack> findByActiveTrueOrderBySortOrderAscIdAsc();

    /** Cả bản đã tắt — khu quản trị cần thấy chúng để bật lại. */
    List<BgmTrack> findAllByOrderBySortOrderAscIdAsc();
}
