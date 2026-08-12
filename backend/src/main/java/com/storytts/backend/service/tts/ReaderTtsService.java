package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsReaderStatusDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.exception.TtsQuotaExceededException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * <h2>Nút "Nghe bằng AI" của người đọc — mục 4.5 của đề bài</h2>
 *
 * Đề bài yêu cầu người đọc tự bấm để chuyển một chương thành audio. Nhưng mỗi
 * bản dựng mới là một file trên đĩa và một lần gọi API tính tiền, nên đường này
 * mở ra kèm một ngân sách chứ không mở trần:
 *
 * <ol>
 *   <li>phải đăng nhập — không có danh tính thì không đếm được hạn mức lên ai;</li>
 *   <li>hạn mức theo ngày cho từng người, VIP nhiều hơn Thành viên;</li>
 *   <li>trần chung cho cả hệ thống, làm cầu dao cuối;</li>
 *   <li>trần độ dài chương, vì chương càng dài càng nhiều lần gọi nhà cung cấp.</li>
 * </ol>
 *
 * <p><b>Bản đã có thì luôn miễn phí.</b> Ba mức chặn cuối nằm trong
 * {@link TtsService.ReaderBudget#beforeNewGeneration}, chỗ mà {@link TtsService}
 * chỉ gọi tới khi đã xác định không còn bản nào dùng lại được. Nghe lại một
 * chương, hay bấm trong lúc bản đang dựng dở, không đi qua đó.
 *
 * <p>Lớp này là <i>chính sách</i>, {@link TtsService} là <i>cơ chế</i>. Tách ra
 * thành hai bean chứ không phải hai method cùng bean vì hai lẽ: khu quản trị
 * phải giữ nguyên đường cũ, không hạn mức nào lẫn vào; và gọi chéo bean mới đi
 * qua proxy của Spring, nhờ đó {@code @Transactional} của {@code TtsService} còn
 * hiệu lực — mất nó thì {@code @TransactionalEventListener(AFTER_COMMIT)} của
 * {@link TtsGenerationWorker} lặng lẽ không chạy, và bản ghi nằm mãi ở PROCESSING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReaderTtsService {

    /** Hạn mức tính theo ngày ở Việt Nam, không theo UTC. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Giá trị hạn mức nghĩa là "không giới hạn". */
    private static final int UNLIMITED = -1;

    private final TtsService ttsService;
    private final TtsEngine ttsEngine;
    private final TtsProperties properties;
    private final AudioFileRepository audioFileRepository;
    private final CurrentUserService currentUserService;

    /**
     * Người đọc yêu cầu audio cho một chương.
     *
     * <p>Trả về ngay: trúng cache thì READY, còn lại là PROCESSING và trang đọc
     * hỏi lại trạng thái cho tới khi xong.
     */
    public AudioInfoDto request(Long chapterId) {
        TtsProperties.Reader reader = properties.reader();

        if (!reader.enabled()) {
            throw new TtsException("Chức năng tự tạo audio đang tạm tắt. "
                    + "Bạn vẫn nghe được những chương đã có audio.");
        }

        // Tầng URL đã bắt đăng nhập rồi (POST không nằm trong danh sách permitAll),
        // nhưng chặn lại ở đây nữa để service không phải tin vào cấu hình bên ngoài
        // — cùng lý lẽ với AccessControlService. Và chặn trước khi tra chương, để
        // người dò id chương chỉ nhận 401 chứ không phân biệt được 404 với 403.
        AppUserPrincipal principal = currentUserService.currentPrincipal()
                .orElseThrow(() -> new LoginRequiredException(
                        "Bạn cần đăng nhập để dùng chức năng tạo audio bằng AI."));

        // Có principal thì chắc chắn có id, nên tham chiếu này luôn tồn tại.
        User requester = currentUserService.currentUserReference().orElseThrow();

        // Giọng để null cho TtsService tự chọn giọng mặc định, và tốc độ luôn là
        // tốc độ mặc định của máy chủ: cả hai là thành phần khóa cache, nên để
        // người đọc chọn nghĩa là mỗi lựa chọn lại sinh thêm một file cho cùng
        // một chương.
        TtsRequest fixed = new TtsRequest(null, properties.defaultSpeed());

        return ttsService.requestForChapter(chapterId, fixed, new Budget(principal, requester));
    }

    /** Trạng thái để trang đọc biết nên hiện nút, hiện lời mời đăng nhập, hay không hiện gì. */
    public TtsReaderStatusDto status() {
        TtsProperties.Reader reader = properties.reader();
        boolean enabled = reader.enabled() && properties.enabled() && ttsEngine.hasAnyProvider();

        Optional<AppUserPrincipal> principal = currentUserService.currentPrincipal();
        Integer limit = principal.map(this::dailyQuotaFor).orElse(null);

        Integer remaining = null;
        if (limit != null) {
            Instant since = startOfToday();
            long used = audioFileRepository.countReaderRequestsBy(principal.get().getId(), since);
            // Kẹp bởi trần chung: hứa 3 lượt trong khi cả hệ thống chỉ còn 1 thì
            // con số kia là một lời hứa sai.
            remaining = Math.min(Math.max(limit - (int) used, 0), globalRemaining(since));
        }

        return new TtsReaderStatusDto(enabled, reader.maxChars(), limit, remaining);
    }

    /**
     * Ngân sách của một người đọc cụ thể.
     *
     * <p>Chỉ được hỏi tới khi {@link TtsService} đã xác định phải dựng bản mới,
     * nên mọi phép đếm ở đây đều tương ứng với chi phí thật.
     */
    private final class Budget implements TtsService.ReaderBudget {

        private final AppUserPrincipal principal;
        private final User requester;

        private Budget(AppUserPrincipal principal, User requester) {
            this.principal = principal;
            this.requester = requester;
        }

        @Override
        public void beforeNewGeneration(Chapter chapter) {
            TtsProperties.Reader reader = properties.reader();

            int length = chapter.getContent() == null ? 0 : chapter.getContent().length();
            if (length > reader.maxChars()) {
                throw new BadRequestException(
                        ("Chương này dài %,d ký tự, vượt mức %,d ký tự cho phép tự tạo audio. "
                                + "Mời bạn báo với ban quản trị để họ dựng sẵn bản audio cho chương.")
                                .formatted(length, reader.maxChars()));
            }

            Integer limit = dailyQuotaFor(principal);
            if (limit == null) {
                // Admin: đã có console riêng và cũng là người chịu trách nhiệm chi phí.
                return;
            }

            Instant since = startOfToday();

            // Trần chung xét trước hạn mức cá nhân: khi cả hệ thống đã hết thì
            // "bạn hết lượt" là một câu trả lời sai, và không phải lỗi của họ.
            if (globalRemaining(since) <= 0) {
                throw new TtsQuotaExceededException(
                        TtsQuotaExceededException.Scope.GLOBAL,
                        properties.reader().dailyQuotaGlobal());
            }

            long used = audioFileRepository.countReaderRequestsBy(principal.getId(), since);
            if (used >= limit) {
                throw new TtsQuotaExceededException(TtsQuotaExceededException.Scope.USER, limit);
            }

            log.info("Người đọc {} tạo audio cho chương {} (lượt {}/{})",
                    principal.getId(), chapter.getId(), used + 1, limit);
        }

        @Override
        public User requester() {
            return requester;
        }
    }

    /** Hạn mức theo bậc người dùng; null nghĩa là không áp hạn mức nào. */
    private Integer dailyQuotaFor(AppUserPrincipal principal) {
        if (principal.isAdmin()) {
            return null;
        }
        TtsProperties.Reader reader = properties.reader();
        int limit = principal.isVip() ? reader.dailyQuotaVip() : reader.dailyQuota();
        return limit == UNLIMITED ? null : limit;
    }

    /** Số lượt cả hệ thống còn lại trong ngày; {@link Integer#MAX_VALUE} nếu không đặt trần. */
    private int globalRemaining(Instant since) {
        int cap = properties.reader().dailyQuotaGlobal();
        if (cap == UNLIMITED) {
            return Integer.MAX_VALUE;
        }
        long used = audioFileRepository.countReaderRequests(since);
        return Math.max(cap - (int) used, 0);
    }

    private static Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }
}
