package com.storytts.backend.service.notification;

import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/**
 * Tin do quản trị viên tự soạn: gửi cho một người, hoặc loan cho tất cả.
 *
 * <h3>Vì sao không phải một tính năng thông báo riêng</h3>
 * Đây chỉ là một bên gọi nữa của {@link NotificationService} — cùng bảng, cùng
 * cách chống trùng, cùng đường đẩy xuống trình duyệt. Phần riêng của nó vỏn vẹn
 * hai việc: chọn người nhận, và dựng một {@code eventId} cho một sự kiện vốn
 * không có khóa tự nhiên nào.
 *
 * <h3>Nội dung là văn bản thuần</h3>
 * Không có chỗ nào dựng nó thành HTML — trình duyệt đặt nó vào nội dung một thẻ
 * và React tự thoát ký tự. Nên một quản trị viên gõ {@code <script>} sẽ thấy
 * đúng bảy ký tự ấy hiện lên. Backend cũng không nhận URL: nếu tin cần một nút
 * bấm thì nó phải đi qua {@code NotificationAction}, không qua một chuỗi tự do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    /**
     * Cùng múi giờ với hạn mức ngày của {@code GlobalExceptionHandler}.
     *
     * <p>"Cùng một ngày" là khái niệm dùng cho người ngồi ở Việt Nam đang bấm
     * nút, không phải cho UTC.
     */
    private static final ZoneId ANNOUNCEMENT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /** Ai nhận tin này. */
    public enum Target {
        /** Mọi tài khoản còn dùng được. */
        ALL,
        /** Đúng một người. */
        USER
    }

    /**
     * Kết quả một lần gửi, để bảng quản trị nói lại được chuyện gì đã xảy ra.
     *
     * @param recipients số người nằm trong tầm gửi
     * @param created    số thông báo thật sự được ghi; nhỏ hơn {@code recipients}
     *                   nghĩa là phần chênh đã có từ lần bấm trước
     */
    public record Result(int recipients, int created) {
    }

    /**
     * Gửi một tin.
     *
     * <h3>Chống bấm hai lần</h3>
     * Một tin loan chung không có khóa tự nhiên nào — không có id đơn hàng,
     * không có id chương — nên {@code eventId} được dựng từ chính nội dung: băm
     * của (loại, tiêu đề, nội dung), cộng <b>ngày</b> gửi.
     *
     * <pre>
     *   bấm hai lần trong một phút   → cùng eventId → một thông báo
     *   gửi lại đúng câu ấy tháng sau → khác ngày   → một thông báo mới
     * </pre>
     *
     * Ngày chứ không phải giây, vì "bấm hai lần" ở đây gồm cả trường hợp quản
     * trị viên tải lại trang rồi gửi lại vì tưởng lần đầu chưa ăn. Và có ngày
     * trong khóa chứ không phải không có gì, vì một tin bảo trì hàng tháng là
     * chuyện có thật, và nó phải gửi được lần thứ hai.
     *
     * <p>Người nhận cố ý <b>không</b> nằm trong băm: ràng buộc duy nhất là
     * {@code (user_id, event_id)}, nên cùng một chuỗi dùng chung cho cả đợt gửi
     * vẫn chặn được trùng ở từng người. Đổi lại, khóa ấy trở thành thứ tra được
     * — "ai đã nhận tin này" là một câu truy vấn, không phải một cuộc tìm kiếm
     * theo nội dung. Đó cũng là lý do gửi cho một người rồi gửi cho tất cả
     * <i>cùng nội dung, cùng ngày</i> không gửi lại cho người ấy lần thứ hai.
     *
     * @param target    ALL hay USER
     * @param userId    bắt buộc khi {@code target} là USER, bỏ qua khi là ALL
     * @param type      thường là {@code ANNOUNCEMENT} hoặc {@code SYSTEM}
     * @param priority  mức của tin
     * @param title     một dòng, văn bản thuần
     * @param message   vài câu, văn bản thuần
     */
    @Transactional
    public Result send(Target target, Long userId, NotificationType type,
                       NotificationPriority priority, String title, String message) {

        List<Long> recipients = switch (target) {
            case ALL -> userRepository.findEnabledIds();
            case USER -> {
                // Tra thật chứ không tin id gửi lên: gửi vào một tài khoản không
                // tồn tại sẽ tạo ra một hàng có khóa ngoại treo, và quản trị viên
                // nhận về một câu "đã gửi" cho việc không xảy ra.
                if (userId == null || !userRepository.existsById(userId)) {
                    throw ResourceNotFoundException.of("người dùng", userId);
                }
                yield List.of(userId);
            }
        };

        String trimmedTitle = title.trim();
        String trimmedMessage = message.trim();
        String eventId = "admin:" + fingerprint(
                LocalDate.now(ANNOUNCEMENT_ZONE).toString(),
                type.name(), trimmedTitle, trimmedMessage);

        List<NotificationDraft> drafts = recipients.stream()
                .map(recipient -> NotificationDraft.to(recipient)
                        .type(type)
                        .priority(priority)
                        .title(trimmedTitle)
                        .message(trimmedMessage)
                        .event(eventId)
                        .build())
                .toList();

        int created = notificationService.notifyAll(drafts);

        log.info("Quản trị viên gửi thông báo {} tới {} người, ghi được {} dòng",
                type, recipients.size(), created);

        return new Result(recipients.size(), created);
    }

    /**
     * Băm nội dung thành một khóa ngắn, ổn định.
     *
     * <p>Cắt còn 32 ký tự hex (128 bit): cột {@code event_id} dài 120 ký tự và
     * còn phải chứa tiền tố, còn khả năng hai tin khác nhau trùng băm ở 128 bit
     * thì không phải thứ đáng lo ở quy mô này.
     */
    private static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                // Dấu ngăn, để ("ab","c") và ("a","bc") không ra cùng một băm.
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (Exception ex) {
            // SHA-256 luôn có mặt trong mọi JVM; nhánh này là để trình biên dịch
            // yên tâm, không phải một trường hợp thật.
            throw new IllegalStateException("Không băm được nội dung thông báo", ex);
        }
    }
}
