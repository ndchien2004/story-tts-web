package com.storytts.backend.service.notification;

import com.storytts.backend.domain.Notification;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tin do quản trị viên soạn tay.
 *
 * <h3>Điều được ghim ở đây</h3>
 * Loan tin là thao tác duy nhất trong cả tính năng có thể ghi hàng nghìn hàng
 * từ một cú bấm nút, và cũng là thao tác duy nhất không có nghiệp vụ nào phía
 * sau để làm khóa chống trùng. Nên hai câu hỏi dưới đây là toàn bộ lý do lớp
 * {@code AdminNotificationService} tồn tại: gửi tới <i>đúng</i> những ai, và
 * bấm hai lần thì sao.
 */
@DataJpaTest
@Import({AdminNotificationService.class, NotificationService.class})
class AdminNotificationJpaTest {

    @Autowired
    private AdminNotificationService adminNotificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;

    private Long readerId;
    private Long otherId;
    private Long lockedId;

    @BeforeEach
    void setUp() {
        readerId = newUser("nguoidoc", "doc@test.local", true);
        otherId = newUser("nguoikhac", "khac@test.local", true);
        lockedId = newUser("bikhoa", "khoa@test.local", false);
    }

    @Test
    @DisplayName("loan tin cho tất cả: mỗi tài khoản còn dùng được nhận một hàng")
    void anAnnouncementReachesEveryActiveAccount() {
        AdminNotificationService.Result result = broadcast("Bảo trì", "Tối nay 23h.");

        assertThat(result.recipients()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(notificationRepository.findAll())
                .extracting(sent -> sent.getUser().getId())
                .containsExactlyInAnyOrder(readerId, otherId);
    }

    @Test
    @DisplayName("tài khoản bị khóa không nhận gì")
    void aLockedAccountIsSkipped() {
        broadcast("Bảo trì", "Tối nay 23h.");

        // Họ không đăng nhập được nên hộp thư của họ không có ai mở; ghi vào đó
        // chỉ làm bảng dài thêm.
        assertThat(notificationRepository.countUnread(lockedId)).isZero();
    }

    @Test
    @DisplayName("bấm gửi hai lần cùng một nội dung trong ngày chỉ tạo một thông báo mỗi người")
    void sendingTwiceInADayLandsOnce() {
        broadcast("Bảo trì", "Tối nay 23h.");
        AdminNotificationService.Result again = broadcast("Bảo trì", "Tối nay 23h.");

        assertThat(notificationRepository.count()).isEqualTo(2);
        // Con số thứ hai là thứ bảng quản trị nói lại: "đã gửi 0/2 người, số còn
        // lại đã nhận tin này từ trước".
        assertThat(again.recipients()).isEqualTo(2);
        assertThat(again.created()).isZero();
    }

    @Test
    @DisplayName("đổi một chữ trong nội dung là một tin khác, và nó gửi được")
    void adifferentMessageIsADifferentAnnouncement() {
        broadcast("Bảo trì", "Tối nay 23h.");
        broadcast("Bảo trì", "Tối nay 23h30.");

        assertThat(notificationRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("gửi riêng cho một người thì chỉ người ấy nhận")
    void aTargetedMessageReachesOnlyOnePerson() {
        AdminNotificationService.Result result = adminNotificationService.send(
                AdminNotificationService.Target.USER, readerId,
                NotificationType.SYSTEM, NotificationPriority.IMPORTANT,
                "Về tài khoản của bạn", "Vui lòng đổi mật khẩu.");

        assertThat(result.recipients()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).singleElement().satisfies(sent -> {
            assertThat(sent.getUser().getId()).isEqualTo(readerId);
            assertThat(sent.getType()).isEqualTo(NotificationType.SYSTEM);
            assertThat(sent.getPriority()).isEqualTo(NotificationPriority.IMPORTANT);
        });
        assertThat(notificationRepository.countUnread(otherId)).isZero();
    }

    @Test
    @DisplayName("gửi tới một tài khoản không tồn tại là 404, không phải một hàng treo")
    void aMissingRecipientIsRefused() {
        // Không tin id gửi lên: ghi bừa sẽ tạo một hàng có khóa ngoại treo, và
        // quản trị viên nhận về câu "đã gửi" cho một việc không xảy ra.
        assertThatThrownBy(() -> adminNotificationService.send(
                AdminNotificationService.Target.USER, 999_999L,
                NotificationType.ANNOUNCEMENT, NotificationPriority.INFO,
                "Xin chào", "Nội dung."))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("thẻ HTML trong nội dung được lưu nguyên văn, không bị diễn giải")
    void markupIsStoredAsPlainText() {
        broadcast("Chú ý", "<script>alert(1)</script>");

        Notification sent = notificationRepository.findAll().get(0);
        // Backend không dựng HTML từ nó, và giao diện đặt nó vào nội dung một
        // thẻ nên React tự thoát ký tự. Lưu nguyên văn là đúng: cắt xén ở đây
        // sẽ làm hỏng những câu chứa dấu ngoặc nhọn một cách hợp lệ.
        assertThat(sent.getMessage()).isEqualTo("<script>alert(1)</script>");
    }

    private AdminNotificationService.Result broadcast(String title, String message) {
        return adminNotificationService.send(
                AdminNotificationService.Target.ALL, null,
                NotificationType.ANNOUNCEMENT, NotificationPriority.INFO,
                title, message);
    }

    private Long newUser(String username, String email, boolean enabled) {
        return userRepository.save(User.builder()
                .username(username).email(email).passwordHash("hash")
                .role(Role.MEMBER).enabled(enabled).build()).getId();
    }
}
