package com.storytts.backend.service.notification;

import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.notification.NotificationDto;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hộp thư, trên một cơ sở dữ liệu thật.
 *
 * <h3>Vì sao phải là cơ sở dữ liệu thật</h3>
 * Hai trong số những điều được khẳng định ở đây <i>là</i> hành vi của cơ sở dữ
 * liệu chứ không phải của Java: ràng buộc duy nhất chặn thông báo trùng, và câu
 * đếm chưa đọc. Mock cả hai thì bài kiểm chỉ khẳng định rằng mock đã được lập
 * trình đúng.
 *
 * <p>Không có kết nối SSE nào trong tệp này, và đó cũng là một khẳng định: mọi
 * thứ dưới đây phải đúng khi người nhận đang offline. Phần đẩy tin được kiểm
 * riêng ở {@code UserEventStreamTest}.
 */
@DataJpaTest
@RecordApplicationEvents
@Import(NotificationService.class)
class NotificationJpaTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;

    /**
     * Sự kiện mà đường ghi đã <i>phát</i>.
     *
     * <p>Phát, không phải gửi: người nhận thật đăng ký ở {@code AFTER_COMMIT},
     * mà {@code @DataJpaTest} thì luôn cuộn ngược nên mốc ấy không bao giờ tới.
     */
    @Autowired
    private ApplicationEvents events;

    private Long readerId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        readerId = newUser("nguoidoc", "doc@test.local");
        otherId = newUser("nguoikhac", "khac@test.local");
    }

    /* ------------------------------------------------------------------ */
    /* Tạo                                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("thông báo được ghi xuống, đúng người, đủ trường")
    void aNotificationIsPersisted() {
        notificationService.notify(draft(readerId, "su-kien-1").build());

        List<com.storytts.backend.domain.Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);

        var saved = all.get(0);
        assertThat(saved.getUser().getId()).isEqualTo(readerId);
        assertThat(saved.getType()).isEqualTo(NotificationType.CHAPTER_DELETED);
        assertThat(saved.getPriority()).isEqualTo(NotificationPriority.IMPORTANT);
        assertThat(saved.getActionType()).isEqualTo(NotificationAction.VIEW_REFUND_HISTORY);
        assertThat(saved.getRelatedEntityType()).isEqualTo(NotificationEntityType.STORY);
        assertThat(saved.getRelatedEntityId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("metadata quay về dạng đối tượng, không phải một chuỗi để bên gọi tự phân tích")
    void metadataRoundTrips() {
        NotificationDto dto = notificationService
                .notify(draft(readerId, "su-kien-1").meta("refundedCoins", 100).build())
                .orElseThrow();

        assertThat(dto.metadata()).containsEntry("refundedCoins", 100);
    }

    @Test
    @DisplayName("người nhận đang offline vẫn có thông báo nằm sẵn trong hộp thư")
    void anOfflineReaderStillGetsOne() {
        // Không có kết nối nào được mở trong cả bài kiểm này. Đó chính là tình
        // huống: quản trị viên cấp VIP lúc người dùng đã tắt máy.
        notificationService.notify(draft(readerId, "vip").type(NotificationType.VIP_GRANTED).build());

        assertThat(notificationService.unreadCount(readerId).unread()).isEqualTo(1);
        assertThat(inbox(readerId).content()).hasSize(1);
    }

    /* ------------------------------------------------------------------ */
    /* Chống trùng                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("cùng một sự kiện gửi hai lần chỉ sinh ra một thông báo")
    void aRepeatedEventCreatesNothingTheSecondTime() {
        assertThat(notificationService.notify(draft(readerId, "chapter-deleted:41:7").build()))
                .isPresent();

        // Lần thứ hai: một webhook gọi lại, một handler chạy hai lượt.
        assertThat(notificationService.notify(draft(readerId, "chapter-deleted:41:7").build()))
                .isEmpty();

        assertThat(notificationRepository.count()).isEqualTo(1);
        // Con số chưa đọc cũng không được nhích: đó là thứ người dùng nhìn thấy.
        assertThat(notificationService.unreadCount(readerId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("cùng eventId nhưng khác người thì là hai thông báo khác nhau")
    void theSameEventReachesTwoPeopleSeparately() {
        // Gỡ một chương mà hai người cùng mua: một sự kiện nghiệp vụ, hai phần.
        notificationService.notify(draft(readerId, "chapter-deleted:41:" + readerId).build());
        notificationService.notify(draft(otherId, "chapter-deleted:41:" + otherId).build());

        assertThat(notificationService.unreadCount(readerId).unread()).isEqualTo(1);
        assertThat(notificationService.unreadCount(otherId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("lần gửi trùng không phát thêm sự kiện thời gian thực nào")
    void aRepeatedEventAnnouncesNothing() {
        notificationService.notify(draft(readerId, "su-kien-1").build());
        notificationService.notify(draft(readerId, "su-kien-1").build());

        assertThat(events.stream(NotificationCreated.class)).hasSize(1);
    }

    /* ------------------------------------------------------------------ */
    /* Đã đọc / chưa đọc                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("thông báo mới là chưa đọc")
    void aNewNotificationIsUnread() {
        NotificationDto dto = notificationService.notify(draft(readerId, "su-kien-1").build())
                .orElseThrow();

        assertThat(dto.read()).isFalse();
        assertThat(dto.readAt()).isNull();
        assertThat(notificationService.unreadCount(readerId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("đánh dấu một cái đã đọc thì số chưa đọc giảm đúng một")
    void markingOneReadDropsTheCount() {
        Long id = notificationService.notify(draft(readerId, "su-kien-1").build())
                .orElseThrow().id();
        notificationService.notify(draft(readerId, "su-kien-2").build());

        assertThat(notificationService.markRead(readerId, id).unread()).isEqualTo(1);
        assertThat(notificationService.unreadCount(readerId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("đánh dấu lại một cái đã đọc là lệnh rỗng, không phải lỗi")
    void markingTheSameOneTwiceChangesNothing() {
        Long id = notificationService.notify(draft(readerId, "su-kien-1").build())
                .orElseThrow().id();

        assertThat(notificationService.markRead(readerId, id).marked()).isEqualTo(1);
        // Hai tab cùng mở một thông báo là chuyện xảy ra thật.
        assertThat(notificationService.markRead(readerId, id).marked()).isZero();
        assertThat(notificationService.unreadCount(readerId).unread()).isZero();
    }

    @Test
    @DisplayName("đánh dấu tất cả dọn sạch cái chuông, và chỉ của chính mình")
    void markingAllReadClearsOnlyYourOwn() {
        notificationService.notify(draft(readerId, "su-kien-1").build());
        notificationService.notify(draft(readerId, "su-kien-2").build());
        notificationService.notify(draft(otherId, "su-kien-1").build());

        assertThat(notificationService.markAllRead(readerId).marked()).isEqualTo(2);
        assertThat(notificationService.unreadCount(readerId).unread()).isZero();
        assertThat(notificationService.unreadCount(otherId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("mỗi lượt đánh dấu đều phát một sự kiện để các cửa sổ khác đồng bộ")
    void everyReadIsAnnouncedForTheOtherWindows() {
        Long id = notificationService.notify(draft(readerId, "su-kien-1").build())
                .orElseThrow().id();

        notificationService.markRead(readerId, id);
        notificationService.notify(draft(readerId, "su-kien-2").build());
        notificationService.markAllRead(readerId);

        List<NotificationsRead> announced = events.stream(NotificationsRead.class).toList();
        assertThat(announced).hasSize(2);
        assertThat(announced.get(0).ids()).containsExactly(id);
        assertThat(announced.get(1).everything()).isTrue();
    }

    @Test
    @DisplayName("không còn gì chưa đọc thì 'đánh dấu tất cả' không phát sự kiện nào")
    void markingAnAlreadyEmptyInboxAnnouncesNothing() {
        assertThat(notificationService.markAllRead(readerId).marked()).isZero();
        assertThat(events.stream(NotificationsRead.class)).isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /* Phân quyền                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("hộp thư chỉ trả về thông báo của chính người hỏi")
    void anInboxHoldsNobodyElsesMail() {
        notificationService.notify(draft(readerId, "cua-toi").build());
        notificationService.notify(draft(otherId, "cua-nguoi-khac").build());

        assertThat(inbox(readerId).content())
                .extracting(NotificationDto::eventId)
                .containsExactly("cua-toi");
    }

    @Test
    @DisplayName("không đánh dấu được thông báo của người khác, kể cả khi biết id")
    void youCannotMarkSomebodyElsesNotification() {
        Long theirs = notificationService.notify(draft(otherId, "cua-nguoi-khac").build())
                .orElseThrow().id();

        // Id là số tự tăng nên đoán được. Câu trả lời phải là 404, không phải
        // một lệnh thành công — xem NotificationRepository.findByIdAndUserId.
        assertThatThrownBy(() -> notificationService.markRead(readerId, theirs))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(notificationService.unreadCount(otherId).unread()).isEqualTo(1);
    }

    @Test
    @DisplayName("'đánh dấu tất cả' của người này không đụng tới hộp thư người kia")
    void markingAllIsScopedToOnePerson() {
        notificationService.notify(draft(otherId, "cua-nguoi-khac").build());

        assertThat(notificationService.markAllRead(readerId).marked()).isZero();
        assertThat(notificationService.unreadCount(otherId).unread()).isEqualTo(1);
    }

    /* ------------------------------------------------------------------ */
    /* Phân trang                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("hộp thư dài được cắt thành trang, mới nhất trước")
    void aLongInboxIsPaged() {
        for (int i = 0; i < 25; i++) {
            notificationService.notify(draft(readerId, "su-kien-" + i).build());
        }

        PageResponse<NotificationDto> first = inbox(readerId, 0, 10);
        assertThat(first.content()).hasSize(10);
        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();

        PageResponse<NotificationDto> last = inbox(readerId, 2, 10);
        assertThat(last.content()).hasSize(5);
        assertThat(last.last()).isTrue();

        // Mới nhất trước: hàng cuối được ghi phải đứng đầu trang đầu.
        assertThat(first.content().get(0).eventId()).isEqualTo("su-kien-24");
    }

    /* ------------------------------------------------------------------ */
    /* Đơn dựng sai                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("đơn thiếu eventId bị chặn ngay, không đợi cơ sở dữ liệu từ chối")
    void aDraftWithoutAnEventIdIsRefusedEarly() {
        // Lỗi ấy mà lọt xuống tầng dưới thì nó nổ bên trong giao dịch xóa
        // chương, và kéo theo cả lệnh xóa.
        assertThatThrownBy(() -> NotificationDraft.to(readerId)
                .type(NotificationType.SYSTEM)
                .title("Tiêu đề")
                .message("Nội dung")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("một đơn hỏng trong lô không kéo theo những đơn còn lại")
    void oneBadDraftDoesNotSinkTheBatch() {
        NotificationDraft broken = NotificationDraft.to(readerId)
                .type(NotificationType.SYSTEM)
                .title("Tiêu đề")
                .message("Nội dung");

        int created = notificationService.notifyAll(List.of(
                draft(readerId, "tot-1").build(),
                broken,
                draft(otherId, "tot-2").build()));

        assertThat(created).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    private NotificationDraft draft(Long userId, String eventId) {
        return NotificationDraft.to(userId)
                .type(NotificationType.CHAPTER_DELETED)
                .priority(NotificationPriority.IMPORTANT)
                .title("Chương bạn đã mua đã bị gỡ")
                .message("100 Xu đã được hoàn lại vào ví của bạn.")
                .action(NotificationAction.VIEW_REFUND_HISTORY)
                .about(NotificationEntityType.STORY, 7L)
                .event(eventId);
    }

    private PageResponse<NotificationDto> inbox(Long userId) {
        return inbox(userId, 0, 20);
    }

    private PageResponse<NotificationDto> inbox(Long userId, int page, int size) {
        return notificationService.inbox(userId,
                PageRequest.of(page, size));
    }

    private Long newUser(String username, String email) {
        return userRepository.save(User.builder()
                .username(username).email(email).passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build()).getId();
    }
}
