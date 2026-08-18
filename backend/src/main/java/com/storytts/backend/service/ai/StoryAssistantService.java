package com.storytts.backend.service.ai;

import com.storytts.backend.config.AiAssistantProperties;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.dto.ai.AssistantAskRequest;
import com.storytts.backend.dto.ai.AssistantReplyDto;
import com.storytts.backend.dto.ai.AssistantStatusDto;
import com.storytts.backend.dto.ai.AssistantTurn;
import com.storytts.backend.exception.AiAssistantException;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.ChapterAccessService;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h2>Trợ lý đọc truyện — tóm tắt và giải thích chương đang mở</h2>
 *
 * Người đọc hỏi một câu, trợ lý trả lời dựa trên đúng chương họ đang đọc. Cả
 * lớp này tồn tại để bốn việc dưới đây xảy ra <b>theo đúng thứ tự này</b>, và
 * thứ tự ấy chính là phần đáng đọc nhất:
 *
 * <ol>
 *   <li><b>Đăng nhập.</b> Không có danh tính thì không đếm hạn mức lên ai được
 *       — cùng lý lẽ đã dùng cho nút "Nghe bằng AI".</li>
 *   <li><b>Quyền đọc chương.</b> Gọi đúng cửa mà trang đọc gọi:
 *       {@link ChapterAccessService#requireAccess}. Đây là điều quan trọng nhất
 *       của cả tính năng — xem phần dưới.</li>
 *   <li><b>Hạn mức.</b> Trừ lượt trước khi gọi nhà cung cấp.</li>
 *   <li><b>Gọi Gemini.</b> Chỉ tới bước này nội dung chương mới rời máy chủ.</li>
 * </ol>
 *
 * <h3>Vì sao trợ lý không thể thành cửa sau của chương tính phí</h3>
 * Trình duyệt chỉ gửi lên một con số: {@code chapterId}. Nội dung chương do
 * chính máy chủ tra từ cơ sở dữ liệu, <i>sau</i> khi đi qua cùng một hàm xét
 * quyền mà đường đọc chữ đang dùng. Nghĩa là không có nhánh nào để mà quên:
 * một chương VIP hay một chương giá Xu chưa mở khoá sẽ ném 403/402 ở bước 2,
 * trước khi có một ký tự nào của nó được đọc lên. Và vì trình duyệt không được
 * phép gửi kèm nội dung, dán nội dung chương lậu vào đây cũng không có tác
 * dụng gì — không có trường nào nhận nó.
 *
 * <h3>Cái không có ở đây</h3>
 * Không bảng, không lịch sử hội thoại lưu lại, không véc-tơ, không tóm tắt dựng
 * sẵn. Hội thoại sống trong trình duyệt và chết cùng chương. Đây là lựa chọn
 * chứ không phải thiếu sót: mọi thứ vừa kể đều cần một cái bảng và một quy tắc
 * làm mới, để đổi lấy một thứ mà một hộp chat mở ra trong lúc đọc chưa cần tới.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryAssistantService {

    /**
     * Trần độ dài một lượt cũ được gửi kèm.
     *
     * <p>Lịch sử do trình duyệt gửi lên, nên độ dài của nó là thứ người ngoài
     * đặt được. Cắt số lượt thôi thì chưa đủ: sáu lượt, mỗi lượt một megabyte,
     * vẫn là một hoá đơn. Con số này rộng rãi so với một lời đáp thật —
     * {@code maxOutputTokens} mặc định không dựng nổi một câu dài tới đây.
     */
    private static final int MAX_HISTORY_TURN_CHARS = 4_000;

    private final AiAssistantProperties properties;
    private final GeminiClient geminiClient;
    private final AssistantQuota quota;
    private final ChapterService chapterService;
    private final ChapterAccessService chapterAccessService;
    private final CurrentUserService currentUserService;

    /**
     * Trạng thái để trang đọc biết nên hiện gì — không tiêu lượt nào.
     *
     * <p>Trả lời được cả cho khách chưa đăng nhập, vì câu hỏi "máy chủ này có
     * bật trợ lý không" không phụ thuộc vào ai đang hỏi. Riêng con số lượt còn
     * lại thì có, nên nó là {@code null} với khách.
     */
    public AssistantStatusDto status() {
        boolean enabled = properties.enabled() && geminiClient.isConfigured();

        Optional<AppUserPrincipal> principal = currentUserService.currentPrincipal();
        Integer limit = null;
        Integer remaining = null;
        if (enabled && principal.isPresent()) {
            AppUserPrincipal caller = principal.get();
            limit = properties.dailyQuotaFor(caller.isVip());
            remaining = quota.remainingFor(caller.getId(), caller.isVip());
        }

        return new AssistantStatusDto(enabled, limit, remaining, properties.maxQuestionChars());
    }

    /**
     * Một câu hỏi về chương đang đọc.
     *
     * <p><b>Không có {@code @Transactional} ở đây, và đó là chủ ý.</b> Một lời
     * gọi Gemini mất từ một tới ba chục giây; bọc cả method trong một giao dịch
     * nghĩa là giữ một kết nối cơ sở dữ liệu suốt quãng ấy, mà cả pool chỉ có
     * mười cái. Mười người cùng hỏi trợ lý là cả trang web đứng — kể cả những
     * người chỉ đang lật chương. Cùng một lý lẽ đã viết trong
     * {@code application.properties} về {@code DELAYED_ACQUISITION}.
     */
    public AssistantReplyDto ask(AssistantAskRequest request) {
        if (!properties.enabled() || !geminiClient.isConfigured()) {
            throw AiAssistantException.unavailable();
        }

        // Chặn ở đây nữa dù tầng URL đã bắt đăng nhập: service không nên tin vào
        // một dòng cấu hình ở tệp khác. Và chặn trước khi tra chương, để người
        // dò số hiệu chương chỉ nhận 401, không phân biệt được 404 với 403.
        AppUserPrincipal caller = currentUserService.currentPrincipal()
                .orElseThrow(() -> new LoginRequiredException(
                        "Bạn cần đăng nhập để hỏi trợ lý AI."));

        String question = normaliseQuestion(request.message());

        // >>> Cùng một cửa quyền với đường đọc chữ <<<
        Snapshot chapter = loadReadableChapter(request.chapterId());

        // Trừ lượt trước khi gọi. Một lượt bị tính cho một lời gọi hỏng thì
        // thiệt cho người hỏi; một lời gọi tính tiền mà không ai đếm thì thiệt
        // cho người trả hoá đơn, và đó là bên không có mặt để phàn nàn.
        quota.consume(caller.getId(), caller.isVip());

        AssistantPrompt.Body body = AssistantPrompt.fit(chapter.content(), properties.maxChapterChars());
        List<AssistantTurn> history = trimHistory(request.history());

        long startedAt = System.nanoTime();
        log.info("Hỏi trợ lý AI: chapterId={}, userId={}, lượt cũ={}, ký tự chương={}{}",
                request.chapterId(), caller.getId(), history.size(), body.text().length(),
                body.truncated() ? " (đã cắt)" : "");

        String answer;
        try {
            answer = geminiClient.generate(
                    AssistantPrompt.systemInstruction(),
                    AssistantPrompt.conversation(
                            chapter.chapter(), body.text(), body.truncated(), history, question));
        } catch (RuntimeException ex) {
            // Ghi lại phân loại lỗi và độ trễ, không ghi câu hỏi lẫn nội dung chương.
            log.warn("Trợ lý AI hỏng sau {}ms: chapterId={}, {}",
                    millisSince(startedAt), request.chapterId(), ex.getClass().getSimpleName());
            throw ex;
        }

        log.info("Trợ lý AI trả lời sau {}ms: chapterId={}, {} ký tự",
                millisSince(startedAt), request.chapterId(), answer.length());

        return new AssistantReplyDto(
                answer,
                quota.remainingFor(caller.getId(), caller.isVip()),
                body.truncated());
    }

    /**
     * Tra chương và xét quyền — đúng hai dòng mà {@code ChapterService.getDetail}
     * mở đầu, cố ý không viết lại thành gì khác.
     *
     * <p>Chép hai dòng ấy ra đây trông như trùng lặp, nhưng thứ được chép là
     * <i>lời gọi</i>, không phải <i>luật</i>: luật vẫn nằm nguyên một chỗ trong
     * {@link ChapterAccessService}. Gọi thẳng {@code getDetail} thì tránh được
     * hai dòng này, đổi lại kéo theo bốn câu truy vấn không dùng tới — chương
     * trước, chương sau, hai lần hỏi đã có audio chưa — cho một lời gọi vốn chỉ
     * cần thân chương.
     *
     * <p>Cũng không cần giao dịch nào bọc quanh: {@code findDetailById} đã
     * {@code JOIN FETCH} sang truyện, nên tên truyện có sẵn trong tay chứ không
     * phải một liên kết lười chờ nổ ra sau khi phiên đóng. Và {@code decide}
     * bên trong {@code requireAccess} tự mở giao dịch chỉ-đọc của riêng nó, qua
     * proxy, nên nó vẫn nguyên vẹn.
     */
    private Snapshot loadReadableChapter(Long chapterId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        chapterAccessService.requireAccess(chapter);

        if (chapter.getContent() == null || chapter.getContent().isBlank()) {
            throw new BadRequestException(
                    "Chương này chưa có nội dung nên trợ lý AI không đọc được gì.");
        }

        // Sao ra một chuỗi rời khỏi entity trước khi giao dịch đóng: phần còn
        // lại của lời gọi chạy ngoài giao dịch, và đọc một trường lười sau đó là
        // một LazyInitializationException chờ sẵn.
        return new Snapshot(chapter, chapter.getContent());
    }

    /** Câu hỏi sau khi bỏ khoảng trắng thừa và kiểm tra độ dài. */
    private String normaliseQuestion(String raw) {
        String question = raw == null ? "" : raw.strip();
        if (question.isEmpty()) {
            throw new BadRequestException("Bạn chưa nhập câu hỏi.");
        }
        if (question.length() > properties.maxQuestionChars()) {
            throw new BadRequestException(
                    "Câu hỏi dài quá %d ký tự. Bạn rút ngắn lại giúp nhé."
                            .formatted(properties.maxQuestionChars()));
        }
        return question;
    }

    /**
     * Giữ lại những lượt cũ gần nhất, bỏ phần còn lại.
     *
     * <p>Lấy từ cuối lên: hội thoại nhiều lượt thì lượt gần nhất mới là thứ câu
     * hỏi mới đang nối vào. Cắt từ đầu xuống sẽ giữ đúng phần đã hết liên quan.
     *
     * <p>Nội dung chương không nằm trong lịch sử — nó được dựng lại từ cơ sở dữ
     * liệu ở mỗi lời gọi — nên cắt ở đây không làm trợ lý quên mất chương.
     */
    private List<AssistantTurn> trimHistory(List<AssistantTurn> raw) {
        int maxTurns = properties.maxHistoryTurns();
        if (raw == null || raw.isEmpty() || maxTurns <= 0) {
            return List.of();
        }

        List<AssistantTurn> usable = new ArrayList<>(raw.size());
        for (AssistantTurn turn : raw) {
            if (turn != null && turn.isUsable()) {
                usable.add(turn.content().length() <= MAX_HISTORY_TURN_CHARS
                        ? turn
                        : new AssistantTurn(turn.role(),
                                turn.content().substring(0, MAX_HISTORY_TURN_CHARS)));
            }
        }

        int from = Math.max(usable.size() - maxTurns, 0);
        return List.copyOf(usable.subList(from, usable.size()));
    }

    private long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    /**
     * Chương và thân của nó, đã tách khỏi phiên Hibernate.
     *
     * <p>Chỉ {@code content} được sao ra chuỗi. {@code chapter} vẫn là entity vì
     * những trường lời nhắc cần tới — tên truyện, số chương, tên chương — đều
     * đã được nạp sẵn bởi {@code findDetailById}.
     */
    private record Snapshot(Chapter chapter, String content) {
    }
}
