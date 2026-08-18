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

    /**
     * Chương kèm bộ lọc theo truyện và theo tình trạng audio — màn hình quản lý audio.
     * <p>
     * Điều kiện audio phải nằm trong SQL chứ không lọc sau khi lấy trang về: cả mục đích
     * của màn hình này là tìm những chương còn thiếu audio, lọc sau khi phân trang sẽ ra
     * những trang vơi đầy thất thường và tổng số đếm sai.
     *
     * <p><b>"Đã có audio" nghĩa là có bản READY đọc theo phiên bản nội dung hiện tại</b>
     * — đúng cùng một định nghĩa với {@code AudioFileRepository.findChapterIdsWithReadyAudio},
     * và hai chỗ phải nói cùng một câu. Nếu chỗ này còn tính cả bản của phiên bản
     * cũ thì bộ lọc "còn thiếu audio" sẽ giấu đi đúng những chương vừa được sửa
     * nội dung — tức là giấu đi đúng danh sách việc mà màn hình này sinh ra để
     * đưa cho quản trị viên, ngay lúc nó dài ra.
     *
     * @param withAudio null = không lọc, true = đã có audio, false = còn thiếu
     */
    @Query(value = """
            SELECT c FROM Chapter c
            JOIN FETCH c.story s
            WHERE (:storyId IS NULL OR s.id = :storyId)
              AND (:withAudio IS NULL
                   OR (:withAudio = TRUE AND EXISTS (
                        SELECT 1 FROM AudioFile a
                        WHERE a.chapter = c
                          AND a.status = com.storytts.backend.domain.AudioStatus.READY
                          AND a.contentVersion IS NOT NULL
                          AND a.contentVersion = c.contentVersion))
                   OR (:withAudio = FALSE AND NOT EXISTS (
                        SELECT 1 FROM AudioFile a
                        WHERE a.chapter = c
                          AND a.status = com.storytts.backend.domain.AudioStatus.READY
                          AND a.contentVersion IS NOT NULL
                          AND a.contentVersion = c.contentVersion)))
            ORDER BY s.title ASC, c.chapterNumber ASC
            """,
            countQuery = """
                    SELECT COUNT(c) FROM Chapter c
                    WHERE (:storyId IS NULL OR c.story.id = :storyId)
                      AND (:withAudio IS NULL
                           OR (:withAudio = TRUE AND EXISTS (
                                SELECT 1 FROM AudioFile a
                                WHERE a.chapter = c
                                  AND a.status = com.storytts.backend.domain.AudioStatus.READY
                          AND a.contentVersion IS NOT NULL
                          AND a.contentVersion = c.contentVersion))
                           OR (:withAudio = FALSE AND NOT EXISTS (
                                SELECT 1 FROM AudioFile a
                                WHERE a.chapter = c
                                  AND a.status = com.storytts.backend.domain.AudioStatus.READY
                          AND a.contentVersion IS NOT NULL
                          AND a.contentVersion = c.contentVersion)))
                    """)
    org.springframework.data.domain.Page<Chapter> searchForAudioAdmin(
            @Param("storyId") Long storyId,
            @Param("withAudio") Boolean withAudio,
            org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE Chapter c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
