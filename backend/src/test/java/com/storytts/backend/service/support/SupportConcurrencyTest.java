package com.storytts.backend.service.support;

import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.repository.SupportConversationRepository;
import com.storytts.backend.repository.SupportMessageRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hộp thư hỗ trợ khi nhiều request chạy <b>thật sự</b> song song.
 *
 * <h3>Vì sao lớp này không phải một {@code @DataJpaTest}</h3>
 * {@code @DataJpaTest} gói mỗi bài kiểm trong một giao dịch rồi cuộn nó lại ở
 * cuối. Tiện cho việc dọn dẹp, nhưng nó làm đúng một chuyện khiến nó vô dụng ở
 * đây: mọi lệnh ghi nằm trong <i>cùng một</i> giao dịch, nên không có hai giao
 * dịch nào để nhìn thấy nhau. Bốn cảnh cần dựng dưới đây đều là cảnh hai giao
 * dịch giành nhau một hàng, và không cảnh nào tồn tại được trong mô hình ấy.
 *
 * <p>Cùng lý lẽ và cùng hình dạng với {@code GiftCodeConcurrencyTest}, lớp đã
 * chứng minh những lời hứa tương tự cho việc đổi Xu.
 *
 * <h3>Bốn lời hứa được chứng minh ở đây</h3>
 * <ol>
 *   <li>hai tab cùng mở hộp thư không tạo ra hai luồng cho một người;</li>
 *   <li>một lần bấm gửi được thử lại song song vẫn chỉ thành một tin nhắn;</li>
 *   <li>đóng luồng đúng lúc người ta đang gõ không nuốt mất câu vừa gõ;</li>
 *   <li>đọc và gửi cùng lúc không làm hỏng con số chưa đọc.</li>
 * </ol>
 *
 * <h3>Về H2</h3>
 * Cơ sở dữ liệu thật là MySQL/InnoDB. Ba cơ chế được dựa vào ở đây —
 * {@code UNIQUE}, {@code SELECT ... FOR UPDATE} giữ khóa tới hết giao dịch, và
 * mức cô lập {@code READ_COMMITTED} — có ở cả hai. Điều H2 không mô phỏng được
 * là cách InnoDB xếp hàng và thời điểm nó nhả khóa; đó là lý do mọi khẳng định
 * bên dưới nói về <i>kết quả</i> ("đúng bao nhiêu hàng tồn tại") chứ không về
 * thứ tự hay về việc luồng nào thắng.
 */
@SpringBootTest
class SupportConcurrencyTest {

    private static final int RACERS = 8;

    @Autowired
    private SupportService supportService;
    @Autowired
    private SupportConversationRepository conversationRepository;
    @Autowired
    private SupportMessageRepository messageRepository;
    @Autowired
    private UserRepository userRepository;

    private SupportService.Actor reader;
    private SupportService.Actor admin;
    private SupportService.Actor otherAdmin;

    @BeforeEach
    void setUp() {
        reader = actor(newUser("dua-doc", "duadoc@test.local", Role.MEMBER));
        admin = actor(newUser("dua-admin", "duaadmin@test.local", Role.ADMIN));
        otherAdmin = actor(newUser("dua-admin-2", "duaadmin2@test.local", Role.ADMIN));
    }

    @AfterEach
    void tearDown() {
        // Dọn tay: lớp này commit thật, nên không có lệnh cuộn ngược nào chạy
        // sau mỗi bài kiểm. Xóa luồng trước, tin nhắn đi theo bằng cascade.
        conversationRepository.deleteAll();
        userRepository.deleteAllById(
                List.of(reader.id(), admin.id(), otherAdmin.id()));
    }

    /* ================================================================== */

    @Test
    @DisplayName("hai tab cùng mở hộp thư chỉ tạo ra một luồng")
    void concurrentOpensCreateOneConversation() throws Exception {
        List<Long> ids = runTogether(() -> supportService.conversationOf(reader).getId());

        // Mọi luồng phải nhận về cùng một id — kể cả luồng thua ở tầng ràng
        // buộc, thứ đã đọc lại hàng của luồng thắng thay vì ném ra ngoài.
        assertThat(ids).hasSize(RACERS).containsOnly(ids.get(0));
        assertThat(conversationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("một lần bấm gửi thử lại tám lần song song vẫn chỉ là một tin nhắn")
    void concurrentRetriesCreateOneMessage() throws Exception {
        Long conversationId = supportService.conversationOf(reader).getId();
        String clientId = UUID.randomUUID().toString();
        AtomicInteger accepted = new AtomicInteger();

        runTogether(() -> {
            var appended = supportService.sendAsUser(reader,
                    new SupportSendRequest(clientId, "mạng chập chờn quá"));
            if (!appended.duplicate()) {
                accepted.incrementAndGet();
            }
            return appended.userView().id();
        });

        assertThat(messageRepository.findAll()).hasSize(1);
        // Đúng một luồng ghi thật; bảy luồng còn lại nhận về "đã có rồi" kèm
        // đúng id ấy. Đó là điều khiến việc thử lại an toàn.
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(conversationRepository.findById(conversationId).orElseThrow()
                .getLastMessageId()).isEqualTo(messageRepository.findAll().get(0).getId());
    }

    @Test
    @DisplayName("tám tin khác nhau gửi cùng lúc: đủ tám hàng, và bộ nhớ đệm tin cuối vẫn đúng")
    void concurrentDistinctSendsAllLand() throws Exception {
        Long conversationId = supportService.conversationOf(reader).getId();

        List<Long> ids = runTogether(() -> supportService.sendAsUser(reader,
                new SupportSendRequest(UUID.randomUUID().toString(), "tin"))
                .userView().id());

        assertThat(messageRepository.findAll()).hasSize(RACERS);
        // Khóa hàng xếp các lượt gửi lại, nên không lượt nào ghi đè bộ nhớ đệm
        // của lượt sau: tin cuối trong luồng đúng là tin có id lớn nhất.
        assertThat(conversationRepository.findById(conversationId).orElseThrow()
                .getLastMessageId()).isEqualTo(ids.stream().max(Long::compareTo).orElseThrow());
    }

    @Test
    @DisplayName("đóng luồng đúng lúc người ta đang gõ: câu vừa gõ không bao giờ mất")
    void aCloseRaceNeverSwallowsAMessage() throws Exception {
        Long conversationId = supportService.conversationOf(reader).getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> closing = pool.submit(() -> {
                await(start);
                return supportService.changeStatus(admin, conversationId,
                        SupportConversationStatus.CLOSED);
            });
            Future<?> sending = pool.submit(() -> {
                await(start);
                return supportService.sendAsUser(reader,
                        new SupportSendRequest(UUID.randomUUID().toString(), "em vẫn chưa hiểu"));
            });

            start.countDown();
            closing.get(10, TimeUnit.SECONDS);
            sending.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Hai kết cục đều hợp lệ và cả hai đều xác định — đóng trước rồi tin
        // tới và mở lại, hoặc tin ghi xong rồi mới đóng. Điều KHÔNG được phép
        // xảy ra là câu ấy biến mất, và đó là thứ được khẳng định ở đây.
        assertThat(messageRepository.findAll())
                .extracting(m -> m.getContent())
                .contains("em vẫn chưa hiểu");

        var conversation = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(conversation.getStatus()).isIn(
                SupportConversationStatus.OPEN, SupportConversationStatus.CLOSED);
    }

    @Test
    @DisplayName("đánh dấu đã đọc trong lúc tin mới đang tới: tin mới không bị coi là đã đọc")
    void aReadRaceNeverMarksTheNewMessage() throws Exception {
        Long conversationId = supportService.conversationOf(reader).getId();
        Long firstReply = supportService.sendAsAdmin(admin, conversationId,
                new SupportSendRequest(UUID.randomUUID().toString(), "chào bạn"))
                .adminView().id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Long secondReply;
        try {
            Future<?> reading = pool.submit(() -> {
                await(start);
                return supportService.markReadAsUser(reader, firstReply);
            });
            Future<Long> replying = pool.submit(() -> {
                await(start);
                return supportService.sendAsAdmin(admin, conversationId,
                        new SupportSendRequest(UUID.randomUUID().toString(), "còn gì nữa không"))
                        .adminView().id();
            });

            start.countDown();
            reading.get(10, TimeUnit.SECONDS);
            secondReply = replying.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Mốc đã đọc trỏ tới tin thứ nhất, nên tin thứ hai — id lớn hơn — vẫn
        // chưa đọc. Một bộ đếm cộng trừ sẽ không đảm bảo được điều này; một mốc
        // đơn điệu thì đảm bảo bằng chính hình dạng của nó.
        var conversation = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(secondReply).isGreaterThan(firstReply);
        assertThat(conversation.getUserLastReadMessageId()).isLessThan(secondReply);
        assertThat(supportService.threadForUser(reader, null, null, null)
                .conversation().unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("hai quản trị viên trả lời cùng lúc: hai tin độc lập, không cái nào nuốt cái nào")
    void twoAdminsMayReplyAtOnce() throws Exception {
        Long conversationId = supportService.conversationOf(reader).getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> one = pool.submit(() -> {
                await(start);
                return supportService.sendAsAdmin(admin, conversationId,
                        new SupportSendRequest(UUID.randomUUID().toString(), "của người thứ nhất"));
            });
            Future<?> two = pool.submit(() -> {
                await(start);
                return supportService.sendAsAdmin(otherAdmin, conversationId,
                        new SupportSendRequest(UUID.randomUUID().toString(), "của người thứ hai"));
            });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(messageRepository.findAll())
                .extracting(m -> m.getContent())
                .containsExactlyInAnyOrder("của người thứ nhất", "của người thứ hai");
    }

    /* ================================================================== */
    /* Tiện ích                                                            */
    /* ================================================================== */

    /**
     * Thả {@link #RACERS} luồng cùng lúc và đợi tất cả xong.
     *
     * <p>Một {@code CountDownLatch} chung thay vì thả lần lượt: thả lần lượt thì
     * luồng đầu tiên thường xong trước khi luồng cuối bắt đầu, và cảnh cần dựng
     * — hai giao dịch cùng mở — không bao giờ xảy ra.
     */
    private <T> List<T> runTogether(Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < RACERS; i++) {
                futures.add(pool.submit(() -> {
                    await(start);
                    return work.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
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
