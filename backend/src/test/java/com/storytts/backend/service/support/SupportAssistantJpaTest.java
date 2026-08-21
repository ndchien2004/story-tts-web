package com.storytts.backend.service.support;

import com.storytts.backend.config.SupportProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.SupportAssistantMode;
import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportMessageType;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.support.SupportMessageDto;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.exception.SupportException;
import com.storytts.backend.repository.SupportConversationRepository;
import com.storytts.backend.repository.SupportMessageRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Máy trạng thái của trợ lý AI, trên một cơ sở dữ liệu thật và không có Gemini.
 *
 * <h3>Vì sao không có Gemini ở đây</h3>
 * Vì không một điều nào dưới đây phụ thuộc vào việc trợ lý <i>nói gì</i>. Chúng
 * phụ thuộc vào việc luồng đang thuộc về ai, và ai nợ câu trả lời — hai câu hỏi
 * mà cơ sở dữ liệu trả lời, không phải nhà cung cấp AI. Kéo Gemini vào đây sẽ
 * làm những bài kiểm này hỏng vì những lý do không liên quan tới thứ chúng
 * khẳng định.
 *
 * <p>Phần thật sự cần một mô hình — dựng lời dặn, đọc dấu chuyển tiếp, xử lý
 * lúc nhà cung cấp hỏng — nằm ở {@code SupportAssistantTest} và
 * {@code SupportAssistantPromptTest}.
 *
 * <h3>Vì sao phải là cơ sở dữ liệu thật</h3>
 * Cùng lý do với {@code SupportJpaTest}, và ở đây còn gắt hơn: ba trong số
 * những điều được khẳng định dưới đây <i>là</i> hành vi của truy vấn chứ không
 * của Java — phép đếm chờ trả lời có hai nhánh, số chưa đọc của quản trị viên
 * bị làm phẳng về không khi luồng thuộc về trợ lý, và tin của trợ lý phải sống
 * sót qua một phép nối tới bảng {@code users} mà nó không có hàng nào trong đó.
 * Điều cuối cùng là một lỗi đã suýt lọt: một {@code JOIN FETCH} thường sẽ lặng
 * lẽ xóa sạch tin của trợ lý khỏi lịch sử, không ném gì cả.
 */
@DataJpaTest
@Import({SupportStore.class, SupportService.class, SupportRateLimiter.class})
@EnableConfigurationProperties(SupportProperties.class)
class SupportAssistantJpaTest {

    @Autowired
    private SupportService supportService;
    @Autowired
    private SupportStore store;
    @Autowired
    private SupportConversationRepository conversations;
    @Autowired
    private SupportMessageRepository messages;
    @Autowired
    private UserRepository userRepository;

    private SupportService.Actor reader;
    private SupportService.Actor admin;

    @BeforeEach
    void setUp() {
        reader = actor(newUser("nguoidoc", "doc@test.local", Role.MEMBER));
        admin = actor(newUser("quantri", "admin@test.local", Role.ADMIN));
    }

    /* ================================================================== */
    /* Tương thích ngược                                                   */
    /* ================================================================== */

    @Nested
    @DisplayName("Tương thích ngược")
    class BackwardCompatible {

        /**
         * Lời hứa trung tâm của cả lần thay đổi này.
         *
         * <p>Mặc định {@code HUMAN} nghĩa là mọi hàng có từ trước V16 — và mọi
         * đường ghi không biết gì về trợ lý — vẫn cư xử đúng như cũ. Nếu mặc
         * định là {@code AI} thì ngày triển khai, mọi luồng hỗ trợ đang mở sẽ
         * lặng lẽ biến mất khỏi hàng đợi của người trực.
         */
        @Test
        @DisplayName("Luồng mới mặc định thuộc về người thật, không thuộc về trợ lý")
        void defaultsToHuman() {
            SupportConversation conversation = supportService.conversationOf(reader);

            assertThat(conversation.getAssistantMode()).isEqualTo(SupportAssistantMode.HUMAN);
            assertThat(conversation.assistantMayReply()).isFalse();
            assertThat(conversation.awaitingFirstWord()).isTrue();
        }

        @Test
        @DisplayName("Người đọc nhắn thẳng cho tư vấn viên vẫn bật huy hiệu như trước")
        void plainMessageStillRaisesBadge() {
            sendAsUser("Cho tôi hỏi về gói VIP");

            assertThat(conversations.countConversationsAwaitingReply()).isEqualTo(1);
        }

        @Test
        @DisplayName("Quản trị viên trả lời xong thì luồng rời khỏi phép đếm")
        void adminReplyClearsBadge() {
            Long id = sendAsUser("Cho tôi hỏi về gói VIP").userView().conversationId();

            supportService.sendAsAdmin(admin, id, send("Chào bạn, VIP gồm..."));

            assertThat(conversations.countConversationsAwaitingReply()).isZero();
        }
    }

    /* ================================================================== */
    /* Chọn trợ lý                                                         */
    /* ================================================================== */

    @Nested
    @DisplayName("Chọn trợ lý")
    class StartingSession {

        @Test
        @DisplayName("Chuyển sang AI và ghi một tin hệ thống làm mốc")
        void startsAndMarks() {
            Long id = supportService.conversationOf(reader).getId();

            Optional<SupportStore.Appended> marked = store.startAssistantSession(id, reader.id());

            assertThat(marked).isPresent();
            assertThat(marked.get().userView().type()).isEqualTo(SupportMessageType.SYSTEM);
            assertThat(reload(id).getAssistantMode()).isEqualTo(SupportAssistantMode.AI);
        }

        /**
         * Cái mốc ấy không phải trang trí: nó là thứ tư vấn viên đọc về sau để
         * biết đoạn nào của bản ghi là người nói với máy. Không có nó thì mọi
         * câu trông như nhau.
         */
        @Test
        @DisplayName("Tin hệ thống mang vai người đọc, vì chính họ đã chọn")
        void systemLineBelongsToTheReader() {
            Long id = supportService.conversationOf(reader).getId();
            store.startAssistantSession(id, reader.id());

            SupportMessage line = lastMessage(id);
            assertThat(line.getSenderRole()).isEqualTo(SupportSenderRole.USER);
            assertThat(line.getMessageType()).isEqualTo(SupportMessageType.SYSTEM);
            assertThat(line.getSender().getId()).isEqualTo(reader.id());
        }

        @Test
        @DisplayName("Chọn lại khi đang ở AI là lệnh rỗng: không có tin hệ thống thứ hai")
        void startingTwiceIsNoOp() {
            Long id = supportService.conversationOf(reader).getId();
            store.startAssistantSession(id, reader.id());

            assertThat(store.startAssistantSession(id, reader.id())).isEmpty();
            assertThat(messages.count()).isEqualTo(1);
        }

        /**
         * Quy tắc 35 của đặc tả: quyền ưu tiên thuộc về người thật. Một cuộc
         * trao đổi đang dở với tư vấn viên không được phép bị trợ lý giành lấy
         * sau lưng cả hai bên.
         */
        @Test
        @DisplayName("Không giành được một luồng đang do tư vấn viên phụ trách dở")
        void refusesWhileHumanIsInCharge() {
            Long id = sendAsUser("Tôi cần hỗ trợ").userView().conversationId();
            supportService.sendAsAdmin(admin, id, send("Tôi đang xem giúp bạn"));

            assertThatThrownBy(() -> store.startAssistantSession(id, reader.id()))
                    .isInstanceOf(SupportException.class)
                    .hasFieldOrPropertyWithValue("reason",
                            SupportException.Reason.SUPPORT_HUMAN_IN_CHARGE);
        }

        /**
         * Và chiều ngược lại của cùng quy tắc ấy: cấm hẳn thì sai, vì luồng ở
         * đây sống suốt đời tài khoản. "Đã từng gặp tư vấn viên một lần" mà cấm
         * vĩnh viễn thì tính năng này coi như không tồn tại với người ấy.
         */
        @Test
        @DisplayName("Nhưng mở lại được sau khi hồ sơ cũ đã đóng")
        void allowedAfterClose() {
            Long id = sendAsUser("Tôi cần hỗ trợ").userView().conversationId();
            supportService.sendAsAdmin(admin, id, send("Xong rồi nhé"));
            store.changeStatus(id, SupportConversationStatus.CLOSED, admin.id());

            assertThat(store.startAssistantSession(id, reader.id())).isPresent();
            assertThat(reload(id).getAssistantMode()).isEqualTo(SupportAssistantMode.AI);
        }

        @Test
        @DisplayName("Người bị khóa không được cấp thêm một đường nói chuyện nào")
        void refusesWhenBlocked() {
            Long id = supportService.conversationOf(reader).getId();
            store.changeStatus(id, SupportConversationStatus.BLOCKED, admin.id());

            assertThatThrownBy(() -> store.startAssistantSession(id, reader.id()))
                    .isInstanceOf(SupportException.class)
                    .hasFieldOrPropertyWithValue("reason",
                            SupportException.Reason.CONVERSATION_BLOCKED);
        }
    }

    /* ================================================================== */
    /* Trợ lý nói                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("Tin nhắn của trợ lý")
    class AssistantMessages {

        @Test
        @DisplayName("Ghi được dù không có hàng nào trong bảng người dùng")
        void persistsWithoutSender() {
            Long id = aiConversation();

            SupportStore.Appended written = store
                    .appendAssistantReply(id, "ai-1", "VIP gồm ba quyền lợi chính…")
                    .orElseThrow();

            SupportMessage row = messages.findById(written.userView().id()).orElseThrow();
            assertThat(row.getSenderRole()).isEqualTo(SupportSenderRole.AI);
            assertThat(row.getSender()).isNull();
            assertThat(row.hasHumanSender()).isFalse();
        }

        /**
         * Lỗi mà một {@code JOIN FETCH} thường sẽ gây ra, và nó không ném gì cả
         * — chỉ là nửa cuộc trò chuyện biến mất sau khi tải lại trang.
         */
        @Test
        @DisplayName("Và đọc lại được trong lịch sử — phép nối phải là LEFT JOIN")
        void survivesHistoryQuery() {
            Long id = aiConversation();
            askAndAnswer(id, "VIP là gì?", "VIP gồm ba quyền lợi chính…");

            SupportThreadDto thread = supportService.threadForUser(reader, null, null, null);

            assertThat(thread.messages())
                    .extracting(SupportMessageDto::senderRole)
                    .contains(SupportSenderRole.AI);
        }

        @Test
        @DisplayName("Cả hai phía đều thấy nhãn trợ lý, không phía nào thấy một cái tên người")
        void labelledAsAssistantOnBothSides() {
            Long id = aiConversation();
            Long replyId = askAndAnswer(id, "VIP là gì?", "VIP gồm…");
            SupportMessage row = messages.findById(replyId).orElseThrow();

            for (SupportMessageDto view : List.of(
                    SupportMessageDto.forUser(row), SupportMessageDto.forAdmin(row))) {
                assertThat(view.senderName()).isEqualTo(SupportMessageDto.ASSISTANT_DISPLAY_NAME);
                assertThat(view.senderId()).isNull();
                assertThat(view.senderAvatarUrl()).isNull();
            }
        }

        /**
         * Câu trả lời của trợ lý là một câu gửi cho người đọc, nên nó phải bật
         * con số trên cái nút hỗ trợ đúng như một câu của tư vấn viên. Đây là
         * lý do phép đếm chưa đọc nhận một <i>tập</i> vai chứ không một giá trị.
         */
        @Test
        @DisplayName("Tính vào số chưa đọc của người đọc, y như câu của tư vấn viên")
        void countsAsUnreadForTheReader() {
            Long id = aiConversation();
            askAndAnswer(id, "VIP là gì?", "VIP gồm…");

            assertThat(store.unreadFor(reload(id), SupportSenderRole.USER)).isEqualTo(1);
        }

        /**
         * Chỗ hở nguy hiểm nhất của việc thêm một vai thứ ba, và nó im lặng:
         * {@code advanceReadMark} cũ chia đôi bằng "USER hay không USER", nên
         * một câu của trợ lý sẽ đẩy mốc của <b>quản trị viên</b> lên và xóa sạch
         * số chưa đọc của một luồng người trực còn chưa mở tới.
         */
        @Test
        @DisplayName("Nhưng KHÔNG đẩy mốc đã đọc của quản trị viên")
        void doesNotTouchTheAdminReadMark() {
            Long id = aiConversation();
            askAndAnswer(id, "VIP là gì?", "VIP gồm…");

            assertThat(reload(id).getAdminLastReadMessageId()).isZero();
        }
    }

    /* ================================================================== */
    /* Huy hiệu đỏ                                                         */
    /* ================================================================== */

    @Nested
    @DisplayName("Huy hiệu của quản trị viên")
    class AdminBadge {

        /** Cả lý do tính năng này tồn tại. */
        @Test
        @DisplayName("Trò chuyện với trợ lý không làm sáng đèn của ai")
        void assistantTrafficIsInvisible() {
            Long id = aiConversation();
            askAndAnswer(id, "Làm sao mở khóa chương?", "Bạn vào trang chương rồi…");
            askAndAnswer(id, "Còn nạp Xu?", "Bạn vào /nap-xu…");

            assertThat(conversations.countConversationsAwaitingReply()).isZero();
            assertThat(store.unreadFor(reload(id), SupportSenderRole.ADMIN)).isZero();
        }

        /**
         * Chỗ mà phép đếm cũ bỏ sót, và là lý do nó cần nhánh thứ hai: người
         * bấm thẳng "Chat với tư vấn viên" rồi ngồi chờ chưa gõ câu nào không
         * có tin chưa đọc nào cả, nhưng rõ ràng đang có người chờ.
         */
        @Test
        @DisplayName("Nhưng một luồng vừa xin gặp người thật thì sáng ngay, dù chưa gõ câu nào")
        void handoffAloneRaisesBadge() {
            Long id = supportService.conversationOf(reader).getId();

            store.requestHandoff(id, reader.id(), null);

            assertThat(messages.countUnread(id,
                    SupportSenderRole.ADMIN.incomingFor(), 0L)).isZero();
            assertThat(conversations.countConversationsAwaitingReply()).isEqualTo(1);
        }

        /**
         * Và nó không tắt chỉ vì có người liếc qua. Đây là một thay đổi so với
         * quy tắc cũ, có chủ đích: một luồng đang chờ tư vấn viên rời khỏi phép
         * đếm khi có người thật sự <i>nhận</i> nó, không phải khi có người mở
         * nó ra đọc.
         */
        @Test
        @DisplayName("Đọc thôi không tắt được huy hiệu của một luồng đang chờ nhận")
        void readingDoesNotClearAHandoff() {
            Long id = aiConversation();
            askAndAnswer(id, "Tôi bị trừ tiền mà chưa nhận Xu", "Việc này cần người thật…");
            store.requestHandoff(id, reader.id(), "nạp Xu chưa nhận");

            store.markRead(id, SupportSenderRole.ADMIN, lastMessage(id).getId());

            assertThat(conversations.countConversationsAwaitingReply()).isEqualTo(1);
        }

        @Test
        @DisplayName("Trả lời thì tắt — và luồng chuyển hẳn sang tay người thật")
        void replyingClearsIt() {
            Long id = aiConversation();
            askAndAnswer(id, "Tôi bị trừ tiền mà chưa nhận Xu", "Việc này cần người thật…");
            store.requestHandoff(id, reader.id(), null);

            supportService.sendAsAdmin(admin, id, send("Chào bạn, tôi đang kiểm tra giao dịch"));

            assertThat(reload(id).getAssistantMode()).isEqualTo(SupportAssistantMode.HUMAN);
            assertThat(conversations.countConversationsAwaitingReply()).isZero();
        }

        /**
         * Không có nút "nhận việc" riêng, và đó là chủ ý: một nút như thế là
         * một bước người trực có thể quên, và mỗi lần quên là một luồng nằm mãi
         * trong phép đếm dù đã được xử lý.
         */
        @Test
        @DisplayName("Đóng luồng cũng là nhận nó — huy hiệu không kẹt lại")
        void closingAlsoCounts() {
            Long id = supportService.conversationOf(reader).getId();
            store.requestHandoff(id, reader.id(), null);

            store.changeStatus(id, SupportConversationStatus.CLOSED, admin.id());

            assertThat(reload(id).getAssistantMode()).isEqualTo(SupportAssistantMode.HUMAN);
            assertThat(conversations.countConversationsAwaitingReply()).isZero();
        }
    }

    /* ================================================================== */
    /* Chuyển giao                                                         */
    /* ================================================================== */

    @Nested
    @DisplayName("Chuyển cho tư vấn viên")
    class Handoff {

        @Test
        @DisplayName("Giữ nguyên toàn bộ lịch sử — không có gì được chép đi đâu cả")
        void keepsEveryWord() {
            Long id = aiConversation();
            askAndAnswer(id, "Tôi bị trừ tiền mà chưa nhận Xu", "Việc này cần người thật…");

            store.requestHandoff(id, reader.id(), "nạp Xu chưa nhận");

            SupportThreadDto asAdmin = supportService.threadForAdmin(admin, id, null, null, null);
            assertThat(asAdmin.messages())
                    .extracting(SupportMessageDto::content)
                    .anySatisfy(text -> assertThat(text).contains("chưa nhận Xu"))
                    .anySatisfy(text -> assertThat(text).contains("cần người thật"));
        }

        @Test
        @DisplayName("Lý do đi vào bản ghi để người trực đọc được")
        void keepsTheReason() {
            Long id = aiConversation();
            store.requestHandoff(id, reader.id(), "nạp Xu chưa nhận");

            assertThat(lastMessage(id).getContent()).contains("nạp Xu chưa nhận");
        }

        /**
         * Quy tắc 14 của đặc tả. Chỗ giữ nó là {@code queueForHuman()} chạy bên
         * trong khóa hàng, không phải một cái cờ ở tầng gọi — nên hai tab bấm
         * cùng lúc cũng chỉ ra một lần chuyển giao. Cảnh song song thật nằm ở
         * {@code SupportConcurrencyTest}.
         */
        @Test
        @DisplayName("Bấm lần thứ hai không sinh thêm gì")
        void isIdempotent() {
            Long id = aiConversation();
            store.requestHandoff(id, reader.id(), null);
            long after = messages.count();

            assertThat(store.requestHandoff(id, reader.id(), null)).isEmpty();
            assertThat(store.requestHandoff(id, reader.id(), null)).isEmpty();
            assertThat(messages.count()).isEqualTo(after);
            assertThat(conversations.count()).isEqualTo(1);
        }

        /**
         * Quy tắc 36 của đặc tả, và là quy tắc duy nhất được đánh dấu bắt buộc.
         *
         * <p>Cảnh: người đọc gõ một câu, lời gọi Gemini bắt đầu, người đọc bấm
         * "Chat với tư vấn viên", chuyển giao xong, rồi Gemini mới trả lời. Câu
         * ấy đã lỗi thời, và để nó rơi vào sau lời chào của tư vấn viên thì
         * người đọc không còn biết mình đang nói với ai.
         */
        @Test
        @DisplayName("Câu trả lời về muộn sau khi đã chuyển giao thì bị bỏ, không được ghi")
        void lateAssistantReplyIsDropped() {
            Long id = aiConversation();
            SupportStore.Appended asked = supportService.appendUserQuestion(
                    reader, id, "Tôi bị trừ tiền", UUID.randomUUID().toString());
            long before = messages.count();

            store.requestHandoff(id, reader.id(), null);

            Optional<SupportStore.Appended> late = store.appendAssistantReply(
                    id, "ai-" + asked.userView().id(), "Tôi đã hoàn Xu cho bạn rồi nhé");

            assertThat(late).isEmpty();
            // Chỉ có tin hệ thống của lần chuyển giao được thêm vào.
            assertThat(messages.count()).isEqualTo(before + 1);
            assertThat(messages.findAll())
                    .noneMatch(m -> m.getSenderRole() == SupportSenderRole.AI);
        }

        @Test
        @DisplayName("Quản trị viên nhảy vào một luồng đang do trợ lý phụ trách thì giành quyền")
        void adminTakesOverFromTheAssistant() {
            Long id = aiConversation();
            askAndAnswer(id, "VIP là gì?", "VIP gồm…");

            supportService.sendAsAdmin(admin, id, send("Chào bạn, để tôi giúp nhé"));

            assertThat(reload(id).getAssistantMode()).isEqualTo(SupportAssistantMode.HUMAN);
            assertThat(reload(id).assistantMayReply()).isFalse();
        }
    }

    /* ================================================================== */
    /* Cửa vào                                                             */
    /* ================================================================== */

    @Nested
    @DisplayName("Cửa vào")
    class Routing {

        /**
         * Một tin thường không được rơi vào luồng đang do trợ lý phụ trách: nó
         * sẽ nằm đó không có câu trả lời nào bên dưới, và người gửi ngồi chờ
         * mãi. Đây cũng là chốt chặn cho đường WebSocket, thứ không gọi Gemini
         * được.
         */
        @Test
        @DisplayName("Gửi tin thường vào luồng của trợ lý bị từ chối, không lặng lẽ ghi")
        void plainSendRefusedInAssistantMode() {
            aiConversation();

            assertThatThrownBy(() -> supportService.sendAsUser(reader, send("VIP là gì?")))
                    .isInstanceOf(SupportException.class)
                    .hasFieldOrPropertyWithValue("reason",
                            SupportException.Reason.SUPPORT_ASSISTANT_IN_CHARGE);
        }

        @Test
        @DisplayName("Sau khi chuyển giao thì đường gửi thường mở lại như thường")
        void plainSendWorksAgainAfterHandoff() {
            Long id = aiConversation();
            store.requestHandoff(id, reader.id(), null);

            assertThat(supportService.sendAsUser(reader, send("Còn đây là ảnh chụp màn hình"))
                    .userView().content()).contains("ảnh chụp");
        }
    }

    /* ================================================================== */
    /* Tiện ích                                                            */
    /* ================================================================== */

    /** Một luồng đã ở chế độ trợ lý, sẵn sàng cho phần thân bài kiểm. */
    private Long aiConversation() {
        Long id = supportService.conversationOf(reader).getId();
        store.startAssistantSession(id, reader.id());
        return id;
    }

    /** Một lượt hỏi–đáp trọn vẹn, đúng đường mà {@code SupportAssistant} đi. */
    private Long askAndAnswer(Long conversationId, String question, String answer) {
        SupportStore.Appended asked = supportService.appendUserQuestion(
                reader, conversationId, question, UUID.randomUUID().toString());
        return store.appendAssistantReply(conversationId,
                        "ai-" + asked.userView().id(), answer)
                .orElseThrow()
                .userView().id();
    }

    private SupportStore.Appended sendAsUser(String content) {
        return supportService.sendAsUser(reader, send(content));
    }

    private static SupportSendRequest send(String content) {
        return new SupportSendRequest(UUID.randomUUID().toString(), content);
    }

    private SupportConversation reload(Long conversationId) {
        return conversations.findById(conversationId).orElseThrow();
    }

    private SupportMessage lastMessage(Long conversationId) {
        return messages.findById(reload(conversationId).getLastMessageId()).orElseThrow();
    }

    private SupportService.Actor actor(Long userId) {
        return supportService.resolveActor(userId);
    }

    private Long newUser(String username, String email, Role role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .passwordHash("{noop}x")
                .displayName(username)
                .role(role)
                .enabled(true)
                .build()).getId();
    }
}
