package com.storytts.backend.repository;

import com.storytts.backend.domain.AiUsage;
import com.storytts.backend.domain.AiUsageKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Sổ đếm lượt dùng AI. Chỉ có đường ghi thêm và đường đếm — không có đường xóa,
 * và đó là toàn bộ lý do bảng này tồn tại.
 */
public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    /**
     * Số lượt một người đã dùng kể từ mốc thời gian cho trước.
     *
     * <p>Dùng để hiển thị "hôm nay bạn còn N lượt". Việc chặn thì dùng
     * {@link #rankForUser} — xem lý do ở đó.
     */
    @Query("""
            SELECT COUNT(u) FROM AiUsage u
            WHERE u.userId = :userId
              AND u.kind = :kind
              AND u.refundedAt IS NULL
              AND u.createdAt >= :since
            """)
    long countForUser(@Param("userId") Long userId,
                      @Param("kind") AiUsageKind kind,
                      @Param("since") Instant since);

    /** Như trên, nhưng cho toàn bộ người dùng — trần chung của cả hệ thống. */
    @Query("""
            SELECT COUNT(u) FROM AiUsage u
            WHERE u.kind = :kind
              AND u.refundedAt IS NULL
              AND u.createdAt >= :since
            """)
    long countAll(@Param("kind") AiUsageKind kind, @Param("since") Instant since);

    /**
     * Dòng vừa ghi là lượt thứ mấy của người này trong ngày.
     *
     * <h3>Vì sao đếm tới một id chứ không đếm tất cả</h3>
     * Cách cũ là "đếm rồi mới ghi": hai request chạy song song cùng đọc thấy 2/3
     * lượt đã dùng, cả hai cùng thấy còn chỗ, và cả hai cùng ghi — hạn mức 3 cho
     * ra 4 lượt. Đọc trước rồi ghi sau thì luôn có khe hở ấy, dù khe hẹp tới đâu.
     *
     * <p>Ở đây thứ tự bị đảo lại: ghi trước để <i>chiếm chỗ</i>, rồi mới hỏi
     * "chỗ vừa chiếm là chỗ thứ mấy". Khóa chính tự tăng cho ra một thứ tự dứt
     * khoát, nên trong hai request đua nhau, đúng một request nhận số 4 và bị từ
     * chối — không phải cả hai cùng lọt, cũng không phải cả hai cùng bị đuổi.
     * Lượt bị từ chối được hoàn ngay tại chỗ, nên nó không chiếm phần của ai.
     *
     * @param upToId id của chính dòng vừa ghi, được tính vào kết quả
     */
    @Query("""
            SELECT COUNT(u) FROM AiUsage u
            WHERE u.userId = :userId
              AND u.kind = :kind
              AND u.refundedAt IS NULL
              AND u.createdAt >= :since
              AND u.id <= :upToId
            """)
    long rankForUser(@Param("userId") Long userId,
                     @Param("kind") AiUsageKind kind,
                     @Param("since") Instant since,
                     @Param("upToId") Long upToId);

    /** Cùng phép xếp chỗ ấy, nhưng trên hàng đợi chung của cả hệ thống. */
    @Query("""
            SELECT COUNT(u) FROM AiUsage u
            WHERE u.kind = :kind
              AND u.refundedAt IS NULL
              AND u.createdAt >= :since
              AND u.id <= :upToId
            """)
    long rankGlobal(@Param("kind") AiUsageKind kind,
                    @Param("since") Instant since,
                    @Param("upToId") Long upToId);

    /**
     * Lượt đã trả tiền cho một bản audio cụ thể, nếu nó chưa được hoàn.
     *
     * <p>Đường vào của việc hoàn lượt khi bản dựng hỏng. Trả về rỗng là chuyện
     * bình thường chứ không phải lỗi: bản do khu quản trị dựng không có dòng sổ
     * nào, và một lượt đã hoàn rồi thì không hoàn lần nữa.
     */
    Optional<AiUsage> findFirstByAudioFileIdAndRefundedAtIsNull(Long audioFileId);
}
