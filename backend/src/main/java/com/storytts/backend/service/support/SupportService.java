package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportMessageType;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportInboxItemDto;
import com.storytts.backend.dto.support.SupportMessageDto;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.dto.support.SupportSummaryDto;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.dto.support.SupportUserSummaryDto;
import com.storytts.backend.exception.AccountLockedException;
import com.storytts.backend.exception.SupportException;
import com.storytts.backend.repository.SupportConversationRepository;
import com.storytts.backend.repository.SupportMessageRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Chỗ duy nhất mà một lệnh của hộp thư hỗ trợ đi qua — dù nó đến từ REST hay từ
 * WebSocket.
 *
 * <h3>Đây là điểm thiết kế quan trọng nhất của tính năng</h3>
 * Đặc tả nói rõ hai lần: REST không được là lối vòng qua phép kiểm của
 * WebSocket, và ngược lại. Cách chắc chắn nhất để giữ điều đó không phải là viết
 * hai bộ kiểm giống nhau rồi cố giữ chúng đồng bộ — mà là <b>không có bộ thứ
 * hai</b>. Controller và bộ xử lý WebSocket ở đây đều mỏng: chúng dịch một
 * request hoặc một khung tin thành một lời gọi vào lớp này, rồi dịch kết quả
 * ngược lại. Toàn bộ phần quyết định nằm ở đây.
 *
 * <h3>Bốn thứ không bao giờ đến từ trình duyệt</h3>
 * <pre>
 *   danh tính người gửi → từ phiên đã xác thực
 *   vai trò             → từ users.role, đọc lại từ cơ sở dữ liệu ở mỗi lệnh
 *   luồng của người đọc → suy từ quyền sở hữu, không nhận conversationId
 *   mốc thời gian       → đồng hồ máy chủ
 * </pre>
 *
 * Vế thứ ba đáng nói thêm: {@link #sendAsUser} và {@link #markReadAsUser}
 * <i>không có tham số {@code conversationId}</i>. Không phải "có nhưng được
 * kiểm" — không có. Một IDOR cần một tham số để tấn công, và ở đây không có
 * tham số nào.
 *
 * <h3>Vì sao lớp này gần như không có {@code @Transactional}</h3>
 * Vì mọi ranh giới giao dịch nằm ở {@link SupportStore}, và chúng cố ý hẹp. Ba
 * việc đắt nhất của một lượt gửi — kiểm tần suất, chuẩn hóa và làm sạch nội
 * dung, dựng DTO — không cần kết nối cơ sở dữ liệu nào, nên chúng chạy
 * <i>trước</i> khi giao dịch mở ra. Pool ở đây chỉ có mười kết nối, và một
 * WebSocket sống hàng giờ không được phép giữ cái nào.
 *
 * <p>Ngoại lệ là mấy đường chỉ đọc, nơi {@code @Transactional(readOnly = true)}
 * gom vài câu SELECT vào một kết nối thay vì mở lại cho từng câu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {

    private final SupportStore store;
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final SupportRateLimiter rateLimiter;
    private final SupportProperties properties;

    /* ------------------------------------------------------------------ */
    /* Danh tính và quyền                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Ai đang gọi, đọc lại từ cơ sở dữ liệu <b>ở mỗi lệnh</b>.
     *
     * <h3>Vì sao đọc lại mỗi lần thay vì tin vào phiên</h3>
     * Vì đây là chỗ duy nhất khiến WebSocket có cùng mức bảo đảm với HTTP. Mỗi
     * request HTTP mang token đều nạp lại người dùng từ cơ sở dữ liệu — xem
     * {@code JwtAuthenticationFilter}, và ghi chú ở đó nói rõ vì sao: không có
     * danh sách token bị thu hồi, cơ sở dữ liệu <i>là</i> nguồn sự thật, và nó
     * được hỏi ở mọi request.
     *
     * <p>Một kết nối WebSocket thì ngược lại: nó bắt tay một lần rồi sống hàng
     * giờ. Không đọc lại ở đây thì "đã xác thực một lần" trở thành "được xác
     * thực mãi mãi", và ba tình huống mà đặc tả bắt phải xử lý đều lọt qua: tài
     * khoản bị khóa giữa chừng, quản trị viên bị hạ quyền giữa chừng, và một
     * phiên đã đăng xuất.
     *
     * <p>Cái giá là một câu SELECT theo khóa chính cho mỗi lệnh — đúng cái giá
     * mà phần còn lại của ứng dụng đã trả cho mỗi request, và vì đúng lý do ấy.
     *
     * @throws AccountLockedException tài khoản đã bị khóa hoặc không còn tồn tại
     */
    @Transactional(readOnly = true)
    public Actor resolveActor(Long userId) {
        User user = users.findById(userId).orElseThrow(AccountLockedException::new);
        if (!user.isEnabled()) {
            throw new AccountLockedException();
        }
        return new Actor(user.getId(),
                user.isAdmin() ? SupportSenderRole.ADMIN : SupportSenderRole.USER);
    }

    /** Bắt buộc là quản trị viên. Kiểm lại ở tầng service, không chỉ ở tầng URL. */
    private static void requireAdmin(Actor actor) {
        if (actor.role() != SupportSenderRole.ADMIN) {
            throw new SupportException(SupportException.Reason.CONVERSATION_ACCESS_DENIED);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Người đọc                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Vài con số cho cái bong bóng chat, và <b>không tạo gì cả</b>.
     *
     * <h3>Vì sao cần một đường riêng thay vì dùng {@link #threadForUser}</h3>
     * Vì cái bong bóng nằm ở góc màn hình của <i>mọi</i> trang, với <i>mọi</i>
     * người đã đăng nhập. Nếu nó gọi đường kia để biết số chưa đọc thì mỗi lần
     * ai đó mở trang chủ là một hàng mới trong {@code support_conversations} —
     * hàng nghìn luồng rỗng của những người chưa bao giờ định liên hệ hỗ trợ.
     *
     * <p>Nên đường này chỉ <i>đọc</i>. Luồng được tạo ở đúng một thời điểm: lần
     * đầu người dùng thật sự mở hộp thoại ra.
     *
     * <p>Quản trị viên gọi vào đây thì nhận về "chưa có gì" chứ không phải một
     * lời từ chối: họ không có luồng của riêng mình (xem {@link #conversationOf}),
     * và cái bong bóng vốn không được vẽ cho họ — nhưng một lời từ chối ở đây sẽ
     * biến một chuyện bình thường thành một lỗi đỏ trong bảng điều khiển trình
     * duyệt.
     */
    @Transactional(readOnly = true)
    public SupportSummaryDto summaryForUser(Actor actor) {
        if (actor.role() == SupportSenderRole.ADMIN) {
            return SupportSummaryDto.none();
        }
        return store.findByUser(actor.id())
                .map(conversation -> new SupportSummaryDto(
                        true,
                        store.unreadFor(conversation, SupportSenderRole.USER),
                        conversation.getStatus(),
                        conversation.getAssistantMode(),
                        conversation.getLastMessageId()))
                .orElseGet(SupportSummaryDto::none);
    }

    /**
     * Luồng của người đang gọi, mở mới nếu chưa có.
     *
     * <h3>Cuộc đua tạo trùng, và vì sao nó không cần khóa nào</h3>
     * Hai tab cùng bấm "Liên hệ hỗ trợ" thì cả hai cùng thấy "chưa có" rồi cùng
     * gọi {@code create}. Một bên thắng; bên kia đụng {@code UNIQUE (user_id)}
     * và nhận {@code DataIntegrityViolationException}. Đọc lại lúc ấy là chắc
     * chắn thấy hàng của bên thắng — nó đã commit, nếu không thì lỗi ràng buộc
     * đã không xảy ra.
     *
     * <p>Đây là lý do lời gọi {@code store.create} phải nằm ở một bean khác:
     * lỗi ràng buộc đánh dấu giao dịch phải cuộn ngược, nên lượt đọc lại
     * <i>phải</i> là một giao dịch mới. Xem ghi chú ở đầu {@link SupportStore}.
     */
    public SupportConversation conversationOf(Actor actor) {
        if (actor.role() == SupportSenderRole.ADMIN) {
            throw new SupportException(SupportException.Reason.SUPPORT_NOT_FOR_ADMIN);
        }

        Optional<SupportConversation> existing = store.findByUser(actor.id());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return store.create(actor.id());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Hai lượt mở luồng hỗ trợ song song cho người {}; dùng lại luồng đã có",
                    actor.id());
            return store.findByUser(actor.id())
                    .orElseThrow(() -> new SupportException(
                            SupportException.Reason.CONVERSATION_NOT_FOUND));
        }
    }

    /**
     * Một lát cắt luồng của người đang gọi.
     *
     * <p>Mở lần đầu ({@code before} và {@code after} đều null) trả về trang mới
     * nhất — đúng thứ trang chat cần. Đây cũng là đường mà trình duyệt gọi lại
     * ở <i>mỗi</i> lần nối lại WebSocket, và đó là chủ ý: nó vá được khoảng hở
     * do {@code auto_increment} sinh ra, thứ mà một lượt "xin phần sau con trỏ"
     * không vá được. Xem ghi chú ở {@code SupportMessage}.
     */
    public SupportThreadDto threadForUser(Actor actor, Long before, Long after, Integer limit) {
        // Cố ý KHÔNG có @Transactional ở đây. Dòng dưới có thể phải INSERT một
        // luồng mới, và một lệnh ghi bên trong giao dịch readOnly của phép đọc
        // sẽ bị trình điều khiển từ chối. Phần đọc thuần có giao dịch riêng của
        // nó, một tầng bên dưới — xem SupportStore.readSlice.
        SupportConversation conversation = conversationOf(actor);
        return slice(conversation.getId(), SupportSenderRole.USER, before, after, limit,
                SupportMessageDto::forUser);
    }

    /**
     * Gửi một tin với tư cách người đọc.
     *
     * <p>Thứ tự bốn bước dưới đây không đổi chỗ được:
     *
     * <pre>
     *   1. xác định luồng từ quyền sở hữu   (không nhận id từ trình duyệt)
     *   2. làm sạch và đo nội dung           (chưa chạm cơ sở dữ liệu)
     *   3. hàng rào tần suất                 (chưa chạm cơ sở dữ liệu)
     *   4. giao dịch ghi                     (ngắn nhất có thể)
     * </pre>
     *
     * Bước 3 phải đứng trước bước 4: một tin bị chặn vì quá tần suất không được
     * để lại dấu vết nào trong cơ sở dữ liệu, nếu không thì hàng rào đã không
     * chặn được đúng thứ nó sinh ra để chặn — sức ép lên cơ sở dữ liệu.
     */
    public SupportStore.Appended sendAsUser(Actor actor, SupportSendRequest request) {
        SupportConversation conversation = conversationOf(actor);

        // Một tin thường không được rơi vào luồng đang do trợ lý phụ trách: nó
        // sẽ nằm đó không có câu trả lời nào bên dưới, và người gửi ngồi chờ
        // mãi. Đường đúng là POST /api/support/ai/messages, thứ gọi Gemini rồi
        // ghi cả hỏi lẫn đáp.
        //
        // Chốt chặn này ở đây chứ không ở controller vì cả REST lẫn WebSocket
        // cùng đi qua chỗ này — và đường WebSocket thì *không* có lựa chọn nào
        // khác: giữ một luồng xử lý socket đứng chờ Gemini ba mươi giây là
        // chuyện không làm, nên nó chỉ có thể từ chối.
        //
        // Không tự động chuyển giao thay người dùng: đổi chế độ sau lưng họ là
        // đúng thứ mà quy tắc 35 của đặc tả cấm.
        if (conversation.getAssistantMode().answeredByAssistant()) {
            throw new SupportException(SupportException.Reason.SUPPORT_ASSISTANT_IN_CHARGE);
        }

        return send(actor, conversation.getId(), SupportSenderRole.USER, request);
    }

    /** Đánh dấu người đọc đã xem tới một tin. Luồng suy từ quyền sở hữu. */
    public SupportConversationDto markReadAsUser(Actor actor, Long lastMessageId) {
        SupportConversation conversation = conversationOf(actor);
        return store.markRead(conversation.getId(), SupportSenderRole.USER, lastMessageId).state();
    }

    /* ------------------------------------------------------------------ */
    /* Quản trị viên                                                       */
    /* ------------------------------------------------------------------ */

    /** Một trang hộp thư hỗ trợ, hoạt động mới nhất trước. */
    @Transactional(readOnly = true)
    public PageResponse<SupportInboxItemDto> inbox(Actor actor,
                                                   SupportConversationStatus status,
                                                   String keyword,
                                                   int page,
                                                   int size) {
        requireAdmin(actor);

        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), properties.inboxPageSize()));
        String trimmed = (keyword == null || keyword.isBlank()) ? null : keyword.strip();

        Page<SupportConversation> found = conversations.search(status, trimmed, pageable);

        // Số chưa đọc cho cả trang trong một câu, không phải một câu cho mỗi
        // dòng. Xem SupportConversationRepository.countAdminUnread.
        Map<Long, Long> unread = unreadByConversation(found.getContent());

        return PageResponse.from(found, conversation -> new SupportInboxItemDto(
                SupportConversationDto.of(conversation, SupportSenderRole.ADMIN,
                        unread.getOrDefault(conversation.getId(), 0L)),
                SupportUserSummaryDto.from(conversation.getUser()),
                conversation.getLastMessagePreview()));
    }

    /** Con số trên tab "Hỗ trợ": còn bao nhiêu luồng đang chờ trả lời. */
    @Transactional(readOnly = true)
    public long awaitingReplyCount(Actor actor) {
        requireAdmin(actor);
        return conversations.countConversationsAwaitingReply();
    }

    /** Một lát cắt của một luồng bất kỳ, nhìn từ phía hỗ trợ. */
    public SupportThreadDto threadForAdmin(Actor actor, Long conversationId,
                                           Long before, Long after, Integer limit) {
        requireAdmin(actor);
        return slice(conversationId, SupportSenderRole.ADMIN, before, after, limit,
                SupportMessageDto::forAdmin);
    }

    /** Chủ luồng, cho tiêu đề màn hình trả lời. */
    @Transactional(readOnly = true)
    public SupportInboxItemDto conversationForAdmin(Actor actor, Long conversationId) {
        requireAdmin(actor);
        SupportConversation conversation = requireConversation(conversationId);
        return new SupportInboxItemDto(
                SupportConversationDto.of(conversation, SupportSenderRole.ADMIN,
                        store.unreadFor(conversation, SupportSenderRole.ADMIN)),
                SupportUserSummaryDto.from(conversation.getUser()),
                conversation.getLastMessagePreview());
    }

    /** Trả lời một luồng với tư cách quản trị viên. */
    public SupportStore.Appended sendAsAdmin(Actor actor, Long conversationId,
                                             SupportSendRequest request) {
        requireAdmin(actor);
        requireConversationId(conversationId);
        return send(actor, conversationId, SupportSenderRole.ADMIN, request);
    }

    /** Đánh dấu phía hỗ trợ đã xem tới một tin. Mốc dùng chung cho cả đội. */
    public SupportConversationDto markReadAsAdmin(Actor actor, Long conversationId,
                                                  Long lastMessageId) {
        requireAdmin(actor);
        requireConversationId(conversationId);
        return store.markRead(conversationId, SupportSenderRole.ADMIN, lastMessageId).state();
    }

    /**
     * Đóng, mở lại, khóa hoặc bỏ khóa một luồng.
     *
     * <p>Trả về dòng hộp thư đã cập nhật kể cả khi lệnh là lệnh rỗng (bấm đóng
     * một luồng đã đóng): giao diện cần một câu trả lời để vẽ lại, và "không có
     * gì đổi" là một câu trả lời hợp lệ chứ không phải một lỗi.
     */
    public SupportInboxItemDto changeStatus(Actor actor, Long conversationId,
                                            SupportConversationStatus status) {
        requireAdmin(actor);
        if (status == null) {
            throw new SupportException(SupportException.Reason.INVALID_STATUS_TRANSITION);
        }
        return store.changeStatus(conversationId, status, actor.id())
                .map(SupportStore.Appended::adminState)
                .orElseGet(() -> conversationForAdmin(actor, conversationId));
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Đường gửi dùng chung cho cả hai phía.
     *
     * <p>Một đường, hai vai trò. Phần khác nhau giữa người đọc và quản trị viên
     * đã được quyết xong ở bên trên — luồng nào, vai trò gì — nên phần còn lại
     * không có nhánh nào, và vì thế không có nhánh nào có thể kiểm thiếu.
     */
    private SupportStore.Appended send(Actor actor, Long conversationId,
                                       SupportSenderRole role, SupportSendRequest request) {
        if (request == null) {
            throw new SupportException(SupportException.Reason.MESSAGE_INVALID);
        }

        String clientMessageId = SupportContent.requireClientId(
                request.clientMessageId(), SupportMessage.CLIENT_ID_LIMIT);
        String content = SupportContent.sanitise(
                request.content(), properties.effectiveMaxMessageLength());

        if (!rateLimiter.tryAcquire(actor.id())) {
            throw new SupportException(SupportException.Reason.SUPPORT_RATE_LIMITED);
        }

        return appendText(actor, conversationId, role, content, clientMessageId);
    }

    /**
     * Ghi một câu hỏi gửi trợ lý AI như một tin nhắn thường của người đọc.
     *
     * <h3>Vì sao đường này tồn tại thay vì gọi thẳng {@code store.append}</h3>
     * Vì phần đáng giá của {@link #send} không phải lệnh ghi — nó là cái
     * {@code catch}: đường đọc lại sau khi ràng buộc duy nhất bắn, thứ phải nằm
     * trong một giao dịch mới và vì thế phải đi qua một bean khác. Gọi thẳng
     * {@code store.append} sẽ là một đường ghi thứ hai thiếu đúng cái nhánh khó
     * nhất, và nó chỉ lộ ra ở một cảnh hiếm mà không ai dựng lại được.
     *
     * <p>Khác {@link #send} ở hai chỗ, cả hai đều vì bên gọi đã làm rồi: nội
     * dung vào đây <i>đã</i> được làm sạch, và lớp kiểm tần suất <i>đã</i> tính
     * một lượt. Tính hai lần cho một lần bấm gửi sẽ làm trần thật bằng một nửa
     * con số ghi trong cấu hình.
     *
     * <p>Vai luôn là {@code USER} và kiểu luôn là {@code TEXT}: câu hỏi cho trợ
     * lý là một câu người đọc gõ ra, không hơn không kém. Nó tính vào số chưa
     * đọc, nó mở lại một luồng đã đóng, và tư vấn viên đọc được nó sau khi
     * chuyển giao — cả ba đều đúng như mong muốn.
     */
    public SupportStore.Appended appendUserQuestion(Actor actor, Long conversationId,
                                                    String content, String clientMessageId) {
        return appendText(actor, conversationId, SupportSenderRole.USER, content, clientMessageId);
    }

    private SupportStore.Appended appendText(Actor actor, Long conversationId,
                                             SupportSenderRole role, String content,
                                             String clientMessageId) {
        try {
            return store.append(conversationId, actor.id(), role,
                    SupportMessageType.TEXT, content, clientMessageId);
        } catch (DataIntegrityViolationException ex) {
            // Hai lần thử lại của cùng một lần bấm gửi lọt qua được phép kiểm
            // trùng bên trong khóa hàng, và ràng buộc duy nhất chặn lại ở tầng
            // cuối. Đọc lại tin đã ghi và trả về nó như một lần trùng — người
            // gửi không cần biết chuyện gì vừa xảy ra bên dưới, vì kết quả với
            // họ là như nhau: câu ấy nằm trong cơ sở dữ liệu đúng một lần.
            //
            // Giao dịch cũ đã bị đánh dấu cuộn ngược, nên lượt đọc lại PHẢI là
            // một giao dịch mới — đó là lý do nó nằm ở một bean khác.
            return store.findAppended(conversationId, actor.id(), clientMessageId)
                    .orElseThrow(() -> {
                        // Ràng buộc bắn vì chuyện khác. Không nuốt: một lỗi
                        // toàn vẹn dữ liệu không rõ nguyên nhân phải nổi lên
                        // thành nhật ký có ngăn xếp, không thành một lời báo
                        // nhận giả.
                        log.error("Lỗi toàn vẹn dữ liệu khi ghi tin hỗ trợ: luồng {}, người gửi {}",
                                conversationId, actor.id(), ex);
                        return ex;
                    });
        }
    }

    /**
     * Bắt buộc có id luồng, và từ chối TRƯỚC khi tiêu một token tần suất.
     *
     * <p>Thứ tự ấy là điểm chính. Không có phép này thì một khung tin thiếu
     * {@code conversationId} vẫn đi qua phần làm sạch nội dung và phần kiểm tần
     * suất rồi mới hỏng ở tầng cơ sở dữ liệu — tức là một máy khách viết sai
     * (hoặc cố tình viết sai) tiêu được hạn mức của chính tài khoản nó bằng
     * những lượt gửi không bao giờ ghi được gì.
     */
    private static void requireConversationId(Long conversationId) {
        if (conversationId == null) {
            throw new SupportException(SupportException.Reason.CONVERSATION_NOT_FOUND);
        }
    }

    private SupportConversation requireConversation(Long conversationId) {
        if (conversationId == null) {
            throw new SupportException(SupportException.Reason.CONVERSATION_NOT_FOUND);
        }
        return conversations.findById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));
    }

    /**
     * Một trang tin nhắn theo con trỏ, luôn trả về theo chiều tăng dần của id.
     *
     * <p>Phần việc thật nằm ở {@link SupportStore#readSlice}, và chỗ nó nằm là
     * một quyết định về <i>giao dịch</i>, không phải về cách chia lớp: đường của
     * người đọc có thể phải tạo luồng trước khi đọc nó, nên nó không mở được
     * một giao dịch chỉ đọc bao trùm cả hai việc. Lý lẽ đầy đủ ở lớp kia.
     *
     * <p>Ở lại đây là hai con số trần, vì chúng là chính sách sản phẩm — xem
     * {@code SupportProperties} — chứ không phải chi tiết của phép đọc.
     */
    private SupportThreadDto slice(Long conversationId,
                                   SupportSenderRole viewer,
                                   Long before,
                                   Long after,
                                   Integer limit,
                                   Function<SupportMessage, SupportMessageDto> mapper) {
        return store.readSlice(conversationId, viewer, before, after, limit,
                properties.historyPageSize(), properties.syncPageSize(), mapper);
    }

    private Map<Long, Long> unreadByConversation(List<SupportConversation> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> byId = new HashMap<>();
        for (var row : conversations.countAdminUnread(page.stream()
                .map(SupportConversation::getId).toList())) {
            byId.put(row.conversationId(), row.unread());
        }
        return byId;
    }

    /**
     * Người đang thực hiện một lệnh, đã được đối chiếu với cơ sở dữ liệu.
     *
     * <h3>Vì sao là một record hai trường chứ không phải {@code AppUserPrincipal}</h3>
     * {@code AppUserPrincipal} là danh tính của một <i>request HTTP</i>: nó được
     * dựng bởi chuỗi filter và mang theo mật khẩu đã băm, danh sách authority,
     * cờ VIP. Bộ xử lý WebSocket không có chuỗi filter nào chạy trước nó, và
     * không việc gì ở đây cần những trường ấy.
     *
     * <p>Hai trường dưới đây là đúng những gì mọi phép kiểm của tính năng này
     * hỏi tới. Giữ nó hẹp cũng là giữ cho không ai nhét thêm vào đây một giá trị
     * đến từ trình duyệt: một {@code Actor} chỉ ra đời từ
     * {@link #resolveActor(Long)}, và hàm ấy chỉ đọc từ cơ sở dữ liệu.
     */
    public record Actor(Long id, SupportSenderRole role) {
    }
}
