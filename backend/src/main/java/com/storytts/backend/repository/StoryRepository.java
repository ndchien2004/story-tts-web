package com.storytts.backend.repository;

import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.StoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /**
     * Truy vấn dùng chung cho danh sách truyện: tìm kiếm theo tên/tác giả,
     * lọc theo thể loại và trạng thái, có phân trang + sắp xếp do Pageable quyết định.
     */
    @Query("""
            SELECT s FROM Story s
            LEFT JOIN s.author a
            LEFT JOIN s.genre g
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:genreId IS NULL OR g.id = :genreId)
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<Story> search(@Param("keyword") String keyword,
                       @Param("genreId") Long genreId,
                       @Param("status") StoryStatus status,
                       Pageable pageable);

    @Query("SELECT s FROM Story s LEFT JOIN FETCH s.author LEFT JOIN FETCH s.genre WHERE s.id = :id")
    Optional<Story> findDetailById(@Param("id") Long id);

    /** Tên truyện không phải khóa duy nhất, nên chỉ dùng để dò dữ liệu mẫu lúc khởi động. */
    Optional<Story> findFirstByTitle(String title);

    /**
     * Gợi ý truyện cùng thể loại mà người dùng chưa đọc.
     */
    @Query("""
            SELECT s FROM Story s
            WHERE s.genre.id IN :genreIds
              AND s.id NOT IN :excludedStoryIds
            ORDER BY s.viewCount DESC, s.createdAt DESC
            """)
    List<Story> findRecommendations(@Param("genreIds") Collection<Long> genreIds,
                                    @Param("excludedStoryIds") Collection<Long> excludedStoryIds,
                                    Pageable pageable);

    @Modifying
    @Query("UPDATE Story s SET s.viewCount = s.viewCount + 1 WHERE s.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Truyện được xem nhiều nhất — biểu đồ "top truyện" ở trang thống kê. */
    @Query("SELECT s FROM Story s ORDER BY s.viewCount DESC, s.title ASC")
    List<Story> findTopByViewCount(Pageable pageable);

    long countByStatus(StoryStatus status);

    long countByGenreId(Long genreId);

    long countByAuthorId(Long authorId);
}
