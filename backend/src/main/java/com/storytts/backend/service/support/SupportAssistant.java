package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportAssistantProperties;
import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.dto.support.SupportAssistantReplyDto;
import com.storytts.backend.dto.support.SupportAssistantStatusDto;
import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportMessageDto;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.exception.AiQuotaExceededException;
import com.storytts.backend.exception.SupportException;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.AiUsageService;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.ai.GeminiClient;
import com.storytts.backend.service.ai.SupportAssistantPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trợ lý AI đứng trước hàng đợi hỗ trợ.
 *
 * <h3>Lớp này KHÔNG có {@code @Transactional}, và đó là điều kiện</h3>
 * Một lượt hỏi có một lời gọi mạng dài ba mươi giây ở giữa. Bọc cả lượt trong
 * một giao dịch là giữ một kết nối cơ sở dữ liệu suốt quãng ấy, và pool ở đây
 * chỉ có mười kết nối — mười người hỏi cùng lúc là cả website đứng, kể cả trang
 * chủ. Nên ranh giới được chia làm ba, và Gemini nằm ở khoảng trống giữa hai
 * giao dịch:
 *
 * <pre>
 *   [giao dịch 1]  ghi câu hỏi
 *   ...            đọc ngữ cảnh
 *   ( ngoài giao dịch )  gọi Gemini  ← quãng dài nằm ở đây
 *   [giao dịch 2]  khóa hàng, kiểm lại trạng thái, ghi câu trả lời
 * </pre>
 *
 * Đây chính là quy tắc 31 của đặc tả, và nó cũng là <i>lý do</i> quy tắc 36 tồn
 * tại: vì có một khoảng trống ở giữa, mọi thứ đã đúng lúc bắt đầu đều có thể sai
 * lúc kết thúc. Giao dịch 2 vì thế không tin gì vào những gì giao dịch 1 thấy —
 * nó khóa hàng và hỏi lại từ đầu. Xem {@code SupportStore#appendAssistantReply}.
 *
 * <h3>Câu hỏi được ghi trước khi biết có trả lời được hay không</h3>
 * Cố ý, và đây là quyết định thiết kế đáng chú ý nhất của lớp này. Đường ngược
 * lại — chỉ ghi khi đã có câu trả lời — nghe gọn hơn nhưng hỏng ở đúng cảnh
 * quan trọng nhất: Gemini hết giờ chờ, người đọc bực mình bấm "Chat với tư vấn
 * viên", và tư vấn viên mở ra thấy một cuộc trò chuyện trống không. Câu vừa gõ
 * là thứ <b>không được phép</b> mất, vì nó là toàn bộ lý do người ta ở đây.
 *
 * <p>Hệ quả phải chấp nhận: một câu hỏi không có câu trả lời nào bên dưới vẫn
 * nằm trong bản ghi. Đó là sự thật của việc đã xảy ra, và nó đọc ra đúng.
 *
 * <h3>Ba hàng rào chi phí, xếp theo thứ tự rẻ dần</h3>
 * <ol>
 *   <li>{@link SupportRateLimiter} — dùng chung với đường gửi tin thường, vì
 *       một lượt hỏi <i>cũng là</i> một tin nhắn được ghi. Một bộ đếm thứ hai
 *       cho cùng một hành vi là hai con số có thể lệch nhau.</li>
 *   <li>{@link #generating} — một lượt sinh cho một luồng tại một thời điểm.
 *       Đây là hàng rào <i>thứ tự</i> chứ không phải hàng rào chi phí; xem ghi
 *       chú ở chính trường ấy.</li>
 *   <li>Hạn mức trong ngày, ghi trên đĩa ở bảng {@code ai_usage} — hàng rào duy
 *       nhất sống qua một lần khởi động lại, thứ mà gói miễn phí của Render bắt
 *       phải nghĩ tới. Xem V9.</li>
 * </ol>
 *
 * <h3>Không lối nào ở đây dẫn tới ngõ cụt</h3>
 * Trợ lý tắt, hết lượt, Gemini hỏng, câu trả lời rỗng, luồng vừa bị chuyển giao
 * — mọi nhánh đều kết thúc bằng một câu tử tế và {@code suggestHandoff = true}.
 * Cái nút "Chat với tư vấn viên" không tốn lượt nào và không đi qua lớp nào ở
 * đây cả. Đó là nguyên tắc 4 của đặc tả, và nó được giữ bằng cấu trúc chứ không
 * bằng một câu dặn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAssistant {

    private final SupportService supportService;
    private final SupportStore store;
    private final SupportRateLimiter rateLimiter;
    private final SupportProperties supportProperties;
    private final SupportAssistantProperties properties;
    private final GeminiClient gemini;
    private final AiUsageService usage;
    private final CurrentUserService currentUserService;

    /**
     * Những luồng đang có một lượt sinh chạy dở.
     *
     * <h3>Đây là câu trả lời cho quy tắc 33 — thứ tự câu trả lời</h3>
     * Cảnh cần chặn: người đọc gõ câu A, chưa kịp trả lời thì gõ tiếp câu B. Hai
     * lượt sinh chạy song song, và không có gì bảo đảm A xong trước B. Một cuộc
     * trò chuyện mà câu trả lời cho B nằm trên câu trả lời cho A là một cuộc trò
     * chuyện không đọc được — tệ hơn hẳn một lần phải chờ vài giây.
     *
     * <p>Xếp hàng thay vì từ chối cũng là một phương án, nhưng nó giữ một luồng
     * Tomcat đứng chờ một luồng Tomcat khác, và số luồng ở đây là hai mươi. Từ
     * chối thì rẻ, và người đọc nhận một câu rõ ràng ("trợ lý đang trả lời câu
     * trước") thay vì một ô chat đứng im không biết vì sao.
     *
     * <h3>Trong bộ nhớ, và vì sao thế là đủ ở đây</h3>
     * Bản triển khai chạy một tiến trình (một dịch vụ web trên Render), nên một
     * tập trong bộ nhớ phủ hết. Nếu về sau chạy nhiều bản, hàng rào này rách —
     * nhưng cái rách ấy có giới hạn rõ và không phải một lỗ hổng: hai lượt sinh
     * song song tốn thêm một lượt hạn mức và có thể lộn thứ tự hai câu trả lời.
     * Không có gì hỏng về dữ liệu, vì thứ giữ dữ liệu đúng là khóa hàng ở
     * {@code SupportStore}, không phải tập này.
     *
     * <p>Ghi rõ ranh giới ấy ra thay vì dựng sẵn một cơ chế phân tán cho một bài
     * toán chưa có: cùng lối nghĩ với {@link SupportRateLimiter}, vốn cũng là
     * một {@code ConcurrentHashMap} trong bộ nhớ.
     */
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    /* ================================================================== */
    /* Trạng thái                                                          */
    /* ================================================================== */

    /** Trợ lý có bật, và có khóa Gemini để gọi không. */
    public boolean available() {
        return properties.enabled() && gemini.isConfigured();
    }

    /**
     * Trợ lý dùng được không, và người đang gọi còn bao nhiêu lượt hôm nay.
     *
     * <p>Không tạo luồng hỗ trợ nào — đường này chạy mỗi lần mở hộp thoại, và
     * cùng lý lẽ với {@code SupportSummaryDto}: nó phải rẻ tới mức không ai phải
     * nghĩ về việc gọi nó.
     */
    public SupportAssistantStatusDto status() {
        if (!available()) {
            return SupportAssistantStatusDto.off();
        }
        Optional<AppUserPrincipal> principal = currentUserService.currentPrincipal();
        if (principal.isEmpty()) {
            return new SupportAssistantStatusDto(true, null, null);
        }
        AppUserPrincipal caller = principal.get();
        return new SupportAssistantStatusDto(true,
                limitOrNull(caller.isVip()),
                remainingFor(caller));
    }

    /* ================================================================== */
    /* Chọn trợ lý, và chọn người thật                                     */
    /* ================================================================== */

    /**
     * Người đọc bấm "Chat với AI".
     *
     * <p>Trả về cả một lát cắt luồng chứ không chỉ trạng thái, vì hộp thoại cần
     * vẽ ngay tin hệ thống vừa được ghi. Đợi khung tin WebSocket tới rồi mới vẽ
     * là để lại một khoảng trống nhìn thấy được — và trên một bản triển khai
     * không bật được WebSocket thì khoảng trống ấy là vĩnh viễn.
     */
    public SupportThreadDto startSession(SupportService.Actor actor) {
        requireReader(actor);
        requireAvailable();
        SupportConversation conversation = supportService.conversationOf(actor);
        store.startAssistantSession(conversation.getId(), actor.id());
        return supportService.threadForUser(actor, null, null, null);
    }

    /**
     * Người đọc bấm "Chat với tư vấn viên".
     *
     * <h3>Đường này cố ý không đi qua hàng rào nào của trợ lý</h3>
     * Không kiểm {@link #available()}, không tốn lượt hạn mức, không đợi lượt
     * sinh nào xong. Trợ lý có tắt, có hỏng, có hết lượt thì đường tới người
     * thật vẫn nguyên vẹn — đó là nguyên tắc 4 của đặc tả, và nó chỉ đáng tin
     * khi được giữ bằng cấu trúc.
     *
     * <p>Vẫn qua lớp kiểm tần suất, vì lượt bấm này <i>ghi</i> một tin hệ thống
     * và bật huy hiệu của người trực.
     *
     * <p>Tính bất biến nằm ở {@code SupportStore.requestHandoff}, dưới khóa
     * hàng: bấm lần thứ hai không sinh ra tin hệ thống thứ hai, không sinh ra
     * khung tin thứ hai, và không sinh ra phiếu hỗ trợ thứ hai — thứ vốn không
     * dựng được, vì một người chỉ có một luồng.
     */
    public SupportThreadDto handoff(SupportService.Actor actor, String reason) {
        requireReader(actor);
        SupportConversation conversation = supportService.conversationOf(actor);
        if (!rateLimiter.tryAcquire(actor.id())) {
            throw new SupportException(SupportException.Reason.SUPPORT_RATE_LIMITED);
        }
        store.requestHandoff(conversation.getId(), actor.id(), reason);
        return supportService.threadForUser(actor, null, null, null);
    }

    /* ================================================================== */
    /* Một lượt hỏi                                                        */
    /* ================================================================== */

    /**
     * Hỏi trợ lý một câu.
     *
     * <p>Thứ tự các bước dưới đây là phần nghiệp vụ, không phải chi tiết cài
     * đặt — xem ghi chú ở đầu lớp về ba ranh giới giao dịch và về việc câu hỏi
     * được ghi trước.
     */
    public SupportAssistantReplyDto ask(SupportService.Actor actor, SupportSendRequest request) {
        requireReader(actor);
        requireAvailable();
        if (request == null) {
            throw new SupportException(SupportException.Reason.MESSAGE_INVALID);
        }

        // Làm sạch trước khi chạm tới cơ sở dữ liệu, y như đường gửi tin thường:
        // cùng bộ lọc, cùng trần độ dài. Một câu hỏi cho trợ lý *là* một tin
        // nhắn trong luồng, nên nó không được phép đi qua một cửa lỏng hơn.
        String clientMessageId = SupportContent.requireClientId(
                request.clientMessageId(), SupportMessage.CLIENT_ID_LIMIT);
        String question = SupportContent.sanitise(
                request.content(), supportProperties.effectiveMaxMessageLength());

        SupportConversation conversation = supportService.conversationOf(actor);
        Long conversationId = conversation.getId();
        requireAssistantOwns(conversation);

        if (!rateLimiter.tryAcquire(actor.id())) {
            throw new SupportException(SupportException.Reason.SUPPORT_RATE_LIMITED);
        }

        // Chỗ giữ thứ tự. add() trả về false nghĩa là luồng này đang có một lượt
        // sinh chạy dở — xem ghi chú ở trường `generating`.
        if (!generating.add(conversationId)) {
            throw new SupportException(SupportException.Reason.SUPPORT_ASSISTANT_BUSY);
        }
        try {
            return generate(actor, conversationId, clientMessageId, question);
        } finally {
            generating.remove(conversationId);
        }
    }

    private SupportAssistantReplyDto generate(SupportService.Actor actor,
                                              Long conversationId,
                                              String clientMessageId,
                                              String question) {

        // ---- Giao dịch 1: ghi câu hỏi. -----------------------------------
        SupportStore.Appended asked = supportService.appendUserQuestion(
                actor, conversationId, question, clientMessageId);
        Long questionId = asked.userView().id();
        String replyId = replyIdFor(questionId);

        // Lần thử lại của cùng một lần bấm gửi: câu hỏi đã dedup bằng ràng buộc
        // UNIQUE, và câu trả lời tra được bằng định danh suy ra từ id câu hỏi.
        // Trả lại đúng câu cũ thay vì tốn thêm một lượt Gemini để sinh một câu
        // khác — cùng một lần bấm không được phép có hai câu trả lời.
        if (asked.duplicate()) {
            Optional<SupportMessageDto> existing =
                    store.findAssistantReply(conversationId, replyId);
            if (existing.isPresent()) {
                return answered(asked, existing.get(), false, null, actor);
            }
        }

        // ---- Đường tắt: xin gặp người thật. ------------------------------
        // Nhận ra bằng một phép so chuỗi, không bằng mô hình. Hai lý do: nó
        // không tốn lượt nào, và nó không bao giờ *bỏ sót* — thứ mà một mô hình
        // ngôn ngữ thì không hứa được. Nhận nhầm ở đây gần như vô hại: người
        // đọc nhận đúng cái nút họ vừa hỏi xin.
        if (asksForHuman(question)) {
            log.info("AI_ESCALATE_EXPLICIT ho-tro: luồng {} — người đọc xin gặp tư vấn viên",
                    conversationId);
            return persistReply(asked, replyId, HUMAN_REQUESTED_LINE, true, actor);
        }

        // ---- Hạn mức. ----------------------------------------------------
        // Sau khi đã ghi câu hỏi, cố ý: hết lượt thì câu vừa gõ vẫn nằm trong
        // bản ghi, nên một cú bấm sang tư vấn viên là đủ để người trực đọc được
        // nó. Ném lỗi ở đây và bỏ câu hỏi đi sẽ bắt người ta gõ lại.
        Long usageId;
        try {
            usageId = reserveQuota(actor);
        } catch (AiQuotaExceededException ex) {
            log.info("AI_QUOTA_EXHAUSTED ho-tro: luồng {}, người {}", conversationId, actor.id());
            return declined(asked, QUOTA_LINE, actor);
        }

        // ---- Ngữ cảnh, rồi lời gọi mạng nằm ngoài mọi giao dịch. ----------
        List<SupportMessageDto> history = store.assistantContext(
                conversationId, questionId, properties.maxHistoryTurns());

        long startedAt = System.nanoTime();
        log.info("AI_REQUEST_STARTED ho-tro: luồng {}, {} lượt ngữ cảnh, câu hỏi {} ký tự",
                conversationId, history.size(), question.length());

        SupportAssistantPrompt.Answer answer;
        try {
            answer = SupportAssistantPrompt.parse(gemini.generate(
                    SupportAssistantPrompt.systemInstruction(),
                    SupportAssistantPrompt.conversation(history, question)));
        } catch (RuntimeException ex) {
            // Lỗi thô của nhà cung cấp không bao giờ ra tới người đọc — nó vào
            // log, và người đọc nhận một câu tử tế kèm đường sang tư vấn viên.
            log.warn("AI_REQUEST_FAILED ho-tro: luồng {} hỏng sau {}ms, {}",
                    conversationId, millisSince(startedAt), ex.getClass().getSimpleName());
            usage.refundUsage(usageId, "goi Gemini hong");
            return declined(asked, UNAVAILABLE_LINE, actor);
        }

        if (answer.text().isBlank()) {
            log.info("AI_REQUEST_EMPTY ho-tro: luồng {} — câu trả lời rỗng", conversationId);
            usage.refundUsage(usageId, "cau tra loi rong");
            return declined(asked, UNAVAILABLE_LINE, actor);
        }

        log.info("AI_REQUEST_DONE ho-tro: luồng {}, {}ms, {} ký tự{}",
                conversationId, millisSince(startedAt), answer.text().length(),
                answer.escalate() ? ", có gợi ý chuyển tiếp" : "");

        // ---- Giao dịch 2: ghi câu trả lời, sau khi kiểm lại trạng thái. ---
        return persistReply(asked, replyId, answer.text(), answer.escalate(), actor);
    }

    /**
     * Ghi câu trả lời — hoặc bỏ nó, nếu luồng đã rời khỏi tay trợ lý.
     *
     * <p>Phép kiểm nằm bên trong {@code appendAssistantReply}, dưới khóa hàng.
     * Ở đây chỉ còn việc dịch "đã bị bỏ" thành một câu cho người đọc: họ vừa bấm
     * chuyển sang tư vấn viên, nên câu đúng là "đang kết nối", không phải một
     * lời xin lỗi về trợ lý.
     */
    private SupportAssistantReplyDto persistReply(SupportStore.Appended asked,
                                                  String replyId,
                                                  String text,
                                                  boolean escalate,
                                                  SupportService.Actor actor) {
        Long conversationId = asked.userView().conversationId();
        Optional<SupportStore.Appended> written =
                store.appendAssistantReply(conversationId, replyId, clampReply(text));
        if (written.isEmpty()) {
            return new SupportAssistantReplyDto(asked.userView(), null,
                    latestState(actor, asked), asked.duplicate(), true,
                    HANDED_OFF_LINE, remainingFor(actor));
        }
        return answered(asked, written.get().userView(), escalate, null, actor);
    }

    /** Có câu trả lời. */
    private SupportAssistantReplyDto answered(SupportStore.Appended asked,
                                              SupportMessageDto reply,
                                              boolean escalate,
                                              String notice,
                                              SupportService.Actor actor) {
        return new SupportAssistantReplyDto(asked.userView(), reply,
                latestState(actor, asked), asked.duplicate(), escalate, notice,
                remainingFor(actor));
    }

    /** Không có câu trả lời, và đó không phải lỗi của người hỏi. */
    private SupportAssistantReplyDto declined(SupportStore.Appended asked,
                                              String notice,
                                              SupportService.Actor actor) {
        return new SupportAssistantReplyDto(asked.userView(), null,
                latestState(actor, asked), asked.duplicate(), true, notice,
                remainingFor(actor));
    }

    /**
     * Trạng thái luồng đọc lại sau lượt ghi cuối.
     *
     * <p>Không dùng lại {@code asked.userState()}: nó được chụp trước khi câu
     * trả lời được ghi, nên {@code lastMessageId} và số chưa đọc trong đó đã cũ
     * đúng một tin. Trình duyệt dùng {@code lastMessageId} để biết mình có bỏ lỡ
     * gì không, nên một giá trị cũ ở đây sẽ sinh ra một lượt đồng bộ thừa ở mỗi
     * lượt hỏi.
     */
    private SupportConversationDto latestState(SupportService.Actor actor,
                                               SupportStore.Appended asked) {
        return store.findByUser(actor.id())
                .map(conversation -> SupportConversationDto.of(conversation,
                        SupportSenderRole.USER,
                        store.unreadFor(conversation, SupportSenderRole.USER)))
                .orElseGet(asked::userState);
    }

    /* ================================================================== */
    /* Hàng rào và tiện ích                                                */
    /* ================================================================== */

    private static void requireReader(SupportService.Actor actor) {
        if (actor.role() != SupportSenderRole.USER) {
            throw new SupportException(SupportException.Reason.SUPPORT_NOT_FOR_ADMIN);
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new SupportException(SupportException.Reason.SUPPORT_ASSISTANT_DISABLED);
        }
    }

    /**
     * Phép kiểm sớm, để không tiêu gì cho một lượt chắc chắn không ghi được.
     *
     * <p>Nó <b>không</b> thay cho phép kiểm dưới khóa hàng ở
     * {@code appendAssistantReply}: giữa hai thời điểm ấy có một lời gọi mạng ba
     * mươi giây, và cuộc đua nằm đúng ở quãng đó. Đây chỉ là cái cửa đóng sớm
     * cho một trình duyệt cũ gọi nhầm đường sau khi đã chuyển giao.
     */
    private static void requireAssistantOwns(SupportConversation conversation) {
        if (conversation.getStatus() == SupportConversationStatus.BLOCKED) {
            throw new SupportException(SupportException.Reason.CONVERSATION_BLOCKED);
        }
        if (!conversation.getAssistantMode().answeredByAssistant()) {
            throw new SupportException(SupportException.Reason.SUPPORT_ASSISTANT_NOT_ACTIVE);
        }
    }

    private Long reserveQuota(SupportService.Actor actor) {
        boolean vip = currentUserService.currentPrincipal()
                .map(AppUserPrincipal::isVip)
                .orElse(false);
        return usage.reserve(actor.id(), AiUsageKind.SUPPORT, null,
                limitOrNull(vip), properties.dailyQuotaGlobal(),
                (scope, limit) -> new AiQuotaExceededException(
                        scope == AiUsageService.Scope.GLOBAL
                                ? AiQuotaExceededException.Scope.GLOBAL
                                : AiQuotaExceededException.Scope.USER,
                        limit));
    }

    private Integer limitOrNull(boolean vip) {
        int limit = properties.dailyQuotaFor(vip);
        return limit == SupportAssistantProperties.UNLIMITED ? null : limit;
    }

    private Integer remainingFor(SupportService.Actor actor) {
        return currentUserService.currentPrincipal()
                .map(this::remainingFor)
                .orElse(null);
    }

    private Integer remainingFor(AppUserPrincipal caller) {
        return usage.remaining(caller.getId(), AiUsageKind.SUPPORT,
                limitOrNull(caller.isVip()), properties.dailyQuotaGlobal());
    }

    /**
     * Cắt câu trả lời cho vừa cột thay vì để cơ sở dữ liệu từ chối cả hàng.
     *
     * <p>Cùng lập luận với {@code SupportConversation.preview} và
     * {@code NotificationService.clamp}: cái giá của một câu quá dài phải là một
     * đoạn bị cụt, chứ không phải một lượt hỏi mất trắng. Trần thật đến từ cấu
     * hình và luôn nhỏ hơn {@code SupportMessage.CONTENT_LIMIT}.
     *
     * <p>Đi qua {@code SupportContent.sanitise} sau khi cắt, không phải trước:
     * nó là bộ lọc chung cho mọi nội dung vào bảng này, và một câu do máy sinh
     * cũng không được miễn — ký tự điều khiển trong một phản hồi lạ vẫn là ký tự
     * điều khiển.
     */
    private String clampReply(String raw) {
        int limit = Math.min(properties.maxReplyChars(), SupportMessage.CONTENT_LIMIT);
        String text = raw.strip();
        if (text.length() > limit) {
            text = text.substring(0, limit - 1).stripTrailing() + "…";
        }
        return SupportContent.sanitise(text, limit);
    }

    /**
     * Người đọc có đang xin gặp người thật không.
     *
     * <p>Danh sách cố ý hẹp và gồm những cụm <i>chỉ</i> xuất hiện khi người ta
     * thật sự muốn gặp người: "gặp admin", "tư vấn viên", "nhân viên hỗ trợ". Nó
     * không cố đoán ý — việc ấy đã có mô hình làm, ở lớp thứ hai. Nó chỉ bảo
     * đảm rằng câu rõ ràng nhất thì không bao giờ bị bỏ sót, kể cả khi Gemini
     * đang hỏng.
     */
    private static boolean asksForHuman(String question) {
        String text = question.toLowerCase(Locale.ROOT);
        for (String phrase : HUMAN_REQUEST_PHRASES) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static final List<String> HUMAN_REQUEST_PHRASES = List.of(
            "tư vấn viên", "tu van vien",
            "gặp admin", "gap admin",
            "gặp quản trị", "gap quan tri",
            "nhân viên hỗ trợ", "nhan vien ho tro",
            "gặp người thật", "gap nguoi that",
            "nói chuyện với người", "noi chuyen voi nguoi",
            "chat với người thật", "chat voi nguoi that");

    private static String replyIdFor(Long questionId) {
        return ASSISTANT_REPLY_PREFIX + questionId;
    }

    /**
     * Tiền tố định danh câu trả lời của trợ lý.
     *
     * <p>Định danh là {@code "ai-<id câu hỏi>"}, một hàm của câu hỏi chứ không
     * phải một số ngẫu nhiên, và đó là toàn bộ cơ chế chống trùng cho tin của
     * trợ lý — thứ không dùng được ràng buộc {@code UNIQUE} của V15 vì cột người
     * gửi để trống. Một câu hỏi sinh ra đúng một câu trả lời, dù lượt hỏi được
     * thử lại bao nhiêu lần. Xem V16.
     *
     * <p>Vừa trong {@code CLIENT_ID_LIMIT} (64) với biên rộng: "ai-" cộng một
     * {@code bigint} là nhiều nhất 22 ký tự.
     */
    static final String ASSISTANT_REPLY_PREFIX = "ai-";

    private static long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    /* ------------------------------------------------------------------ */
    /* Những câu nói với người đọc                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Câu trả lời cho một lời xin gặp người thật.
     *
     * <p>Nó nói rõ hai điều mà đặc tả cấm nói sai: trợ lý <i>chưa</i> chuyển gì
     * cả, và người đọc mới là người bấm. Hứa hẹn một việc chưa xảy ra là cách
     * nhanh nhất để một người ngồi chờ một câu trả lời không bao giờ tới.
     */
    static final String HUMAN_REQUESTED_LINE =
            "Được, bạn bấm nút \"Chat với tư vấn viên\" ngay bên dưới nhé. "
                    + "Toàn bộ nội dung trò chuyện phía trên sẽ được giữ nguyên, "
                    + "nên bạn không cần kể lại từ đầu.";

    static final String QUOTA_LINE =
            "Bạn đã dùng hết lượt hỏi trợ lý AI trong hôm nay. "
                    + "Bạn có thể chat với tư vấn viên — đường đó không giới hạn lượt.";

    static final String UNAVAILABLE_LINE =
            "Hiện tại trợ lý AI đang tạm thời không phản hồi. "
                    + "Câu hỏi của bạn vẫn được lưu lại. "
                    + "Bạn có muốn chuyển sang tư vấn viên không?";

    static final String HANDED_OFF_LINE =
            "Cuộc trò chuyện đã được chuyển cho tư vấn viên. Vui lòng chờ trong giây lát.";
}
