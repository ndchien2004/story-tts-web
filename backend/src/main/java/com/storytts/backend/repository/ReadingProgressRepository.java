package com.storytts.backend.repository;

import com.storytts.backend.domain.ReadingProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByUserIdAndChapterId(Long userId, Long chapterId);

    /** Toàn bộ tiến độ của một người trong một truyện — để đánh dấu chương đã đọc. */
    @Query("""
            SELECT p FROM ReadingProgress p
            WHERE p.user.id = :userId AND p.chapter.story.id = :storyId
            """)
    List<ReadingProgress> findByUserAndStory(@Param("userId") Long userId,
                                             @Param("storyId") Long storyId);

    /** Chương đọc gần nhất của một truyện — để nút "Đọc tiếp" nhảy đúng chỗ. */
    @Query("""
            SELECT p FROM ReadingProgress p
            WHERE p.user.id = :userId AND p.chapter.story.id = :storyId
            ORDER BY p.updatedAt DESC
            LIMIT 1
            """)
    Optional<ReadingProgress> findLatestByUserAndStory(@Param("userId") Long userId,
                                                       @Param("storyId") Long storyId);

    /** Lịch sử đọc/nghe gần đây. */
    @Query("""
            SELECT p FROM ReadingProgress p
            JOIN FETCH p.chapter c
            JOIN FETCH c.story s
            WHERE p.user.id = :userId
            ORDER BY p.updatedAt DESC
            """)
    List<ReadingProgress> findRecentByUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Toàn bộ tiến độ của một người, mới nhất trước.
     * <p>
     * Tủ truyện cần đếm số chương đã đọc xong của từng truyện nên không cắt bớt được
     * như {@link #findRecentByUser}: cắt đi là đếm thiếu và truyện đọc xong bị xếp nhầm.
     */
    @Query("""
            SELECT p FROM ReadingProgress p
            JOIN FETCH p.chapter c
            JOIN FETCH c.story s
            WHERE p.user.id = :userId
            ORDER BY p.updatedAt DESC
            """)
    List<ReadingProgress> findAllByUser(@Param("userId") Long userId);

    /** Thể loại của các truyện người dùng đọc gần đây — đầu vào cho gợi ý. */
    @Query("""
            SELECT DISTINCT s.genre.id FROM ReadingProgress p
            JOIN p.chapter c
            JOIN c.story s
            WHERE p.user.id = :userId AND s.genre IS NOT NULL
            """)
    List<Long> findRecentGenreIds(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT c.story.id FROM ReadingProgress p
            JOIN p.chapter c
            WHERE p.user.id = :userId
            """)
    List<Long> findReadStoryIds(@Param("userId") Long userId);
}
