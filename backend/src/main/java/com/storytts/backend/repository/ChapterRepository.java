package com.storytts.backend.repository;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByStoryIdOrderByChapterNumberAsc(Long storyId);

    Optional<Chapter> findByStoryIdAndChapterNumber(Long storyId, Integer chapterNumber);

    boolean existsByStoryIdAndChapterNumber(Long storyId, Integer chapterNumber);

    @Query("SELECT c FROM Chapter c JOIN FETCH c.story WHERE c.id = :id")
    Optional<Chapter> findDetailById(@Param("id") Long id);

    /**
     * Một trang chương của một truyện, chỉ gồm những chương người gọi được thấy.
     *
     * <h3>Vì sao có phân trang</h3>
     * Trang chi tiết truyện từng trả về <b>toàn bộ</b> chương trong một lần. Với
     * sáu truyện mẫu bốn chương thì không ai thấy gì; với một truyện dịch 1.200
     * chương — đúng hình dạng nội dung mà trang này nhắm tới — đó là vài trăm KB
     * JSON cho mỗi lần mở trang, cộng hai truy vấn phụ ("chương nào đã có audio",
     * "chương nào đã mua") chạy trên đúng tập ấy.
     *
     * <h3>Vì sao lọc ngay trong SQL chứ không lọc sau khi lấy trang về</h3>
     * Lọc sau phân trang cho ra những trang vơi đầy thất thường và một tổng số
     * đếm sai — trang đầu còn 8 dòng, trang sau còn 20, mà thanh phân trang vẫn
     * hứa hẹn theo con số cũ.
     *
     * <p>{@code includeUnpublished} là quyền của người gọi, do service quyết
     * định; truyền xuống dưới dạng tham số thay vì viết hai câu truy vấn, để hai
     * bản không bao giờ lệch nhau về những mệnh đề còn lại.
     *
     * @param keyword tìm trong tiêu đề chương; null hoặc rỗng là không lọc
     * @param number  số hiệu chương chính xác; dành cho ô "nhảy tới chương…"
     */
    @Query("""
            SELECT c FROM Chapter c
            WHERE c.story.id = :storyId
              AND (:includeUnpublished = TRUE
                   OR (c.publishedAt IS NOT NULL AND c.publishedAt <= :now))
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:number IS NULL OR c.chapterNumber = :number)
            """)
    org.springframework.data.domain.Page<Chapter> findVisibleByStory(
            @Param("storyId") Long storyId,
            @Param("includeUnpublished") boolean includeUnpublished,
            @Param("now") Instant now,
            @Param("keyword") String keyword,
            @Param("number") Integer number,
            org.springframework.data.domain.Pageable pageable);

    /** Số chương người gọi được thấy — con số hiện trên thẻ truyện. */
    @Query("""
            SELECT COUNT(c) FROM Chapter c
            WHERE c.story.id = :storyId
              AND (:includeUnpublished = TRUE
                   OR (c.publishedAt IS NOT NULL AND c.publishedAt <= :now))
            """)
    long countVisibleByStory(@Param("storyId") Long storyId,
                             @Param("includeUnpublished") boolean includeUnpublished,
                             @Param("now") Instant now);

    /**
     * Chương kế tiếp — phục vụ nút "chương sau" và chế độ nghe liên tục.
     *
     * <p>Bỏ qua chương chưa đăng, và điều đó là bắt buộc chứ không phải cho
     * gọn: nút "chương sau" trỏ tới một bản nháp sẽ đưa người đọc thẳng vào một
     * trang 404, ngay giữa lúc họ đang nghe liên tục.
     */
    @Query("""
            SELECT c FROM Chapter c
            WHERE c.story.id = :storyId AND c.chapterNumber > :currentNumber
              AND (:includeUnpublished = TRUE
                   OR (c.publishedAt IS NOT NULL AND c.publishedAt <= :now))
            ORDER BY c.chapterNumber ASC
            LIMIT 1
            """)
    Optional<Chapter> findNext(@Param("storyId") Long storyId,
                               @Param("currentNumber") Integer currentNumber,
                               @Param("includeUnpublished") boolean includeUnpublished,
                               @Param("now") Instant now);

    @Query("""
            SELECT c FROM Chapter c
            WHERE c.story.id = :storyId AND c.chapterNumber < :currentNumber
              AND (:includeUnpublished = TRUE
                   OR (c.publishedAt IS NOT NULL AND c.publishedAt <= :now))
            ORDER BY c.chapterNumber DESC
            LIMIT 1
            """)
    Optional<Chapter> findPrevious(@Param("storyId") Long storyId,
                                   @Param("currentNumber") Integer currentNumber,
                                   @Param("includeUnpublished") boolean includeUnpublished,
                                   @Param("now") Instant now);

    @Query("SELECT COALESCE(MAX(c.chapterNumber), 0) FROM Chapter c WHERE c.story.id = :storyId")
    Integer findMaxChapterNumber(@Param("storyId") Long storyId);

    long countByStoryId(Long storyId);

    /**
     * Id của mọi chương thuộc một truyện.
     *
     * <p>Chỉ dùng ở đường xóa truyện, và chỉ để soạn sự kiện báo cho những trình
     * duyệt đang mở dở một chương của nó — xem {@code ContentDeleted}. Phải chạy
     * <b>trước</b> lệnh xóa: sau khi commit thì không còn hàng nào để hỏi, và bên
     * nhận sự kiện chạy sau đúng mốc ấy.
     *
     * <p>Lấy id chứ không lấy cả thực thể: một truyện nghìn chương thì đây là
     * một truy vấn trên chỉ mục trả về nghìn con số, còn cách kia là nạp nghìn
     * hàng đầy đủ kèm nội dung chương vào bộ nhớ để rồi vứt đi.
     */
    @Query("SELECT c.id FROM Chapter c WHERE c.story.id = :storyId")
    List<Long> findIdsByStoryId(@Param("storyId") Long storyId);

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
