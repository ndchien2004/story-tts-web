package com.storytts.backend.service;

import com.storytts.backend.domain.Notification;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.service.notification.NotificationCreated;
import com.storytts.backend.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Quản trị viên cấp VIP, và lời chúc mừng đi kèm.
 *
 * <h3>Điều được ghim ở đây</h3>
 * Thông báo phải được ghi <b>trong cùng giao dịch</b> với việc bật cờ VIP, và
 * chỉ khi cờ ấy thật sự đổi. Ba cách làm sai đều có thật:
 *
 * <pre>
 *   ghi trước khi bật cờ   → giao dịch hỏng → lời chúc cho một quyền chưa có
 *   ghi ở giao dịch riêng  → như trên
 *   ghi mỗi lần bấm nút    → bấm hai lần → hai lời chúc cho một lần cấp
 * </pre>
 */
@DataJpaTest
@RecordApplicationEvents
@Import({UserAdminService.class, NotificationService.class})
class VipNotificationJpaTest {

    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationEvents events;

    /** Chỉ dùng ở đường đổi quyền quản trị, không phải ở đường cấp VIP. */
    @MockitoBean
    private CurrentUserService currentUserService;

    private Long memberId;

    @BeforeEach
    void setUp() {
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        memberId = userRepository.save(User.builder()
                .username("nguoidoc").email("doc@test.local").passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build()).getId();
    }

    @Test
    @DisplayName("cấp VIP thành công thì có một thông báo chúc mừng, đúng người")
    void grantingVipCongratulatesTheMember() {
        userAdminService.setVip(memberId, true);

        List<Notification> inbox = notificationRepository.findAll();
        assertThat(inbox).hasSize(1);

        Notification congratulation = inbox.get(0);
        assertThat(congratulation.getUser().getId()).isEqualTo(memberId);
        assertThat(congratulation.getType()).isEqualTo(NotificationType.VIP_GRANTED);
        assertThat(congratulation.getPriority()).isEqualTo(NotificationPriority.SUCCESS);
        assertThat(congratulation.getActionType()).isEqualTo(NotificationAction.VIEW_VIP);
        assertThat(congratulation.isRead()).isFalse();

        // Quyền phải có thật trước khi lời chúc nói về nó.
        assertThat(userRepository.findById(memberId).orElseThrow().isVipGranted()).isTrue();
    }

    @Test
    @DisplayName("thông báo được phát ra để đẩy xuống trình duyệt, kèm số chưa đọc")
    void theCongratulationIsAnnouncedForTheStream() {
        userAdminService.setVip(memberId, true);

        List<NotificationCreated> announced = events.stream(NotificationCreated.class).toList();
        assertThat(announced).hasSize(1);
        assertThat(announced.get(0).userId()).isEqualTo(memberId);
        assertThat(announced.get(0).unread()).isEqualTo(1);
        assertThat(announced.get(0).notification().type()).isEqualTo(NotificationType.VIP_GRANTED);
    }

    @Test
    @DisplayName("bấm 'Cấp VIP' lần thứ hai trên người đã là VIP không chúc mừng lần nữa")
    void grantingTwiceCongratulatesOnce() {
        userAdminService.setVip(memberId, true);
        userAdminService.setVip(memberId, true);

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("thu hồi VIP không sinh thông báo nào")
    void revokingSaysNothing() {
        userAdminService.setVip(memberId, true);
        userAdminService.setVip(memberId, false);

        // Câu duy nhất viết được ở đây là một câu xấu mà người nhận không làm gì
        // được với nó; quyền đọc tự nói ra ở lần mở chương kế tiếp.
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("thao tác bị từ chối thì không có lời chúc nào đi ra")
    void arefusedGrantCongratulatesNobody() {
        Long adminId = userRepository.save(User.builder()
                .username("quantri").email("qt@test.local").passwordHash("hash")
                .role(Role.ADMIN).enabled(true).build()).getId();

        // Cấp VIP cho một tài khoản quản trị bị chặn ở tầng nghiệp vụ. Không có
        // quyền nào được cấp, nên không có gì để chúc mừng.
        assertThatThrownBy(() -> userAdminService.setVip(adminId, true))
                .isInstanceOf(com.storytts.backend.exception.BadRequestException.class);

        assertThat(notificationRepository.count()).isZero();
        assertThat(events.stream(NotificationCreated.class)).isEmpty();
    }
}
