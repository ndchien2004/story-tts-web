package com.storytts.backend.repository;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    List<AudioFile> findByChapterId(Long chapterId);

    Optional<AudioFile> findFirstByChapterIdAndSourceAndStatus(Long chapterId, AudioSource source, AudioStatus status);

    /**
     * Tra cứu cache TTS trong phạm vi của một người.
     *
     * <p>{@code requesterId} null nghĩa là khu quản trị: chỉ những bản không mang
     * tên ai mới được tái sử dụng. Người đọc thì chỉ gặp lại bản của chính mình —
     * bản người khác dựng không phải thứ họ được nghe, nên cũng không phải thứ
     * dùng lại để khỏi gọi API.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.source = com.storytts.backend.domain.AudioSource.TTS
              AND a.voice = :voice
              AND a.speed = :speed
              AND (:requesterId IS NULL AND a.requestedBy IS NULL
                   OR a.requestedBy.id = :requesterId)
            ORDER BY a.createdAt DESC
            LIMIT 1
            """)
    Optional<AudioFile> findTtsCache(@Param("chapterId") Long chapterId,
                                     @Param("voice") String voice,
                                     @Param("speed") Integer speed,
                                     @Param("requesterId") Long requesterId);

    /**
     * Những bản audio một người được phép thấy ở một chương: bản của khu quản trị,
     * cộng bản do chính người ấy dựng.
     *
     * <p>{@code userId} null là Khách chưa đăng nhập — chỉ còn phần của quản trị.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND (a.requestedBy IS NULL OR a.requestedBy.id = :userId)
            """)
    List<AudioFile> findVisibleForChapter(@Param("chapterId") Long chapterId,
                                          @Param("userId") Long userId);

    /**
     * Phần audio thuộc về khu quản trị: bản tải lên và bản admin tự dựng.
     *
     * <p>Bản do người đọc bấm tạo là việc riêng của họ, không phải kho của quản trị.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.requestedBy IS NULL
            """)
    List<AudioFile> findAdminOwnedForChapter(@Param("chapterId") Long chapterId);

    /** Mọi bản một người đọc đã tự dựng — dùng để dọn khi phiên đăng nhập kết thúc. */
    List<AudioFile> findByRequestedById(Long userId);

    boolean existsByChapterIdAndStatus(Long chapterId, AudioStatus status);

    /**
     * Số bản audio một người đọc đã tự dựng kể từ mốc thời gian cho trước.
     *
     * <p>Bỏ bản FAILED: người đọc trả lượt cho một bản nghe được, không cho một
     * lần hỏng của nhà cung cấp. Bản PROCESSING vẫn tính — API đã gọi rồi.
     *
     * <p>{@code a.requestedBy.id} so thẳng cột khóa ngoại, không sinh join.
     */
    @Query("""
            SELECT COUNT(a) FROM AudioFile a
            WHERE a.requestedBy.id = :userId
              AND a.source = com.storytts.backend.domain.AudioSource.TTS
              AND a.status <> com.storytts.backend.domain.AudioStatus.FAILED
              AND a.createdAt >= :since
            """)
    long countReaderRequestsBy(@Param("userId") Long userId, @Param("since") Instant since);

    /**
     * Tổng số bản audio do người đọc dựng kể từ mốc thời gian cho trước — trần
     * chung của toàn hệ thống.
     *
     * <p>{@code requestedBy IS NOT NULL} lọc ra đúng phần do người đọc gây ra:
     * một lô do Admin dựng không có tên người nên không bị tính vào đây, đúng ý
     * "khu quản trị không bị hạn mức của người đọc chặn".
     */
    @Query("""
            SELECT COUNT(a) FROM AudioFile a
            WHERE a.requestedBy IS NOT NULL
              AND a.source = com.storytts.backend.domain.AudioSource.TTS
              AND a.status <> com.storytts.backend.domain.AudioStatus.FAILED
              AND a.createdAt >= :since
            """)
    long countReaderRequests(@Param("since") Instant since);

    /**
     * Các chương (trong danh sách truyền vào) đã có audio dùng được — một truy vấn duy nhất,
     * để danh sách chương không sinh N+1 query.
     */
    @Query("""
            SELECT DISTINCT a.chapter.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds
              AND a.status = com.storytts.backend.domain.AudioStatus.READY
            """)
    List<Long> findChapterIdsWithReadyAudio(@Param("chapterIds") java.util.Collection<Long> chapterIds);

    /**
     * Cặp (chương, bản audio dùng được) cho danh sách chương truyền vào.
     *
     * <p>Trang quản trị cần đúng id của bản audio để dựng đường nghe thử ngay
     * trên dòng, mà biết "chương này có audio" thì chưa đủ để phát nó.
     */
    @Query("""
            SELECT a.chapter.id, a.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds
              AND a.status = com.storytts.backend.domain.AudioStatus.READY
            ORDER BY a.id
            """)
    List<Object[]> findReadyAudioIds(@Param("chapterIds") java.util.Collection<Long> chapterIds);

    long countBySource(AudioSource source);

    /** Số chương đã có ít nhất một bản audio dùng được — tử số của "độ phủ audio". */
    @Query("""
            SELECT COUNT(DISTINCT a.chapter.id) FROM AudioFile a
            WHERE a.status = com.storytts.backend.domain.AudioStatus.READY
            """)
    long countChaptersWithReadyAudio();

    long countByStatus(AudioStatus status);

    /** Các chương (trong danh sách truyền vào) đang có bản audio ở trạng thái đã cho. */
    @Query("""
            SELECT DISTINCT a.chapter.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds AND a.status = :status
            """)
    List<Long> findChapterIdsByStatus(@Param("chapterIds") java.util.Collection<Long> chapterIds,
                                      @Param("status") AudioStatus status);

    void deleteByChapterId(Long chapterId);
}
