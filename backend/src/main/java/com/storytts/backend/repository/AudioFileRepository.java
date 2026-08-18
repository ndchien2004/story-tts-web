package com.storytts.backend.repository;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    /**
     * <p><b>Phiên bản nội dung là một phần của khóa.</b> Trước đây khóa chỉ gồm
     * (chương, giọng, tốc độ), nên bản dựng theo nội dung cũ vẫn khớp và vẫn được
     * trả về; nó chỉ bị phát hiện sau đó bằng một phép so hash trong service. Đưa
     * phiên bản vào thẳng câu truy vấn khiến bản của phiên bản cũ đơn giản là
     * không còn nằm trong tập kết quả — không có gì để phát hiện nữa.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.source = com.storytts.backend.domain.AudioSource.TTS
              AND a.contentVersion = :contentVersion
              AND a.voice = :voice
              AND a.speed = :speed
              AND (:requesterId IS NULL AND a.requestedBy IS NULL
                   OR a.requestedBy.id = :requesterId)
            ORDER BY a.createdAt DESC
            LIMIT 1
            """)
    Optional<AudioFile> findTtsCache(@Param("chapterId") Long chapterId,
                                     @Param("contentVersion") Integer contentVersion,
                                     @Param("voice") String voice,
                                     @Param("speed") Integer speed,
                                     @Param("requesterId") Long requesterId);

    /**
     * Đánh dấu mọi bản audio của một chương là lỗi thời, trừ bản của phiên bản
     * hiện tại.
     *
     * <p>Chạy ngay trong giao dịch sửa chương, nên "Admin bấm lưu" và "audio cũ
     * hết hiệu lực" là <b>một</b> sự kiện chứ không phải hai. Cách khác là để các
     * đường đọc tự lọc theo phiên bản và không đụng gì tới trạng thái — nhưng khi
     * ấy cơ sở dữ liệu vẫn ghi READY cho một bản không ai được nghe, và bất kỳ
     * chỗ nào quên mất mệnh đề lọc sẽ lặng lẽ phục vụ nó trở lại. Ghi thẳng trạng
     * thái khiến bất biến nằm trong dữ liệu, không nằm trong trí nhớ của người
     * viết truy vấn tiếp theo.
     *
     * <p>Chỉ động tới READY và PROCESSING. FAILED không có gì để lỗi thời, còn
     * STALE thì đã lỗi thời rồi — ghi đè lên nó chỉ làm mới {@code updatedAt} và
     * đẩy lùi ngày dọn của một file lẽ ra đã đến hạn.
     *
     * <p>Bản đang PROCESSING cũng bị đánh dấu, và điều đó là cố ý: nó đang đọc
     * nội dung cũ, nên kết quả của nó đằng nào cũng không dùng được. Luồng nền
     * vẫn chạy nốt và tự so phiên bản một lần nữa lúc ghi kết quả — xem
     * {@code TtsGenerationRecords.markReady}. Hai lớp chặn cho cùng một tình
     * huống, vì chúng chặn ở hai thời điểm khác nhau: cái này bắt lúc Admin lưu,
     * cái kia bắt lúc lượt dựng kết thúc.
     *
     * <p>{@code updatedAt} phải được đặt tay ở đây: câu UPDATE hàng loạt đi thẳng
     * xuống cơ sở dữ liệu, không nạp thực thể nào, nên {@code @PreUpdate} của
     * {@link AudioFile} không chạy. Bỏ qua nó thì mọi bản lỗi thời giữ nguyên mốc
     * thời gian cũ, và hạn lưu giữ sẽ tính từ lần ghi trước đó thay vì từ lúc
     * chúng thành lỗi thời — bản vừa bị thay thế có thể bị dọn ngay lập tức, đúng
     * lúc còn người đang nghe nó.
     *
     * <p>Mốc thời gian là tham số chứ không phải {@code CURRENT_TIMESTAMP}: hàm
     * ấy cho ra {@code java.sql.Timestamp}, không gán được vào một trường
     * {@link java.time.Instant}.
     *
     * @return số bản vừa chuyển sang lỗi thời
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AudioFile a
               SET a.status = com.storytts.backend.domain.AudioStatus.STALE,
                   a.updatedAt = :now
             WHERE a.chapter.id = :chapterId
               AND a.status IN (com.storytts.backend.domain.AudioStatus.READY,
                                com.storytts.backend.domain.AudioStatus.PROCESSING)
               AND (a.contentVersion IS NULL OR a.contentVersion <> :currentVersion)
            """)
    int markSupersededStale(@Param("chapterId") Long chapterId,
                            @Param("currentVersion") Integer currentVersion,
                            @Param("now") Instant now);

    /**
     * Những bản audio <b>hiện tại</b> của một chương mà một người được phép thấy.
     *
     * <p>Khác {@link #findVisibleForChapter} ở đúng một mệnh đề, và đó là mệnh đề
     * cả tính năng này xoay quanh: {@code a.contentVersion = a.chapter.contentVersion}.
     * Bản không khớp phiên bản không phải là "bản cũ để tạm nghe" — nó không nằm
     * trong tập kết quả, chấm hết.
     *
     * <p>{@code IS NOT NULL} là thừa về mặt logic (null không bằng gì cả) nhưng
     * được viết ra để câu truy vấn tự nói lên rằng bản không rõ phiên bản cũng bị
     * loại — đó là những bản dựng từ trước khi có cột này.
     *
     * <p>Giữ lại cả PROCESSING: trang đọc cần thấy "đang dựng" để hiện đúng trạng
     * thái chờ. FAILED và STALE thì không.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.contentVersion IS NOT NULL
              AND a.contentVersion = a.chapter.contentVersion
              AND a.status IN (com.storytts.backend.domain.AudioStatus.READY,
                               com.storytts.backend.domain.AudioStatus.PROCESSING)
              AND (a.requestedBy IS NULL OR a.requestedBy.id = :userId)
            """)
    List<AudioFile> findCurrentForChapter(@Param("chapterId") Long chapterId,
                                          @Param("userId") Long userId);

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
     * Chương này có bản audio phát được ngay bây giờ không.
     *
     * <p>Bản sao một-chương của {@link #findChapterIdsWithReadyAudio}, cùng đúng
     * một định nghĩa "phát được": READY và đọc theo phiên bản nội dung hiện tại.
     * Có mặt để những đường trả về một dòng chương khỏi phải nạp cả danh sách bản
     * audio rồi đếm trong bộ nhớ chỉ để lấy một chữ có/không.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.status = com.storytts.backend.domain.AudioStatus.READY
              AND a.contentVersion IS NOT NULL
              AND a.contentVersion = a.chapter.contentVersion
            """)
    boolean hasCurrentReadyAudio(@Param("chapterId") Long chapterId);

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
     *
     * <p>"Dùng được" giờ có nghĩa hẹp hơn: READY <i>và</i> đúng phiên bản nội dung
     * hiện tại của chương. Không siết chỗ này thì danh sách chương treo biểu tượng
     * loa lên một chương mà bấm vào không có gì để nghe — mà đó lại là chỗ người
     * đọc nhìn thấy đầu tiên.
     */
    @Query("""
            SELECT DISTINCT a.chapter.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds
              AND a.status = com.storytts.backend.domain.AudioStatus.READY
              AND a.contentVersion IS NOT NULL
              AND a.contentVersion = a.chapter.contentVersion
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
              AND a.contentVersion IS NOT NULL
              AND a.contentVersion = a.chapter.contentVersion
            ORDER BY a.id
            """)
    List<Object[]> findReadyAudioIds(@Param("chapterIds") java.util.Collection<Long> chapterIds);

    long countBySource(AudioSource source);

    /**
     * Số chương đã có ít nhất một bản audio dùng được — tử số của "độ phủ audio".
     *
     * <p>Cùng định nghĩa "dùng được" với {@link #findChapterIdsWithReadyAudio}:
     * một chương có bản audio đọc theo nội dung đã bị sửa thì chưa được phủ, và
     * con số trên bảng điều khiển không nên nói ngược lại.
     */
    @Query("""
            SELECT COUNT(DISTINCT a.chapter.id) FROM AudioFile a
            WHERE a.status = com.storytts.backend.domain.AudioStatus.READY
              AND a.contentVersion IS NOT NULL
              AND a.contentVersion = a.chapter.contentVersion
            """)
    long countChaptersWithReadyAudio();

    /**
     * Bản đã lỗi thời đủ lâu để dọn được.
     *
     * <p>Không dọn ngay lúc chuyển sang lỗi thời, vì lúc ấy rất có thể đang có
     * người nghe dở đúng file đó — trang đọc cố ý không cắt ngang họ. Hạn lưu giữ
     * là khoảng đệm cho việc ấy, và cũng là khoảng để một bản sửa nhầm còn kịp
     * hoàn tác trước khi file biến mất.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.status = com.storytts.backend.domain.AudioStatus.STALE
              AND a.updatedAt < :before
            """)
    List<AudioFile> findStaleUpdatedBefore(@Param("before") Instant before);

    long countByStatus(AudioStatus status);

    /**
     * Những bản đang ở một trạng thái nhất định.
     *
     * <p>Có mặt để {@code StaleGenerationReconciler} khỏi phải gọi {@code findAll()}
     * rồi lọc trong bộ nhớ. Khác biệt ấy đáng kể hơn vẻ ngoài của nó: lượt dọn ấy
     * chạy mỗi lần ứng dụng khởi động, mà trên nền tảng ngủ khi vắng khách thì
     * "mỗi lần khởi động" nghĩa là vài chục lần một ngày — và heap chỉ có 224MB.
     */
    List<AudioFile> findByStatus(AudioStatus status);

    /** Các chương (trong danh sách truyền vào) đang có bản audio ở trạng thái đã cho. */
    @Query("""
            SELECT DISTINCT a.chapter.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds AND a.status = :status
            """)
    List<Long> findChapterIdsByStatus(@Param("chapterIds") java.util.Collection<Long> chapterIds,
                                      @Param("status") AudioStatus status);

    void deleteByChapterId(Long chapterId);
}
