package com.storytts.backend.repository;

import com.storytts.backend.domain.ViewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ViewEventRepository extends JpaRepository<ViewEvent, Long> {

    /**
     * Mốc thời gian và loại của mọi lượt truy cập từ {@code since} tới nay.
     *
     * <p>Gom theo ngày ở tầng Java chứ không dùng {@code GROUP BY DATE(...)} trong SQL,
     * vì "ngày nào" phụ thuộc múi giờ: hàm ngày của cơ sở dữ liệu sẽ cắt theo múi giờ của
     * máy chủ CSDL, còn biểu đồ thì phải cắt theo múi giờ mà người xem đang sống. Cửa sổ
     * chỉ vài tuần nên số dòng kéo về là nhỏ, và câu truy vấn chạy trên chỉ mục created_at.
     */
    @Query("SELECT e.createdAt, e.type FROM ViewEvent e WHERE e.createdAt >= :since")
    List<Object[]> findTimestampsSince(@Param("since") Instant since);
}
