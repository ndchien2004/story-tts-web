package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsReaderStatusDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.exception.TtsQuotaExceededException;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.AiUsageService;
import com.storytts.backend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * <h3>Hạn mức đếm ở đâu, và vì sao không đếm ở chỗ cũ</h3>
 * Trước đây hạn mức là một phép đếm trên chính bảng {@code audio_files}: bao
 * nhiêu bản mang tên người này, tạo từ 0 giờ sáng nay. Cách ấy có một tính chất
 * đẹp — dùng lại cache thì không sinh hàng nào nên cũng không tốn lượt nào —
 * nhưng nó đứng cạnh {@link ReaderNarrationCleanup}, thứ xóa sạch những hàng ấy
 * mỗi lần người ta mở phiên đăng nhập mới. Hai việc đều đúng theo ý định riêng,
 * nhưng cộng lại thì hạn mức nạp lại được bằng cách đăng xuất rồi đăng nhập.
 *
 * <p>Giờ lượt được ghi vào {@code ai_usage} — một sổ chỉ ghi thêm, không ai xóa
 * (xem {@link AiUsageService}). Tính chất "dùng lại cache thì miễn phí" vẫn giữ
 * nguyên, và vẫn vì đúng lý do cũ: sổ chỉ được ghi ở trong
 * {@code beforeNewGeneration}, mà đường trả về từ cache không đi qua đó.
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

    /** Giá trị hạn mức nghĩa là "không giới hạn". */
    private static final int UNLIMITED = AiUsageService.UNLIMITED;

    private final TtsService ttsService;
    private final TtsEngine ttsEngine;
    private final TtsProperties properties;
    private final AiUsageService aiUsageService;
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

        Integer remaining = principal
                .map(caller -> aiUsageService.remaining(
                        caller.getId(), AiUsageKind.TTS, limit, reader.dailyQuotaGlobal()))
                .orElse(null);

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

        /** Dòng sổ vừa chiếm, chờ được nối với bản audio sắp ghi. */
        private Long usageId;

        private Budget(AppUserPrincipal principal, User requester) {
            this.principal = principal;
            this.requester = requester;
        }

        @Override
        public void beforeNewGeneration(Chapter chapter) {
            TtsProperties.Reader reader = properties.reader();

            // Trần độ dài xét trước hạn mức: chương quá dài thì lời từ chối
            // không phụ thuộc vào việc hôm nay còn lượt hay không, và trừ một
            // lượt rồi mới báo "chương này dài quá" là lấy của người ta một thứ
            // họ chưa dùng được.
            int length = chapter.getContent() == null ? 0 : chapter.getContent().length();
            if (length > reader.maxChars()) {
                throw new BadRequestException(
                        ("Chương này dài %,d ký tự, vượt mức %,d ký tự cho phép tự tạo audio. "
                                + "Mời bạn báo với ban quản trị để họ dựng sẵn bản audio cho chương.")
                                .formatted(length, reader.maxChars()));
            }

            // Quản trị viên không bị chặn bởi hạn mức nào, kể cả trần chung —
            // giữ nguyên đường cũ: họ đã có console riêng, và họ chính là người
            // chịu trách nhiệm về hóa đơn. Lượt của họ vẫn được ghi sổ, nên
            // bảng chi phí không có khoảng trống nào.
            boolean unmetered = principal.isAdmin();

            usageId = aiUsageService.reserve(
                    principal.getId(),
                    AiUsageKind.TTS,
                    chapter.getId(),
                    dailyQuotaFor(principal),
                    unmetered ? UNLIMITED : reader.dailyQuotaGlobal(),
                    (scope, limit) -> new TtsQuotaExceededException(
                            scope == AiUsageService.Scope.GLOBAL
                                    ? TtsQuotaExceededException.Scope.GLOBAL
                                    : TtsQuotaExceededException.Scope.USER,
                            limit));
        }

        @Override
        public void afterGenerationQueued(AudioFile audio) {
            aiUsageService.linkToAudio(usageId, audio.getId());
        }

        @Override
        public User requester() {
            return requester;
        }
    }

    /**
     * Hạn mức theo bậc người dùng; null nghĩa là không áp hạn mức cá nhân nào.
     *
     * <p>Quản trị viên không bị chặn, nhưng lượt của họ vẫn được ghi sổ: trần
     * chung là cầu dao chi phí của cả hệ thống, và một lượt không ai đếm vẫn là
     * một lượt có hóa đơn.
     */
    private Integer dailyQuotaFor(AppUserPrincipal principal) {
        if (principal.isAdmin()) {
            return null;
        }
        TtsProperties.Reader reader = properties.reader();
        int limit = principal.isVip() ? reader.dailyQuotaVip() : reader.dailyQuota();
        return limit == UNLIMITED ? null : limit;
    }
}
