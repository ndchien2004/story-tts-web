package com.storytts.backend.repository;

import com.storytts.backend.domain.GiftCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Gift code.
 *
 * <h3>Vì sao chiếm một lượt là câu UPDATE chứ không phải setter</h3>
 * Cách hiển nhiên là {@code code.setUsedCount(code.getUsedCount() + 1)} sau khi
 * đã kiểm {@code usedCount < maxUses}. Cách ấy sai, và sai theo kiểu chỉ lộ ra
 * đúng vào lúc tệ nhất — khi mã vừa được công bố và cả nghìn người cùng gõ nó:
 *
 * <pre>
 *   maxUses = 100, used_count = 99. Hai mươi request tới gần như cùng lúc.
 *
 *   R1..R20: đọc used_count → 99      ← chưa ai kịp ghi
 *   R1..R20: thấy 99 &lt; 100, đều hợp lệ
 *   R1..R20: ghi 100                  ← đè lên nhau
 *
 *   Kết quả: 119 người nhận Xu từ một mã giới hạn 100 lượt, và cột đếm nói
 *   rằng đúng 100 người đã nhận.
 * </pre>
 *
 * {@link #claimUse} gộp việc kiểm tra và việc ghi làm một câu lệnh. InnoDB khóa
 * dòng mã trong lúc chạy nó, nên request thứ hai phải chờ, rồi đọc lại
 * {@code used_count} <i>đã tăng</i> và thấy điều kiện không còn đúng — nó nhận
 * về 0 dòng và bị từ chối. Đúng cùng một hình dạng lời giải với phép trừ Xu ở
 * {@link WalletRepository#debit}, và vì đúng cùng một lý do.
 *
 * <p>Câu lệnh ấy còn mang theo cả điều kiện thời gian và cờ bật/tắt. Không phải
 * để tiết kiệm một lần đọc, mà vì một mã hết hạn <i>giữa</i> lúc kiểm và lúc ghi
 * thì cũng không được phát nữa: kiểm ở Java rồi ghi bằng một câu lệnh không điều
 * kiện là để lại đúng khe hở ấy.
 */
public interface GiftCodeRepository extends JpaRepository<GiftCode, Long> {

    /** Tra theo mã <b>đã chuẩn hóa</b> — xem {@code GiftCodes.normalize}. */
    Optional<GiftCode> findByCode(String code);

    boolean existsByCode(String code);

    /** Mã trùng, bỏ qua chính dòng đang sửa. */
    boolean existsByCodeAndIdNot(String code, Long id);

    /**
     * Chiếm một lượt đổi, và chỉ chiếm khi mọi điều kiện còn đúng.
     *
     * <p>Đây là chỗ duy nhất trong hệ thống làm {@code used_count} tăng lên. Bốn
     * điều kiện trong mệnh đề WHERE tương ứng với bốn tình trạng từ chối —
     * DISABLED, SCHEDULED, EXPIRED, EXHAUSTED — nên nhận về 0 dòng nghĩa là một
     * trong bốn, và bên gọi đọc lại dòng ấy để biết là cái nào mà nói cho người
     * dùng. Việc đọc lại chỉ để soạn câu lỗi, không tham gia vào quyết định.
     *
     * <p>{@code now} truyền vào chứ không lấy từ {@code CURRENT_TIMESTAMP}: hàm
     * ấy của JPQL trả về {@code java.sql.Timestamp}, không so được với một trường
     * {@code Instant} — cùng ghi chú với {@code WalletRepository}.
     *
     * @return 1 nếu đã chiếm được một lượt, 0 nếu mã không đổi được lúc này
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE GiftCode g
               SET g.usedCount = g.usedCount + 1,
                   g.updatedAt = :now
             WHERE g.id = :id
               AND g.enabled = true
               AND (g.startAt IS NULL OR g.startAt <= :now)
               AND (g.endAt IS NULL OR g.endAt >= :now)
               AND (g.maxUses IS NULL OR g.usedCount < g.maxUses)
            """)
    int claimUse(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Bảng quản trị: lọc theo từ khóa và theo tình trạng.
     *
     * <p>Tình trạng không phải một cột nên không lọc bằng dấu bằng được; nó được
     * dịch thành các mệnh đề tương ứng ngay tại đây. Cách khác là nạp hết rồi lọc
     * trong Java, nhưng thế thì phân trang mất nghĩa — trang 1 của một bộ lọc sẽ
     * được cắt ra từ một tập đã bị lọc bớt sau khi cắt.
     *
     * <p>{@code status} null là không lọc. Các mốc {@code from}/{@code to} lọc
     * theo <i>ngày tạo</i>, không theo hạn dùng: câu hỏi của quản trị viên ở ô ấy
     * là "những mã tôi phát ra trong tuần trước", không phải "những mã hết hạn
     * tuần trước".
     */
    @Query("""
            SELECT g FROM GiftCode g
             WHERE (:keyword IS NULL OR :keyword = ''
                    OR LOWER(g.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(g.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
               AND (:from IS NULL OR g.createdAt >= :from)
               AND (:to IS NULL OR g.createdAt <= :to)
               AND (:status IS NULL
                    OR (:status = 'DISABLED' AND g.enabled = false)
                    OR (:status = 'SCHEDULED' AND g.enabled = true
                        AND g.startAt IS NOT NULL AND g.startAt > :now)
                    OR (:status = 'EXPIRED' AND g.enabled = true
                        AND (g.startAt IS NULL OR g.startAt <= :now)
                        AND g.endAt IS NOT NULL AND g.endAt < :now)
                    OR (:status = 'EXHAUSTED' AND g.enabled = true
                        AND (g.startAt IS NULL OR g.startAt <= :now)
                        AND (g.endAt IS NULL OR g.endAt >= :now)
                        AND g.maxUses IS NOT NULL AND g.usedCount >= g.maxUses)
                    OR (:status = 'ACTIVE' AND g.enabled = true
                        AND (g.startAt IS NULL OR g.startAt <= :now)
                        AND (g.endAt IS NULL OR g.endAt >= :now)
                        AND (g.maxUses IS NULL OR g.usedCount < g.maxUses)))
            """)
    Page<GiftCode> search(@Param("keyword") String keyword,
                          @Param("status") String status,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          @Param("now") Instant now,
                          Pageable pageable);

    /* ------------------------------------------------------------------ */
    /* Thống kê                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Số mã đang đổi được ngay lúc này.
     *
     * <p>Đếm bằng cùng bộ điều kiện với {@code claimUse}, nên con số hiện trên
     * trang tổng quan và câu trả lời mà người dùng nhận được khi gõ mã không thể
     * nói hai chuyện khác nhau.
     */
    @Query("""
            SELECT COUNT(g) FROM GiftCode g
             WHERE g.enabled = true
               AND (g.startAt IS NULL OR g.startAt <= :now)
               AND (g.endAt IS NULL OR g.endAt >= :now)
               AND (g.maxUses IS NULL OR g.usedCount < g.maxUses)
            """)
    long countActive(@Param("now") Instant now);
}
