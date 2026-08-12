package com.storytts.backend.repository;

import com.storytts.backend.domain.RatingComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface RatingCommentRepository extends JpaRepository<RatingComment, Long> {

    @Query("""
            SELECT rc FROM RatingComment rc
            JOIN FETCH rc.user
            WHERE rc.story.id = :storyId
            ORDER BY rc.createdAt DESC
            """)
    Page<RatingComment> findByStory(@Param("storyId") Long storyId, Pageable pageable);

    @Query("SELECT AVG(rc.rating) FROM RatingComment rc WHERE rc.story.id = :storyId AND rc.rating IS NOT NULL")
    Double averageRating(@Param("storyId") Long storyId);

    @Query("SELECT COUNT(rc) FROM RatingComment rc WHERE rc.story.id = :storyId AND rc.rating IS NOT NULL")
    long countRatings(@Param("storyId") Long storyId);

    long countByStoryIdAndUserIdIn(Long storyId, Collection<Long> userIds);

    /**
     * Lượt chấm sao của một người cho một truyện — theo thiết kế thì nhiều nhất một dòng.
     *
     * <p>Bảng này giữ cả bình luận lẫn điểm trên cùng một dòng, nên "một người
     * một điểm" không diễn tả được bằng một ràng buộc unique thông thường: người
     * đọc vẫn được bình luận nhiều lần, chỉ điểm là một lần.
     * {@code RatingCommentService.create} là chỗ giữ quy tắc đó.
     */
    Optional<RatingComment> findFirstByStoryIdAndUserIdAndRatingIsNotNull(Long storyId, Long userId);

    /**
     * Mọi bình luận của mọi truyện — màn hình kiểm duyệt bên quản trị.
     * <p>
     * Có {@code countQuery} riêng vì truy vấn chính dùng JOIN FETCH: đếm mà kéo theo
     * cả hai bảng liên kết là thừa, và Hibernate cũng không cho FETCH trong câu đếm.
     * <p>
     * Nội dung bình luận phải {@code CAST(... AS String)} trước khi hạ chữ thường:
     * cột này khai báo {@code @Lob} nên Hibernate coi là CLOB, mà {@code lower()} thì
     * chỉ nhận chuỗi thường.
     */
    @Query(value = """
            SELECT rc FROM RatingComment rc
            JOIN FETCH rc.user u
            JOIN FETCH rc.story s
            WHERE (:storyId IS NULL OR s.id = :storyId)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(CAST(rc.comment AS String)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY rc.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(rc) FROM RatingComment rc
                    WHERE (:storyId IS NULL OR rc.story.id = :storyId)
                      AND (:keyword IS NULL OR :keyword = ''
                           OR LOWER(CAST(rc.comment AS String)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(rc.user.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(rc.user.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(rc.story.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """)
    Page<RatingComment> search(@Param("keyword") String keyword,
                               @Param("storyId") Long storyId,
                               Pageable pageable);
}
