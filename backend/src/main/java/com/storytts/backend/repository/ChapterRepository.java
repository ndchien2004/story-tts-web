package com.storytts.backend.repository;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByStoryIdOrderByChapterNumberAsc(Long storyId);

    Optional<Chapter> findByStoryIdAndChapterNumber(Long storyId, Integer chapterNumber);

    boolean existsByStoryIdAndChapterNumber(Long storyId, Integer chapterNumber);

    @Query("SELECT c FROM Chapter c JOIN FETCH c.story WHERE c.id = :id")
    Optional<Chapter> findDetailById(@Param("id") Long id);

    /** Chương kế tiếp — phục vụ nút "chương sau" và chế độ nghe liên tục. */
    @Query("""
            SELECT c FROM Chapter c
            WHERE c.story.id = :storyId AND c.chapterNumber > :currentNumber
            ORDER BY c.chapterNumber ASC
            LIMIT 1
            """)
    Optional<Chapter> findNext(@Param("storyId") Long storyId, @Param("currentNumber") Integer currentNumber);

    @Query("""
            SELECT c FROM Chapter c
            WHERE c.story.id = :storyId AND c.chapterNumber < :currentNumber
            ORDER BY c.chapterNumber DESC
            LIMIT 1
            """)
    Optional<Chapter> findPrevious(@Param("storyId") Long storyId, @Param("currentNumber") Integer currentNumber);

    @Query("SELECT COALESCE(MAX(c.chapterNumber), 0) FROM Chapter c WHERE c.story.id = :storyId")
    Integer findMaxChapterNumber(@Param("storyId") Long storyId);

    long countByStoryId(Long storyId);

    /**
     * Đếm số chương cho nhiều truyện trong MỘT truy vấn.
     * Tránh N+1 query khi render danh sách truyện (yêu cầu tải dưới 2 giây
     */
    @Query("SELECT c.story.id, COUNT(c) FROM Chapter c WHERE c.story.id IN :storyIds GROUP BY c.story.id")
    List<Object[]> countGroupedByStoryIds(@Param("storyIds") Collection<Long> storyIds);

    long countByAccessLevel(AccessLevel accessLevel);

    @Modifying
    @Query("UPDATE Chapter c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
