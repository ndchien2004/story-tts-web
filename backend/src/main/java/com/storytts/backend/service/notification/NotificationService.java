package com.storytts.backend.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.domain.Notification;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.notification.NotificationDto;
import com.storytts.backend.dto.notification.UnreadCountDto;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Chỗ duy nhất tạo ra thông báo, và chỗ duy nhất đọc chúng ra.
 *
 * <h3>Vì sao mọi tính năng đi qua đây</h3>
 * Cấp VIP, gỡ chương, hoàn Xu, thanh toán xong, tin chung của quản trị viên —
 * năm nghiệp vụ khác nhau, nhưng cả năm đều phải làm đúng bốn việc giống nhau:
 * ghi vào bảng đúng lúc, chống trùng, dựng bản đẩy xuống trình duyệt, và ghi
 * log đủ để về sau truy được. Mỗi bản sao của bộ ấy là một bản có thể viết
 * thiếu một việc — và cái thiếu sẽ chỉ lộ ra khi có người mất tiền mà không
 * được báo.
 *
 * <p>Nên nghiệp vụ chỉ nói <i>cái gì</i> ({@link NotificationDraft}), còn
 * <i>làm thế nào</i> nằm trọn ở đây.
 *
 * <h3>Ranh giới giao dịch: cố ý không mở giao dịch mới</h3>
 * Mọi phương thức tạo ở đây chạy trong giao dịch của bên gọi
 * ({@code Propagation.REQUIRED} mặc định, và không có {@code REQUIRES_NEW} ở đâu
 * cả). Đó là điều kiện, không phải chi tiết:
 *
 * <pre>
 *   hoàn Xu hỏng → giao dịch cuộn ngược → thông báo "đã hoàn" cũng biến mất
 *   hoàn Xu xong → commit               → thông báo có mặt, và mới được gửi đi
 * </pre>
 *
 * Một giao dịch riêng cho thông báo sẽ tạo ra đúng trạng thái tệ nhất mà đặc tả
 * cấm: một lời báo "đã hoàn tiền" cho một lần hoàn chưa từng xảy ra.
 *
 * <h3>Không có bảng outbox riêng, và vì sao không cần</h3>
 * Mẫu outbox tồn tại để giải quyết một chuyện: cơ sở dữ liệu và đường gửi có
 * thể hỏng độc lập với nhau. Ở đây chuyện ấy đã được giải quyết bằng chính hình
 * dạng của tính năng — <b>bảng {@code notifications} chính là outbox</b>. Nó
 * được ghi trong cùng giao dịch nghiệp vụ, và bên nhận không chờ ai gửi cho
 * mình: trình duyệt tự hỏi lại hộp thư mỗi lần nối lại luồng. Một khung SSE mất
 * trên đường không mất mát gì, vì không có "lần gửi" nào là lần cuối cùng.
 *
 * <p>Thêm một bảng {@code outbox} với {@code status}, {@code attempt_count} và
 * một tác vụ quét lại sẽ là bản sao thứ hai của cùng dữ liệu, cộng một tiến
 * trình nền trên một máy chủ ngủ sau mười lăm phút vắng khách. Nó giải quyết
 * một vấn đề mà thiết kế này không có.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /**
     * Riêng một bản, không lấy bean {@code ObjectMapper} của ứng dụng.
     *
     * <p>Thứ đi qua nó là một {@code Map} vài phần tử toàn số và chuỗi ngắn —
     * không có ngày tháng, không có thực thể, không có gì mà cấu hình chung của
     * ứng dụng nói được điều gì hữu ích. Đổi lại, lớp này dựng được trong một
     * lát cắt {@code @DataJpaTest}, nơi Jackson không được tự cấu hình.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Đúng chiều dài hai cột tương ứng trong lược đồ — xem V14. */
    private static final int TITLE_LIMIT = 160;
    private static final int MESSAGE_LIMIT = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /* ------------------------------------------------------------------ */
    /* Tạo                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Ghi một thông báo, đúng một lần cho mỗi sự kiện nghiệp vụ.
     *
     * <h3>Chống trùng</h3>
     * {@code eventId} được hỏi trước, và {@code UNIQUE (user_id, event_id)} đứng
     * sau làm chốt chặn. Phép hỏi trước là để lần thứ hai của cùng một sự kiện
     * — webhook gọi lại, một lần thử lại của trình duyệt — trở thành một lệnh
     * rỗng thay vì một lỗi ràng buộc kéo theo cả giao dịch nghiệp vụ. Ràng buộc
     * ở tầng dưới là để hai luồng song song không cùng lọt qua phép hỏi ấy.
     *
     * <p>Cần nói rõ giới hạn của nó: nó chặn <b>thông báo</b> trùng, không chặn
     * <b>nghiệp vụ</b> chạy hai lần. Tiền không bị hoàn hai lần là nhờ ví và
     * bảng quyền đọc, và điều đó phải đúng kể cả khi bảng này trống.
     *
     * @return thông báo vừa ghi, hoặc rỗng nếu sự kiện ấy đã được ghi từ trước
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<NotificationDto> notify(NotificationDraft draft) {
        draft.build();

        if (notificationRepository.existsByUserIdAndEventId(draft.userId(), draft.eventId())) {
            log.debug("Bỏ qua thông báo trùng: người {} đã có sự kiện {}",
                    draft.userId(), draft.eventId());
            return Optional.empty();
        }

        // getReferenceById: chỉ cần khóa ngoại, không cần đọc bảng users. Người
        // gọi đã cầm id từ chính nghiệp vụ của họ.
        User recipient = userRepository.getReferenceById(draft.userId());

        Notification saved = notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(draft.type())
                .priority(draft.priority())
                .title(clamp(draft.title(), TITLE_LIMIT))
                .message(clamp(draft.message(), MESSAGE_LIMIT))
                .actionType(draft.actionType())
                .relatedEntityType(draft.relatedEntityType())
                .relatedEntityId(draft.relatedEntityId())
                .metadata(writeMetadata(draft.metadata()))
                .eventId(draft.eventId())
                .build());

        long unread = notificationRepository.countUnread(draft.userId());
        NotificationDto dto = NotificationDto.from(saved);

        // Người nhận đăng ký ở AFTER_COMMIT, nên phát ở đây là an toàn: giao
        // dịch này hỏng thì không khung tin nào đi ra. Xem UserEventStream.
        eventPublisher.publishEvent(new NotificationCreated(draft.userId(), dto, unread));

        // Cố ý không ghi title/message vào log: chúng có thể mang tên chương một
        // người đã mua và số Xu họ đã trả. Bốn trường dưới đây đủ để lần lại một
        // thông báo cụ thể mà không chép nội dung của nó vào tệp log.
        log.info("Thông báo {} cho người {}: loại {}, sự kiện {}",
                saved.getId(), draft.userId(), draft.type(), draft.eventId());

        return Optional.of(dto);
    }

    /**
     * Nhiều thông báo trong cùng một giao dịch — gỡ một truyện, loan một tin chung.
     *
     * <p>Một đơn <i>dựng sai</i> — thiếu tiêu đề, thiếu {@code eventId} — không
     * được kéo theo những đơn còn lại: đó là lỗi lập trình ở một nhánh câu chữ,
     * và để nó hủy cả lệnh gỡ truyện thì hai mươi chín người kia cũng mất phần
     * hoàn Xu. Nó được ghi lại rồi bỏ qua.
     *
     * <p>Lỗi của <i>cơ sở dữ liệu</i> thì cố ý không bắt, và sự khác nhau ấy là
     * điểm chính. Một lỗi ràng buộc đã đánh dấu giao dịch phải cuộn ngược; nuốt
     * nó ở đây chỉ khiến lệnh cuộn ngược lộ ra muộn hơn, ở chỗ khó lần hơn.
     *
     * @return số thông báo thật sự được ghi
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public int notifyAll(Collection<NotificationDraft> drafts) {
        int created = 0;
        for (NotificationDraft draft : drafts) {
            try {
                if (notify(draft).isPresent()) {
                    created++;
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Bỏ qua một thông báo dựng sai cho người {}: {}",
                        draft.userId(), ex.getMessage());
            }
        }
        return created;
    }

    /* ------------------------------------------------------------------ */
    /* Đọc                                                                 */
    /* ------------------------------------------------------------------ */

    /** Một trang hộp thư của chính người đang gọi, mới nhất trước. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> inbox(Long userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findInbox(userId, pageable),
                NotificationDto::from);
    }

    /** Con số trên cái chuông. */
    @Transactional(readOnly = true)
    public UnreadCountDto unreadCount(Long userId) {
        return UnreadCountDto.of(notificationRepository.countUnread(userId));
    }

    /* ------------------------------------------------------------------ */
    /* Đánh dấu đã đọc                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Đánh dấu một thông báo là đã đọc.
     *
     * <p>Tra theo cặp {@code (id, userId)} chứ không theo mình id: id thông báo
     * là số tự tăng nên đoán được, và một người thử id của người khác phải nhận
     * về 404 chứ không phải một lệnh thành công.
     *
     * <p>Gọi lại trên một thông báo đã đọc là lệnh rỗng, không phải lỗi — nó xảy
     * ra thật mỗi khi hai tab cùng mở một thông báo.
     */
    @Transactional
    public UnreadCountDto markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("thông báo", notificationId));

        boolean changed = notification.markRead(Instant.now());
        if (changed) {
            notificationRepository.saveAndFlush(notification);
        }

        long unread = notificationRepository.countUnread(userId);
        if (changed) {
            eventPublisher.publishEvent(NotificationsRead.one(userId, notificationId, unread));
        }

        return new UnreadCountDto(unread, changed ? 1 : 0);
    }

    /** Dọn sạch cái chuông. Lệnh rỗng khi vốn đã không còn gì chưa đọc. */
    @Transactional
    public UnreadCountDto markAllRead(Long userId) {
        int marked = notificationRepository.markAllRead(userId, Instant.now());
        if (marked > 0) {
            eventPublisher.publishEvent(NotificationsRead.all(userId));
        }
        return new UnreadCountDto(0L, marked);
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Cắt cho vừa cột, thay vì để cơ sở dữ liệu từ chối cả hàng.
     *
     * <h3>Vì sao cắt chứ không ném</h3>
     * Câu chữ ở đây được ghép từ tên chương và tên truyện, và cả hai đều tới
     * 255 ký tự. Với những mẫu câu hiện có thì tổng vẫn nằm trong hạn — nhưng
     * "hiện có" là một trạng thái, không phải một đảm bảo. Ngày ai đó viết một
     * mẫu câu dài hơn, cái giá phải là một dòng thông báo bị cụt, chứ không
     * phải một lệnh gỡ chương bị cuộn ngược và người mua không được hoàn Xu.
     *
     * <p>Dấu ba chấm để phần bị cắt nhìn ra được, thay vì kết thúc giữa chừng
     * một câu trông như hoàn chỉnh.
     */
    private static String clamp(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        log.warn("Cắt bớt nội dung thông báo: {} ký tự, trần {}", value.length(), limit);
        return value.substring(0, limit - 1) + "…";
    }

    /**
     * Vài con số phụ thành một chuỗi JSON.
     *
     * <p>Rỗng thì lưu null chứ không lưu {@code "{}"}: một chuỗi hai ký tự lặp
     * lại trên mọi hàng không nói gì mà một cột null chưa nói.
     */
    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (Exception ex) {
            // Không đáng để mất cả thông báo: câu chữ chính nằm ở title/message,
            // metadata chỉ tô điểm thêm cho nó.
            log.warn("Không tuần tự hóa được metadata thông báo: {}", ex.getMessage());
            return null;
        }
    }
}
