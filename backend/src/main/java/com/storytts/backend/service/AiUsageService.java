package com.storytts.backend.service;

import com.storytts.backend.domain.AiUsage;
import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.repository.AiUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * <h2>Một chỗ đếm cho mọi hàng rào chi phí AI</h2>
 *
 * Hai đường tiêu tiền — nút "Nghe bằng AI" và trợ lý đọc truyện — trước đây đếm
 * bằng hai cách khác nhau, và <b>cả hai cách đều đếm lại được từ đầu</b>:
 *
 * <ul>
 *   <li>Đường TTS đếm số hàng trong {@code audio_files} mang tên người đọc. Mà
 *       những hàng ấy bị dọn mỗi lần người ta mở một phiên đăng nhập mới, nên
 *       đăng xuất rồi đăng nhập là nạp đầy hạn mức.</li>
 *   <li>Đường trợ lý đếm bằng một bảng băm trong bộ nhớ, mất sạch sau mỗi lần
 *       tiến trình khởi động lại — mà gói miễn phí của Render thì ngủ sau 15
 *       phút vắng khách và tỉnh dậy là một tiến trình mới.</li>
 * </ul>
 *
 * <p>Cả hai giờ đếm trên bảng {@code ai_usage}: một dòng cho một lượt, chỉ ghi
 * thêm, không có đường xóa. Bảng nằm trên đĩa nên nó sống qua mọi lần khởi động
 * lại, và không dòng nào của nó bị cuốn theo việc dọn dẹp bản audio.
 *
 * <h3>Chiếm chỗ trước, hỏi sau</h3>
 * {@link #reserve} ghi dòng sổ <i>trước</i> rồi mới hỏi "dòng này là lượt thứ
 * mấy". Thứ tự ngược đời ấy là thứ đóng lại khe hở giữa lúc đếm và lúc ghi —
 * xem {@link AiUsageRepository#rankForUser}. Hết phần thì dòng vừa ghi được
 * hoàn ngay, nên nó không giữ chỗ của ai.
 *
 * <h3>Hoàn lượt</h3>
 * Hoàn là <b>ghi thêm một mốc thời gian</b>, không phải xóa dòng: "lượt này đã
 * xảy ra rồi được trả lại" và "lượt này chưa từng xảy ra" là hai câu khác nhau,
 * và chỉ câu đầu là sự thật. Nhờ vậy bảng còn đọc được như một sổ chi phí — biết
 * cả phần đã tiêu lẫn phần đã hỏng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageService {

    /** Hạn mức tính theo ngày ở Việt Nam, không theo UTC. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Giá trị hạn mức nghĩa là "không giới hạn". */
    public static final int UNLIMITED = -1;

    private final AiUsageRepository repository;

    /** Phía nào của hàng rào đã chạm trần. */
    public enum Scope {
        /** Người này hết phần của mình. */
        USER,
        /** Cả hệ thống đã đạt trần trong ngày. */
        GLOBAL
    }

    /**
     * Cách dựng ngoại lệ từ chối, do bên gọi cung cấp.
     *
     * <p>Lớp này không biết nói với người dùng bằng lời nào: "hết lượt tạo audio"
     * và "hết lượt hỏi trợ lý" là hai câu khác nhau, và cả hai đều thuộc về nơi
     * hiểu ngữ cảnh của mình. Nó chỉ biết ai còn chỗ và ai không.
     */
    @FunctionalInterface
    public interface Denial {
        RuntimeException build(Scope scope, int limit);
    }

    /**
     * Chiếm một lượt trong ngày hôm nay.
     *
     * <p>Chạy trong giao dịch của bên gọi nếu có: với đường TTS, dòng sổ và bản
     * ghi {@code audio_files} phải cùng sống hoặc cùng chết. Yêu cầu hỏng sau
     * bước này thì lượt cũng phải biến mất theo, và việc cuộn giao dịch lo đúng
     * điều đó mà không cần ai nhớ hoàn tay.
     *
     * @param personalLimit hạn mức của riêng người này; {@code null} hoặc
     *                      {@link #UNLIMITED} nghĩa là không chặn cá nhân — lượt
     *                      vẫn được ghi sổ, để bảng chi phí còn đầy đủ
     * @param globalLimit   trần chung của cả hệ thống trong ngày
     * @param denial        cách dựng ngoại lệ khi hết phần
     * @return id dòng sổ, để gắn với bản audio sau khi nó được tạo
     */
    @Transactional
    public Long reserve(Long userId, AiUsageKind kind, Long chapterId,
                        Integer personalLimit, int globalLimit, Denial denial) {

        Instant since = startOfToday();

        AiUsage row = repository.save(AiUsage.builder()
                .userId(userId)
                .kind(kind)
                .chapterId(chapterId)
                .build());

        // Trần chung xét trước hạn mức cá nhân: khi cả hệ thống đã hết thì "bạn
        // hết lượt" là một câu trả lời sai, và không phải lỗi của người đang hỏi.
        if (globalLimit != UNLIMITED) {
            long rank = repository.rankGlobal(kind, since, row.getId());
            if (rank > globalLimit) {
                refund(row, "tran chung cua he thong");
                throw denial.build(Scope.GLOBAL, globalLimit);
            }
        }

        if (personalLimit != null && personalLimit != UNLIMITED) {
            long rank = repository.rankForUser(userId, kind, since, row.getId());
            if (rank > personalLimit) {
                refund(row, "het han muc trong ngay");
                throw denial.build(Scope.USER, personalLimit);
            }
            log.info("Lượt {} của người dùng {} — {}/{} trong hôm nay",
                    kind, userId, rank, personalLimit);
        }

        return row.getId();
    }

    /**
     * Gắn dòng sổ với bản audio mà nó đã trả tiền để dựng.
     *
     * <p>Tách khỏi {@link #reserve} vì bản audio chưa tồn tại lúc chiếm chỗ — và
     * thứ tự ấy là cố ý: hạn mức phải được hỏi <i>trước</i> khi có gì được tạo
     * ra. Mối nối này chỉ để về sau còn hoàn lượt được cho đúng bản hỏng.
     */
    @Transactional
    public void linkToAudio(Long usageId, Long audioFileId) {
        if (usageId == null || audioFileId == null) {
            return;
        }
        repository.findById(usageId).ifPresent(row -> {
            row.setAudioFileId(audioFileId);
            repository.save(row);
        });
    }

    /**
     * Trả lại lượt đã tiêu cho một bản dựng không đến được tai ai.
     *
     * <h3>Vì sao đi chung giao dịch với bên gọi</h3>
     * Mọi chỗ gọi tới đây đều đang ghi một trạng thái hỏng — FAILED, hoặc STALE
     * ngay lúc dựng xong. Hoàn lượt phải là <i>cùng một sự kiện</i> với việc ấy:
     * hoàn cho một bản mà rốt cuộc không được đánh dấu hỏng là tặng không một
     * lượt, còn đánh dấu hỏng mà quên hoàn là lấy mất của người đọc một lượt họ
     * không tiêu được.
     *
     * <p>Giao dịch của bên gọi cuộn ngược thì cả hai cùng mất, và bản ghi ở lại
     * PROCESSING — {@link com.storytts.backend.service.tts.StaleGenerationReconciler}
     * bắt lại nó ở lần khởi động sau và hoàn lượt khi ấy. Không có đường nào để
     * một lượt hỏng ở lại trong sổ mãi mãi.
     *
     * <p>Gọi lại nhiều lần không cộng thêm gì: truy vấn chỉ tìm dòng chưa hoàn.
     */
    @Transactional
    public void refundForAudio(Long audioFileId, String reason) {
        if (audioFileId == null) {
            return;
        }
        repository.findFirstByAudioFileIdAndRefundedAtIsNull(audioFileId)
                .ifPresent(row -> {
                    refund(row, reason);
                    log.info("Hoàn lượt tạo audio cho người dùng {} (bản {}): {}",
                            row.getUserId(), audioFileId, reason);
                });
    }

    /**
     * Hoàn một lượt đã giữ, theo id dòng sổ mà {@link #reserve} trả về.
     *
     * <h3>Vì sao cần đường này bên cạnh {@link #refundForAudio}</h3>
     * Đường kia tra ngược từ bản audio, thứ chỉ có với {@link AiUsageKind#TTS}.
     * Một câu hỏi gửi trợ lý không sinh ra tài sản nào để tra ngược — nó chỉ có
     * đúng cái id mà bên gọi đang cầm — nên nó cần một đường thẳng.
     *
     * <p>Chỗ dùng: nhà cung cấp AI hỏng giữa chừng. Người đọc gõ một câu, hạn
     * mức bị trừ, rồi Gemini hết giờ chờ. Giữ lại lượt ấy là bắt người ta trả
     * tiền cho một lỗi không phải của họ — và ở đường hỗ trợ thì nó còn tệ hơn
     * một bậc, vì người bị trừ oan là người đang cần giúp.
     *
     * <p>Không xóa dòng, đúng chính sách của V9: "đã hỏng nên trả lại lượt" là
     * một sự kiện thứ hai, không phải bằng chứng rằng sự kiện thứ nhất chưa từng
     * xảy ra. Phép đếm hạn mức bỏ qua những dòng đã có mốc hoàn.
     *
     * <p>Gọi lại nhiều lần không cộng thêm gì: dòng đã hoàn thì bỏ qua.
     */
    @Transactional
    public void refundUsage(Long usageId, String reason) {
        if (usageId == null) {
            return;
        }
        repository.findById(usageId)
                .filter(row -> row.getRefundedAt() == null)
                .ifPresent(row -> {
                    refund(row, reason);
                    log.info("Hoàn lượt {} của người dùng {}: {}",
                            row.getKind(), row.getUserId(), reason);
                });
    }

    /**
     * Còn bao nhiêu lượt hôm nay.
     *
     * @return {@code null} khi không đặt hạn mức cá nhân nào
     */
    @Transactional(readOnly = true)
    public Integer remaining(Long userId, AiUsageKind kind,
                             Integer personalLimit, int globalLimit) {
        if (personalLimit == null || personalLimit == UNLIMITED) {
            return null;
        }
        Instant since = startOfToday();
        long used = repository.countForUser(userId, kind, since);
        int mine = (int) Math.max(personalLimit - used, 0);

        // Kẹp bởi trần chung: hứa 3 lượt trong khi cả hệ thống chỉ còn 1 thì con
        // số kia là một lời hứa sai, và người đọc phát hiện ra đúng lúc bấm.
        return Math.min(mine, globalRemaining(kind, globalLimit, since));
    }

    /** Phần còn lại của trần chung; {@link Integer#MAX_VALUE} nếu không đặt trần. */
    @Transactional(readOnly = true)
    public int globalRemaining(AiUsageKind kind, int globalLimit) {
        return globalRemaining(kind, globalLimit, startOfToday());
    }

    private int globalRemaining(AiUsageKind kind, int globalLimit, Instant since) {
        if (globalLimit == UNLIMITED) {
            return Integer.MAX_VALUE;
        }
        long used = repository.countAll(kind, since);
        return (int) Math.max(globalLimit - used, 0);
    }

    private void refund(AiUsage row, String reason) {
        row.setRefundedAt(Instant.now());
        row.setRefundReason(reason);
        repository.save(row);
    }

    /** 0 giờ sáng nay theo giờ Việt Nam — mốc chung của mọi hạn mức trong ngày. */
    public static Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }
}
