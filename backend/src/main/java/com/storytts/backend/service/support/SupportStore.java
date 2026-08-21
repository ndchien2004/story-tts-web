package com.storytts.backend.service.support;

import com.storytts.backend.domain.SupportAssistantMode;
import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportMessageType;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportInboxItemDto;
import com.storytts.backend.dto.support.SupportMessageDto;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.dto.support.SupportUserSummaryDto;
import com.storytts.backend.exception.SupportException;
import com.storytts.backend.repository.SupportConversationRepository;
import com.storytts.backend.repository.SupportMessageRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Mọi lệnh ghi của hộp thư hỗ trợ, và mọi ranh giới giao dịch của nó.
 *
 * <h3>Vì sao lớp này tách khỏi {@code SupportService}</h3>
 * Vì hai việc mà Spring không cho làm trong cùng một bean:
 *
 * <ol>
 *   <li><b>Bắt lỗi ràng buộc rồi đọc lại.</b> Hai tab cùng bấm "Liên hệ hỗ trợ"
 *       thì một bên nhận {@code DataIntegrityViolationException} từ
 *       {@code UNIQUE (user_id)}. Lỗi ấy đã đánh dấu giao dịch phải cuộn ngược,
 *       nên việc đọc lại hàng đã có <i>phải</i> nằm trong một giao dịch khác.
 *       Gọi từ chính bean này sẽ là một lời gọi thẳng, bỏ qua proxy, và giao
 *       dịch mới không bao giờ được mở. Cùng lối chia đã dùng ở
 *       {@code GiftCodeRedemptionStore} và {@code ChapterEntitlementStore}.</li>
 *   <li><b>Giữ giao dịch thật ngắn.</b> Kiểm tần suất, làm sạch nội dung, dựng
 *       khung tin — không việc nào cần một kết nối cơ sở dữ liệu, nên không việc
 *       nào được đứng bên trong giao dịch. Chúng ở lại {@code SupportService}.</li>
 * </ol>
 *
 * <h3>Ranh giới giao dịch, viết thẳng ra</h3>
 * <pre>
 *   BEGIN
 *     SELECT ... FOR UPDATE   (khóa hàng cuộc trò chuyện)
 *     SELECT                  (đã ghi lần bấm gửi này chưa)
 *     INSERT                  (tin nhắn)
 *     UPDATE                  (bộ nhớ đệm tin cuối + mốc đã đọc của người gửi)
 *     SELECT count(*) × 2     (số chưa đọc của hai phía)
 *   COMMIT
 *   → publish sự kiện → tầng WebSocket đẩy đi
 * </pre>
 *
 * Không có lời gọi mạng nào, không có lượt gửi WebSocket nào, và không có gì chờ
 * trình duyệt <i>bên trong</i> khối trên. Đó là điều kiện chứ không phải chi
 * tiết: một giao dịch mở trong lúc đợi một socket là một kết nối cơ sở dữ liệu
 * bị giữ suốt quãng ấy, và pool ở đây chỉ có mười kết nối.
 *
 * <p>Việc đẩy tin xảy ra sau, ở {@code AFTER_COMMIT} — xem {@code SupportRealtime}.
 * Nên không có đường nào để một khung tin "đã gửi" đi ra trước một giao dịch bị
 * cuộn ngược.
 *
 * <h3>Chống trùng dựa vào ràng buộc, không dựa vào mức cô lập</h3>
 * Hai lần thử lại của <i>cùng</i> một lần bấm gửi là chuyện sẽ xảy ra thật —
 * mạng đứt sau khi máy chủ đã ghi xong nhưng trước khi lời báo nhận về tới nơi.
 * Ba lớp đứng sau nó, và thứ tự quan trọng:
 *
 * <ol>
 *   <li><b>Khóa hàng cuộc trò chuyện.</b> Hai lượt gửi cùng một luồng xếp hàng,
 *       nên lượt sau chỉ chạy sau khi lượt trước đã commit.</li>
 *   <li><b>Phép kiểm trùng bên trong khóa.</b> Đáng tin nhờ một chi tiết của
 *       InnoDB: {@code SELECT ... FOR UPDATE} là một <i>locking read</i>, và
 *       locking read không mở "read view" của {@code REPEATABLE READ}. Ảnh chụp
 *       vì thế được lập ở câu SELECT thường kế tiếp — tức là <i>sau</i> khi
 *       khóa đã về tay, tức là sau khi lượt trước commit.</li>
 *   <li><b>{@code UNIQUE (conversation_id, sender_id, client_message_id)}.</b>
 *       Đây mới là bảo đảm thật, và là thứ duy nhất không phụ thuộc vào chi
 *       tiết nào của cơ sở dữ liệu. Lớp 2 chỉ biến lần thử lại thành một lời báo
 *       nhận "DUPLICATE" gọn gàng thay vì một lỗi ràng buộc.</li>
 * </ol>
 *
 * <p>Lớp 3 vẫn có đường hồi phục: {@code SupportService} bắt
 * {@code DataIntegrityViolationException} rồi đọc lại tin đã ghi trong một giao
 * dịch mới — cùng lối với việc mở luồng ở trên.
 *
 * <h3>Vì sao KHÔNG đặt mức cô lập riêng ở đây</h3>
 * Bản đầu của lớp này ghi {@code @Transactional(isolation = READ_COMMITTED)},
 * và nó <b>hỏng ở mọi lượt gửi trên bản chạy thật</b>:
 *
 * <pre>
 *   InvalidIsolationLevelException: HibernateJpaDialect is not allowed to
 *   support custom isolation levels ... connection release mode ON_CLOSE
 * </pre>
 *
 * Spring chỉ áp được mức cô lập tùy chọn khi nó lấy được kết nối JDBC <i>ngay
 * lúc mở giao dịch</i>, và điều đó đòi chế độ nhả kết nối {@code ON_CLOSE}.
 * Ứng dụng này cố ý đặt {@code DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION}
 * — xem {@code application.properties}, nơi ghi rõ lý do: vài phương thức ghi
 * file hoặc gọi dịch vụ ngoài ở đầu một method {@code @Transactional}, trước
 * mọi câu SQL, nên quãng chờ ấy không được cầm kết nối nào. Đổi cấu hình ấy để
 * chiều một mức cô lập là đánh đổi sai chiều.
 *
 * <p>Và không cần đánh đổi gì: ba lớp ở trên đã đủ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportStore {

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final ApplicationEventPublisher eventPublisher;

    /* ------------------------------------------------------------------ */
    /* Đọc                                                                 */
    /* ------------------------------------------------------------------ */

    /** Luồng của một người đọc, nếu đã có. Người gửi được nạp sẵn. */
    @Transactional(readOnly = true)
    public Optional<SupportConversation> findByUser(Long userId) {
        return conversations.findByUserId(userId);
    }

    /** Một luồng bất kỳ theo id — chỉ đường quản trị dùng tới. */
    @Transactional(readOnly = true)
    public Optional<SupportConversation> findById(Long conversationId) {
        return conversations.findById(conversationId);
    }

    /**
     * Tin đã ghi cho một lần bấm gửi, đọc trong một giao dịch <b>mới</b>.
     *
     * <h3>Đây là đường hồi phục sau khi ràng buộc duy nhất bắn</h3>
     * Nó chỉ chạy ở một nhánh rất hẹp: hai lần thử lại của cùng một lần bấm gửi
     * lọt qua được phép kiểm trùng bên trong khóa hàng, và
     * {@code UNIQUE (conversation_id, sender_id, client_message_id)} chặn lại ở
     * tầng cuối. Trên MySQL/InnoDB nhánh ấy gần như không xảy ra — xem ghi chú
     * ở đầu lớp về locking read — nhưng "gần như" không phải một bảo đảm, và
     * chống trùng thì phải là một bảo đảm.
     *
     * <p>Phải là một giao dịch mới: lỗi ràng buộc đã đánh dấu giao dịch cũ phải
     * cuộn ngược, nên không đọc thêm được gì trong đó. Đó cũng là lý do bên bắt
     * lỗi phải nằm ở {@code SupportService} chứ không ở đây — cùng lối chia với
     * việc mở luồng.
     *
     * @return kết quả mang {@code duplicate = true}, hoặc rỗng nếu tin không có
     *         thật (khi ấy lỗi ràng buộc đến từ chuyện khác và phải nổi lên)
     */
    @Transactional(readOnly = true)
    public Optional<Appended> findAppended(Long conversationId, Long senderId,
                                           String clientMessageId) {
        Optional<SupportMessage> found =
                messages.findByClientId(conversationId, senderId, clientMessageId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));
        SupportMessage message = found.get();

        return Optional.of(new Appended(
                SupportMessageDto.forUser(message),
                SupportMessageDto.forAdmin(message),
                conversationDto(conversation, SupportSenderRole.USER),
                inboxItem(conversation),
                true,
                false));
    }

    /**
     * Số tin chưa đọc của một phía.
     *
     * <p>Một phép đếm dẫn xuất từ mốc, tính lúc cần chứ không lưu sẵn — xem
     * {@code SupportConversation} về lý do không dùng bộ đếm.
     */
    @Transactional(readOnly = true)
    public long unreadFor(SupportConversation conversation, SupportSenderRole viewer) {
        return unreadOf(conversation, viewer);
    }

    /**
     * Một lát cắt luồng: trạng thái, một trang tin nhắn, và có còn nữa không.
     *
     * <h3>Vì sao phép đọc này nằm ở đây chứ không ở {@code SupportService}</h3>
     * Vì nó cần một giao dịch chỉ đọc, và {@code SupportService.threadForUser}
     * <b>không thể</b> mở một cái. Đường ấy bắt đầu bằng "lấy hoặc tạo luồng" —
     * tức là có thể phải {@code INSERT} — và một lệnh ghi bên trong một giao
     * dịch {@code readOnly} bị trình điều khiển từ chối thẳng.
     *
     * <p>Chỗ hỏng ấy còn khó thấy hơn bình thường: {@code @DataJpaTest} gói mọi
     * thứ trong một giao dịch <i>không</i> readOnly của riêng nó, nên một
     * phương thức readOnly gọi từ trong đó chỉ tham gia vào giao dịch ấy và cờ
     * readOnly không có tác dụng. Bài kiểm xanh, còn bản chạy thật thì hỏng
     * ngay ở lần đầu một người mở trang hỗ trợ.
     *
     * <p>Nên ranh giới được đặt ở đây: bên gọi lo phần "lấy hoặc tạo" ngoài
     * giao dịch, rồi phần đọc thuần chạy trong đúng một giao dịch chỉ đọc.
     *
     * <h3>Hai chiều, một hình dạng</h3>
     * Cuộn lên đọc theo chiều giảm rồi đảo lại; bắt kịp sau khi mất kết nối đọc
     * theo chiều tăng. Việc đảo xảy ra đúng một lần, ở đây, thay vì để mỗi màn
     * hình tự nhớ chiều nào đi với đường nào.
     *
     * @param viewer     phía đang xem — quyết định số chưa đọc và mốc nào là
     *                   "của tôi". Do đường đi quyết định, không do trình duyệt.
     * @param mapper     dựng DTO theo dạng của phía ấy — xem {@link SupportMessageDto}
     * @param historySize trần một trang lịch sử
     * @param syncSize    trần một lượt bắt kịp; rộng hơn vì nó chạy một lần cho
     *                    mỗi lần nối lại chứ không mỗi lần cuộn
     */
    @Transactional(readOnly = true)
    public SupportThreadDto readSlice(Long conversationId,
                                      SupportSenderRole viewer,
                                      Long before,
                                      Long after,
                                      Integer limit,
                                      int historySize,
                                      int syncSize,
                                      Function<SupportMessage, SupportMessageDto> mapper) {

        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        SupportConversationDto state = conversationDto(conversation, viewer);

        List<SupportMessage> rows;
        int size;

        if (after != null) {
            size = clamp(limit, syncSize);
            rows = messages.findPageAfter(conversationId, after, PageRequest.of(0, size));
        } else {
            size = clamp(limit, historySize);
            rows = new ArrayList<>(messages.findPageBefore(conversationId, before,
                    PageRequest.of(0, size)));
            // Câu truy vấn chạy ngược để LIMIT lấy đúng phần cuối; giao diện thì
            // dựng theo chiều xuôi.
            Collections.reverse(rows);
        }

        return new SupportThreadDto(state, rows.stream().map(mapper).toList(),
                rows.size() == size);
    }

    private static int clamp(Integer requested, int max) {
        if (requested == null) {
            return max;
        }
        return Math.min(Math.max(requested, 1), max);
    }

    /* ------------------------------------------------------------------ */
    /* Tạo                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Mở luồng cho một người đọc.
     *
     * <p>{@code saveAndFlush} chứ không {@code save}: lệnh INSERT phải chạy
     * <i>ở đây</i> để một lần đụng {@code UNIQUE (user_id)} nổi lên thành
     * {@code DataIntegrityViolationException} mà bên gọi bắt được, thay vì nổi
     * lên lúc commit — nơi không còn ai để bắt và nơi nó biến thành lỗi 500.
     *
     * <p>{@code getReferenceById}: chỉ cần khóa ngoại, không cần đọc bảng users.
     * Bên gọi đã cầm id từ chính phiên đăng nhập.
     */
    @Transactional
    public SupportConversation create(Long userId) {
        User user = users.getReferenceById(userId);
        SupportConversation created = conversations.saveAndFlush(SupportConversation.builder()
                .user(user)
                .status(SupportConversationStatus.OPEN)
                .build());
        log.info("Mở luồng hỗ trợ {} cho người {}", created.getId(), userId);
        return created;
    }

    /* ------------------------------------------------------------------ */
    /* Ghi tin nhắn                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Ghi một tin nhắn, đúng một lần cho mỗi lần bấm gửi.
     *
     * <p>Nội dung đi vào đây đã được làm sạch và đã qua hàng rào tần suất — xem
     * {@code SupportService}. Lớp này không kiểm hai thứ ấy nữa, nhưng nó
     * <b>vẫn</b> kiểm quyền gửi theo trạng thái luồng, vì trạng thái ấy chỉ đọc
     * được sau khi hàng đã bị khóa.
     *
     * @param senderRole vai trò do máy chủ xác định, không bao giờ từ trình duyệt
     * @return kết quả kèm hai dạng DTO của tin nhắn và trạng thái luồng cho cả
     *         hai phía; {@link Appended#duplicate()} true nghĩa là không có gì
     *         được ghi thêm và tin trả về là tin đã có từ lần trước
     */
    @Transactional
    public Appended append(Long conversationId,
                           Long senderId,
                           SupportSenderRole senderRole,
                           SupportMessageType type,
                           String content,
                           String clientMessageId) {

        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        // Kiểm sau khi khóa, không phải trước. Đọc trạng thái rồi mới khóa là
        // đọc một giá trị có thể đã đổi trước khi khóa về tay — đúng cuộc đua
        // "quản trị viên chặn đúng lúc người ta bấm gửi".
        if (type == SupportMessageType.TEXT && !conversation.allowsSendBy(senderRole)) {
            throw new SupportException(SupportException.Reason.CONVERSATION_BLOCKED);
        }

        // Hai đường tra, vì hai loại tin chống trùng bằng hai thứ khác nhau.
        //
        // Tin của người: khóa là (luồng, người gửi, định danh trình duyệt) —
        // bản sao ở tầng truy vấn của ràng buộc UNIQUE trong lược đồ.
        //
        // Tin của trợ lý: không có người gửi để đưa vào khóa, nên khóa là
        // (luồng, vai AI, định danh) — và định danh ấy được suy ra từ id của
        // chính câu hỏi, nên nó chặt hơn chứ không lỏng hơn. Xem V16 và
        // SupportAssistant#replyIdFor.
        Optional<SupportMessage> existing = senderRole == SupportSenderRole.AI
                ? messages.findAssistantMessage(conversationId, clientMessageId)
                : messages.findByClientId(conversationId, senderId, clientMessageId);
        if (existing.isPresent()) {
            SupportMessage message = existing.get();
            log.info("MESSAGE_DUPLICATE ho-tro: luồng {}, người gửi {}, tin {}",
                    conversationId, senderId, message.getId());
            return new Appended(
                    SupportMessageDto.forUser(message),
                    SupportMessageDto.forAdmin(message),
                    conversationDto(conversation, SupportSenderRole.USER),
                    inboxItem(conversation),
                    true,
                    false);
        }

        SupportMessage message = messages.saveAndFlush(SupportMessage.builder()
                .conversation(conversation)
                // null với tin của trợ lý, và chỉ với tin của trợ lý: nó không
                // phải một người, nên cột trỏ tới `users` để trống. Xem V16.
                .sender(senderId == null ? null : users.getReferenceById(senderId))
                .senderRole(senderRole)
                .messageType(type)
                .content(content)
                // Mốc do máy chủ đặt. Đồng hồ của trình duyệt không tham gia.
                .createdAt(Instant.now())
                .clientMessageId(clientMessageId)
                .build());

        // Chỉ tin do người gõ mới mở lại luồng. Thiếu điều kiện này thì lệnh
        // "đóng cuộc trò chuyện" tự hủy chính nó: nó ghi một tin hệ thống ngay
        // sau khi đặt trạng thái CLOSED, và tin ấy sẽ kéo trạng thái về OPEN.
        boolean reopened = type == SupportMessageType.TEXT && conversation.reopenOnNewMessage();

        // Quản trị viên động vào luồng là quản trị viên nhận luồng — kể cả khi
        // việc họ làm là đóng nó lại. Không có nút "nhận việc" riêng, và đó là
        // chủ ý: một nút như thế là một bước người trực có thể quên, và mỗi lần
        // quên là một luồng nằm mãi trong phép đếm chờ trả lời dù đã được trả
        // lời. Hành động thật sự đáng tin hơn một cái bấm nút.
        //
        // Nó cũng là đường AI → HUMAN: người thật nhảy vào một cuộc đang do trợ
        // lý phụ trách thì quyền ưu tiên thuộc về người thật, và trợ lý im ngay
        // từ câu ấy. Chiều ngược lại không bao giờ tự động — xem
        // SupportConversation#startAssistantSession.
        if (senderRole == SupportSenderRole.ADMIN && conversation.takenOverByHuman()) {
            log.info("HANDOFF_TAKEN ho-tro: luồng {} chuyển sang quản trị viên {}",
                    conversationId, senderId);
        }

        conversation.rememberLastMessage(message);

        // Gửi một tin là đã nhìn thấy mọi thứ trước nó. Không đẩy mốc ở đây thì
        // một quản trị viên vừa trả lời xong vẫn thấy luồng ấy còn "chưa đọc"
        // trong hộp thư, và họ sẽ mở lại nó để tìm xem còn thiếu gì.
        conversation.advanceReadMark(senderRole, message.getId());

        conversations.saveAndFlush(conversation);

        Appended result = new Appended(
                SupportMessageDto.forUser(message),
                SupportMessageDto.forAdmin(message),
                conversationDto(conversation, SupportSenderRole.USER),
                inboxItem(conversation),
                false,
                reopened);

        // Người nhận đăng ký ở AFTER_COMMIT, nên phát ở đây là an toàn: giao
        // dịch này hỏng thì không khung tin nào đi ra. Xem SupportRealtime.
        eventPublisher.publishEvent(new SupportMessageCreated(
                conversationId,
                conversation.getUser().getId(),
                result.userView(),
                result.adminView(),
                result.userState(),
                result.adminState()));

        // Cố ý không ghi nội dung vào nhật ký. Đây là thư riêng giữa một người
        // đọc và bộ phận hỗ trợ, và nó có thể chứa địa chỉ email, mã đơn hàng
        // hay lý do khiếu nại. Năm trường dưới đây đủ để lần lại một tin cụ thể
        // mà không chép nó vào tệp log.
        log.info("MESSAGE_PERSISTED ho-tro: tin {}, luồng {}, phía {}, kiểu {}{}",
                message.getId(), conversationId, senderRole, type,
                reopened ? ", mở lại luồng" : "");

        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Đã đọc                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Đẩy mốc đã đọc của một phía lên tới một tin nhất định.
     *
     * <p>Ba phép kiểm, và cả ba đều cần thiết:
     *
     * <ol>
     *   <li>luồng có tồn tại không — nếu không thì {@code CONVERSATION_NOT_FOUND};</li>
     *   <li>tin ấy có <i>thuộc luồng này</i> không. Thiếu phép này thì một id
     *       bất kỳ, kể cả id tin trong luồng của người khác, cũng đẩy được mốc
     *       lên — và số chưa đọc của chính người gửi lệnh sẽ sai theo một cách
     *       không tự sửa được;</li>
     *   <li>mốc mới có thật sự lớn hơn mốc cũ không ({@code advanceReadMark}).
     *       Đây là thứ khiến hai tab bấm lệch nhịp không kéo con số quay lại.</li>
     * </ol>
     *
     * <p>Quyền sở hữu <b>không</b> được kiểm ở đây mà ở tầng trên, nơi biết
     * người gọi là ai — xem {@code SupportService}. Lớp này chỉ nhận vào một
     * phía đã được xác định.
     */
    @Transactional
    public MarkedRead markRead(Long conversationId, SupportSenderRole side, Long lastMessageId) {
        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        if (!messages.existsByIdAndConversationId(lastMessageId, conversationId)) {
            throw new SupportException(SupportException.Reason.INVALID_READ_TARGET);
        }

        boolean changed = conversation.advanceReadMark(side, lastMessageId);
        if (changed) {
            conversations.saveAndFlush(conversation);
        }

        long unread = unreadOf(conversation, side);

        if (changed) {
            eventPublisher.publishEvent(new SupportReadUpdated(
                    conversationId,
                    conversation.getUser().getId(),
                    side,
                    conversation.readMarkOf(side),
                    unread));
        }

        return new MarkedRead(changed, conversationDto(conversation, side, unread));
    }

    /* ------------------------------------------------------------------ */
    /* Trạng thái                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Đổi trạng thái luồng, và ghi lại việc ấy thành một tin hệ thống.
     *
     * <h3>Vì sao đổi trạng thái và ghi tin nằm trong cùng một giao dịch</h3>
     * Vì tin hệ thống <i>là</i> cách người đọc biết chuyện gì đã xảy ra. Tách ra
     * hai giao dịch thì có một trạng thái tệ hại tồn tại được: luồng đã bị chặn
     * nhưng không có dòng nào nói vì sao ô soạn tin biến mất — và người dùng chỉ
     * còn cách đoán.
     *
     * <h3>Lệnh rỗng là lệnh rỗng</h3>
     * Đóng một luồng đã đóng không ghi gì, không phát sự kiện, và không sinh ra
     * một tin hệ thống thứ hai. Đây cũng là lớp chống trùng: hai lần bấm liên
     * tiếp chỉ có lần đầu đi qua, vì cả hai xếp hàng sau cùng một khóa hàng.
     */
    @Transactional
    public Optional<Appended> changeStatus(Long conversationId,
                                           SupportConversationStatus next,
                                           Long actorId) {
        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        SupportConversationStatus previous = conversation.getStatus();
        User actor = users.getReferenceById(actorId);

        if (!conversation.transitionTo(next, actor, Instant.now())) {
            return Optional.empty();
        }

        conversations.saveAndFlush(conversation);

        log.info("Luồng hỗ trợ {}: {} → {} bởi quản trị viên {}",
                conversationId, previous, next, actorId);

        // Tin hệ thống đi qua đúng đường ghi của tin thường — cùng khóa hàng
        // (đã cầm), cùng bộ nhớ đệm tin cuối, cùng sự kiện thời gian thực. Một
        // đường ghi thứ hai cho "cùng một việc nhưng do máy chủ nói" là một
        // đường có thể quên cập nhật một trong số đó.
        //
        // Lời gọi thẳng, không qua proxy: @Transactional trên append() vì thế
        // không có hiệu lực ở đây, và đó đúng là điều cần — hai việc này phải
        // nằm trong *cùng* giao dịch. Ghi rõ ra vì tự gọi trong cùng bean là
        // chỗ mà một người đọc mã dễ tưởng nhầm là có ranh giới giao dịch mới.
        return Optional.of(append(conversationId, actorId, SupportSenderRole.ADMIN,
                SupportMessageType.SYSTEM, systemLine(previous, next),
                UUID.randomUUID().toString()));
    }

    /* ------------------------------------------------------------------ */
    /* Trợ lý AI: nhận luồng, và trả nó lại cho người thật                 */
    /* ------------------------------------------------------------------ */

    /**
     * Người đọc chọn trò chuyện với trợ lý.
     *
     * <p>Cùng hình dạng với {@link #changeStatus}: khóa hàng, đổi <i>một</i>
     * cột, rồi ghi một tin hệ thống trong cùng giao dịch. Tin ấy không phải để
     * trang trí — nó là dấu mốc mà quản trị viên đọc được về sau để biết đoạn
     * nào của bản ghi là người nói với máy. Đọc một luồng đã chuyển giao mà
     * không có mốc ấy thì mọi câu trông như nhau.
     *
     * <p>Lệnh rỗng là lệnh rỗng: chọn AI khi đang ở AI không ghi gì thêm.
     *
     * @throws SupportException nếu luồng đang bị khóa, hoặc đang do một tư vấn
     *                          viên phụ trách dở
     */
    @Transactional
    public Optional<Appended> startAssistantSession(Long conversationId, Long userId) {
        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        SupportAssistantMode previous = conversation.getAssistantMode();
        boolean changed;
        try {
            changed = conversation.startAssistantSession();
        } catch (IllegalStateException ex) {
            // Hai lý do từ chối khác nhau, hai mã lỗi khác nhau: một cái là
            // "bạn bị khóa", một cái là "đang có người thật lo việc này".
            throw new SupportException(
                    conversation.getStatus() == SupportConversationStatus.BLOCKED
                            ? SupportException.Reason.CONVERSATION_BLOCKED
                            : SupportException.Reason.SUPPORT_HUMAN_IN_CHARGE);
        }
        if (!changed) {
            return Optional.empty();
        }

        conversations.saveAndFlush(conversation);
        log.info("AI_SESSION_STARTED ho-tro: luồng {}, người {}, {} → AI",
                conversationId, userId, previous);

        return Optional.of(append(conversationId, userId, SupportSenderRole.USER,
                SupportMessageType.SYSTEM, ASSISTANT_STARTED_LINE,
                UUID.randomUUID().toString()));
    }

    /**
     * Xin chuyển cho tư vấn viên.
     *
     * <h3>Đây là toàn bộ phần "giao dịch chuyển giao" mà đặc tả mô tả</h3>
     * Đặc tả hình dung một chuỗi việc: đánh dấu cần người thật, tạo hoặc dùng
     * lại phiếu hỗ trợ, chống trùng phiếu, giữ nguyên lịch sử. Ở lược đồ này
     * ba việc sau không tồn tại — {@code UNIQUE (user_id)} của V15 đã bảo đảm
     * một người có đúng một luồng vĩnh viễn, nên phiếu <i>đã</i> ở đó, và lịch
     * sử không đi đâu cả vì nó chưa từng ở chỗ khác. Còn lại đúng một việc: đổi
     * một cột.
     *
     * <h3>Bất biến (idempotent), và chỗ giữ điều ấy</h3>
     * Không phải một cờ trong bộ nhớ, cũng không phải một phép kiểm trước khi
     * gọi. Nó là {@code queueForHuman()} chạy <i>bên trong</i> khóa hàng: bấm
     * hai lần, hai tab bấm cùng lúc, hay một lần thử lại sau khi mạng đứt —
     * lần thứ hai trở đi thấy mode đã là {@code HANDOFF} và trả về rỗng. Không
     * có tin hệ thống thứ hai, không có khung tin thứ hai, không có phiếu thứ
     * hai (thứ vốn không dựng được).
     *
     * <h3>Cơ sở dữ liệu là nguồn sự thật, và đó là điều cứu cảnh xấu nhất</h3>
     * Sự kiện thời gian thực phát ở {@code AFTER_COMMIT}. Nếu nó không tới được
     * ai — không quản trị viên nào đang mở trang, máy chủ vừa khởi động lại,
     * mạng đứt giữa chừng — thì trạng thái vẫn đúng trên đĩa, và lần mở hộp thư
     * kế tiếp tìm thấy nó qua {@code countConversationsAwaitingReply}. Không có
     * hàng đợi gửi lại nào, vì không có lần gửi nào là lần cuối cùng.
     *
     * @param reason lý do người đọc nêu, hoặc null. Nó đi vào <i>nội dung</i>
     *               tin hệ thống chứ không vào một cột riêng: đây là một câu
     *               cho người trực đọc, không phải một thứ có ai truy vấn.
     * @return rỗng nếu luồng đã ở trong hàng đợi từ trước
     */
    @Transactional
    public Optional<Appended> requestHandoff(Long conversationId, Long userId, String reason) {
        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        if (conversation.getStatus() == SupportConversationStatus.BLOCKED) {
            throw new SupportException(SupportException.Reason.CONVERSATION_BLOCKED);
        }

        SupportAssistantMode previous = conversation.getAssistantMode();
        if (!conversation.queueForHuman()) {
            log.info("HANDOFF_DUPLICATE ho-tro: luồng {} đã ở hàng đợi", conversationId);
            return Optional.empty();
        }

        conversations.saveAndFlush(conversation);
        log.info("HANDOFF_REQUESTED ho-tro: luồng {}, người {}, {} → HANDOFF{}",
                conversationId, userId, previous, reason == null ? "" : ", có nêu lý do");

        return Optional.of(append(conversationId, userId, SupportSenderRole.USER,
                SupportMessageType.SYSTEM, handoffLine(reason),
                UUID.randomUUID().toString()));
    }

    /**
     * Ghi một câu trả lời của trợ lý — nếu luồng vẫn còn thuộc về trợ lý.
     *
     * <h3>Phép kiểm ở đây là quy tắc bắt buộc số 36 của đặc tả</h3>
     * Cảnh cần chặn: người đọc gõ một câu, lời gọi Gemini bắt đầu, người đọc bấm
     * "Chat với tư vấn viên", việc chuyển giao xong, <i>rồi</i> Gemini mới trả
     * lời. Câu trả lời ấy đã lỗi thời — nó thuộc về một cuộc trò chuyện mà máy
     * không còn phụ trách nữa — và để nó rơi vào sau lời chào của tư vấn viên
     * thì người đọc không còn biết mình đang nói với ai.
     *
     * <p>Chỗ đặt phép kiểm quan trọng ngang nội dung của nó: nó chạy sau khi
     * hàng đã bị {@code SELECT ... FOR UPDATE} giữ, trong cùng giao dịch với
     * lệnh ghi. Kiểm trước khi gọi Gemini thì vô dụng — cuộc đua nằm đúng ở
     * quãng ba mươi giây giữa hai thời điểm ấy. Kiểm sau khi ghi thì đã muộn.
     *
     * <p>Câu trả lời bị bỏ chứ không được ghi rồi ẩn đi. Nó không có giá trị
     * lịch sử nào — không ai từng thấy nó — và một hàng vô hình trong bản ghi
     * là thứ sẽ làm lệch mọi phép đếm về sau.
     *
     * @return rỗng nếu luồng đã rời khỏi tay trợ lý trong lúc chờ
     */
    @Transactional
    public Optional<Appended> appendAssistantReply(Long conversationId,
                                                   String clientMessageId,
                                                   String content) {
        SupportConversation conversation = conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));

        if (!conversation.assistantMayReply()) {
            log.info("AI_REPLY_DROPPED ho-tro: luồng {} đã chuyển sang {} / {}",
                    conversationId, conversation.getAssistantMode(), conversation.getStatus());
            return Optional.empty();
        }

        return Optional.of(append(conversationId, null, SupportSenderRole.AI,
                SupportMessageType.TEXT, content, clientMessageId));
    }

    /**
     * Câu trả lời trợ lý đã ghi cho một câu hỏi, nếu có.
     *
     * <p>Đây là thứ khiến một lượt hỏi trở nên <i>bất biến</i> chứ không chỉ
     * chống trùng: bấm gửi lại cùng một {@code clientMessageId} sau khi mạng đứt
     * thì câu hỏi dedup bằng ràng buộc {@code UNIQUE} như mọi tin khác, và câu
     * trả lời tìm thấy ở đây — nên lần thử lại trả về đúng câu trả lời cũ thay
     * vì tốn thêm một lượt Gemini để sinh ra một câu khác.
     */
    @Transactional(readOnly = true)
    public Optional<SupportMessageDto> findAssistantReply(Long conversationId,
                                                          String clientMessageId) {
        return messages.findAssistantMessage(conversationId, clientMessageId)
                .map(SupportMessageDto::forUser);
    }

    /**
     * Vài lượt gần nhất của luồng, để làm ngữ cảnh cho trợ lý.
     *
     * <h3>Ngữ cảnh đến từ bảng, không đến từ trình duyệt</h3>
     * Đây là khác biệt then chốt so với trợ lý đọc truyện, nơi lịch sử do trình
     * duyệt gửi kèm. Ở đó chấp nhận được vì lịch sử ấy không quyết định quyền
     * gì. Ở đây thì không: một trình duyệt gửi lên lịch sử tự bịa có thể dựng
     * sẵn một "câu trả lời trước" trong đó trợ lý đã hứa hoàn tiền, rồi hỏi tiếp
     * "vậy bao giờ tôi nhận được?". Đọc từ {@code support_messages} thì cảnh ấy
     * không dựng được.
     *
     * <p>Trần số lượt là trần chi phí <i>và</i> trần riêng tư: càng ít lượt cũ
     * thì càng ít thứ rời khỏi máy chủ.
     *
     * @param beforeMessageId lấy những tin trước mốc này — chính là id câu vừa
     *                        hỏi, vì câu ấy được thêm vào chuỗi lượt ở bước sau
     *                        chứ không phải ở đây
     */
    @Transactional(readOnly = true)
    public List<SupportMessageDto> assistantContext(Long conversationId,
                                                    Long beforeMessageId,
                                                    int maxMessages) {
        List<SupportMessage> rows = new ArrayList<>(messages.findPageBefore(
                conversationId, beforeMessageId, PageRequest.of(0, Math.max(maxMessages, 1))));
        Collections.reverse(rows);
        return rows.stream().map(SupportMessageDto::forUser).toList();
    }

    /**
     * Trạng thái luồng đọc dưới khóa hàng, để quyết định có gọi Gemini không.
     *
     * <p>Tách khỏi {@link #appendAssistantReply} vì hai lời gọi ấy nằm ở hai
     * <i>đầu</i> của lời gọi mạng, và giữa chúng không được có giao dịch nào mở
     * — xem quy tắc 31 của đặc tả và {@code SupportAssistant}.
     */
    @Transactional
    public SupportConversation lockAndRead(Long conversationId) {
        return conversations.lockById(conversationId)
                .orElseThrow(() -> new SupportException(
                        SupportException.Reason.CONVERSATION_NOT_FOUND));
    }

    static final String ASSISTANT_STARTED_LINE =
            "Bạn đang trò chuyện với trợ lý AI. Trợ lý giải đáp được các câu hỏi "
                    + "thường gặp; cần người thật thì bấm \"Chat với tư vấn viên\" bất cứ lúc nào.";

    private static String handoffLine(String reason) {
        String head = "Đã chuyển cuộc trò chuyện cho tư vấn viên. "
                + "Bạn không cần kể lại từ đầu — tư vấn viên đọc được toàn bộ nội dung phía trên.";
        return reason == null || reason.isBlank()
                ? head
                : head + "\nLý do: " + reason.strip();
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Câu mà máy chủ tự nói khi trạng thái đổi.
     *
     * <p>Nói cả bước xuất phát chứ không chỉ bước đến: "mở lại" và "bỏ khóa" đều
     * dẫn tới {@code OPEN} nhưng là hai chuyện khác nhau với người đọc, và một
     * câu chung chung sẽ khiến họ không biết mình vừa được bỏ chặn hay chỉ được
     * mở lại một phiếu đã xong.
     */
    private static String systemLine(SupportConversationStatus from,
                                     SupportConversationStatus to) {
        return switch (to) {
            case CLOSED -> "Quản trị viên đã đóng cuộc trò chuyện này. "
                    + "Bạn vẫn có thể gửi tin mới để mở lại.";
            case BLOCKED -> "Quản trị viên đã khóa cuộc trò chuyện này. "
                    + "Bạn không thể gửi thêm tin nhắn.";
            case OPEN -> from == SupportConversationStatus.BLOCKED
                    ? "Quản trị viên đã bỏ khóa cuộc trò chuyện này."
                    : "Quản trị viên đã mở lại cuộc trò chuyện này.";
        };
    }

    private SupportConversationDto conversationDto(SupportConversation conversation,
                                                   SupportSenderRole viewer) {
        return conversationDto(conversation, viewer, unreadOf(conversation, viewer));
    }

    private static SupportConversationDto conversationDto(SupportConversation conversation,
                                                          SupportSenderRole viewer,
                                                          long unread) {
        return SupportConversationDto.of(conversation, viewer, unread);
    }

    /**
     * Số chưa đọc của một phía, và chỗ duy nhất áp quy tắc "luồng của trợ lý
     * không phải việc của người trực".
     *
     * <p>Phép đếm ở tầng truy vấn trả lời một câu hẹp hơn — "bao nhiêu tin của
     * bên kia có id lớn hơn mốc của tôi" — và với một luồng đang do trợ lý phụ
     * trách, câu trả lời ấy đúng nhưng vô nghĩa với quản trị viên: những tin
     * chưa đọc kia là câu hỏi người ta đang hỏi máy, không phải việc ai phải
     * làm. Nên nó bị làm phẳng về không ở đây, một lần, thay vì được nhớ tới ở
     * ba chỗ dựng DTO khác nhau.
     *
     * <p>Phía người đọc thì không có ngoại lệ nào: câu trả lời của trợ lý là
     * một câu gửi cho họ, và nó phải bật con số trên cái nút hỗ trợ đúng như
     * một câu trả lời của tư vấn viên.
     */
    private long unreadOf(SupportConversation conversation, SupportSenderRole viewer) {
        if (viewer == SupportSenderRole.ADMIN
                && !conversation.getAssistantMode().needsHumanAttention()) {
            return 0L;
        }
        return messages.countUnread(conversation.getId(), viewer.incomingFor(),
                conversation.readMarkOf(viewer));
    }

    /**
     * Một dòng hộp thư quản trị.
     *
     * <p>{@code conversation.getUser()} là một tham chiếu lười, và ở đây nó được
     * nạp thật — một câu SELECT theo khóa chính. Cố ý không {@code JOIN FETCH}
     * cùng lệnh khóa hàng ở trên: {@code SELECT ... FOR UPDATE} có kèm phép nối
     * sẽ khóa cả hàng trong bảng {@code users}, và một lượt gửi tin không có lý
     * do gì để chặn một lượt đăng nhập.
     */
    private SupportInboxItemDto inboxItem(SupportConversation conversation) {
        return new SupportInboxItemDto(
                conversationDto(conversation, SupportSenderRole.ADMIN),
                SupportUserSummaryDto.from(conversation.getUser()),
                conversation.getLastMessagePreview());
    }

    /**
     * Kết quả một lượt ghi tin.
     *
     * @param duplicate lần bấm gửi này đã được ghi từ trước; không có gì mới
     *                  xuống cơ sở dữ liệu và không sự kiện nào được phát
     * @param reopened  luồng vừa từ {@code CLOSED} quay lại {@code OPEN}
     */
    public record Appended(
            SupportMessageDto userView,
            SupportMessageDto adminView,
            SupportConversationDto userState,
            SupportInboxItemDto adminState,
            boolean duplicate,
            boolean reopened
    ) {
    }

    /**
     * Kết quả một lượt đánh dấu đã đọc.
     *
     * @param changed mốc có thật sự tiến lên không — bên gọi dùng nó để khỏi đẩy
     *                một khung tin cho một việc không xảy ra
     */
    public record MarkedRead(boolean changed, SupportConversationDto state) {
    }
}
