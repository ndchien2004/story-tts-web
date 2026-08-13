package com.storytts.backend.repository;

import com.storytts.backend.domain.ViewEvent;
import com.storytts.backend.domain.ViewType;
import org.springframework.data.domain.Pageable;
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

    /**
     * Bảng xếp hạng truyện trong một khoảng thời gian: {@code [storyId, số lượt]},
     * nhiều nhất trước.
     *
     * <p>Đếm ngay trong cơ sở dữ liệu chứ không kéo hết sự kiện về rồi gom ở Java như
     * {@link #findTimestampsSince}: bên kia mỗi lần chỉ lấy vài tuần cho biểu đồ quản trị,
     * còn truy vấn này chạy trên mọi lượt vào trang chủ và bảng {@code view_events} là
     * bảng lớn nhanh nhất trong hệ thống. Chốt "ngày nào" ở đây cũng không cần múi giờ:
     * mốc thời gian do tầng service tính rồi truyền xuống.
     *
     * <p>{@code type} để null nghĩa là gộp cả đọc lẫn nghe.
     */
    @Query("""
            SELECT e.storyId, COUNT(e)
            FROM ViewEvent e
            WHERE e.createdAt >= :since
              AND (:type IS NULL OR e.type = :type)
            GROUP BY e.storyId
            ORDER BY COUNT(e) DESC, e.storyId ASC
            """)
    List<Object[]> rankStoriesSince(@Param("since") Instant since,
                                    @Param("type") ViewType type,
                                    Pageable pageable);

    /** {@code [storyId, tổng số lượt]} trên toàn bộ lịch sử — dùng để dựng lại cột cộng dồn. */
    @Query("SELECT e.storyId, COUNT(e) FROM ViewEvent e GROUP BY e.storyId")
    List<Object[]> countGroupedByStory();

    /** {@code [chapterId, tổng số lượt]} trên toàn bộ lịch sử. */
    @Query("SELECT e.chapterId, COUNT(e) FROM ViewEvent e GROUP BY e.chapterId")
    List<Object[]> countGroupedByChapter();
}
