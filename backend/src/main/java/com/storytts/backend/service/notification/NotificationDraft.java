package com.storytts.backend.service.notification;

import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đơn đặt một thông báo, do nghiệp vụ điền và {@link NotificationService} thực hiện.
 *
 * <h3>Vì sao là một bản ghi có builder chứ không phải một hàm mười tham số</h3>
 * Bốn trường bắt buộc, sáu trường tùy chọn, và trong sáu cái tùy chọn ấy có ba
 * cái cùng kiểu {@code Long}/{@code String}. Một chữ ký mười tham số là chỗ để
 * hoán đổi hai đối số mà trình biên dịch không nói gì — và hậu quả là một thông
 * báo trỏ tới sai truyện.
 *
 * <p>Bên gọi vì thế đọc được thành câu:
 *
 * <pre>
 *   NotificationDraft.to(userId)
 *       .type(CHAPTER_DELETED)
 *       .priority(IMPORTANT)
 *       .title("Chương đã bị gỡ")
 *       .message("…")
 *       .action(VIEW_REFUND_HISTORY)
 *       .about(STORY, storyId)
 *       .meta("refundedCoins", 100)
 *       .event("chapter-deleted:41:7")
 *       .build();
 * </pre>
 *
 * @see NotificationService#notify(NotificationDraft)
 */
public final class NotificationDraft {

    private final Long userId;
    private NotificationType type;
    private NotificationPriority priority = NotificationPriority.INFO;
    private String title;
    private String message;
    private NotificationAction actionType;
    private NotificationEntityType relatedEntityType;
    private Long relatedEntityId;
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private String eventId;

    private NotificationDraft(Long userId) {
        this.userId = userId;
    }

    /** Người nhận. Luôn là một id từ phía máy chủ, không bao giờ từ thân request. */
    public static NotificationDraft to(Long userId) {
        return new NotificationDraft(userId);
    }

    public NotificationDraft type(NotificationType value) {
        this.type = value;
        return this;
    }

    public NotificationDraft priority(NotificationPriority value) {
        this.priority = value;
        return this;
    }

    public NotificationDraft title(String value) {
        this.title = value;
        return this;
    }

    public NotificationDraft message(String value) {
        this.message = value;
        return this;
    }

    public NotificationDraft action(NotificationAction value) {
        this.actionType = value;
        return this;
    }

    /** Thứ thông báo này nói về. Xem {@link NotificationEntityType}: là gợi ý, không phải khóa ngoại. */
    public NotificationDraft about(NotificationEntityType entityType, Long entityId) {
        this.relatedEntityType = entityType;
        this.relatedEntityId = entityId;
        return this;
    }

    /** Một con số phụ cho câu chữ. Bỏ qua giá trị null để bên gọi khỏi phải rẽ nhánh. */
    public NotificationDraft meta(String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    /**
     * Định danh sự kiện nghiệp vụ — thứ chặn thông báo trùng.
     *
     * <p>Phải dựng từ chính sự việc, không phải từ đồng hồ, ở mọi chỗ có sẵn một
     * khóa tự nhiên: {@code payment:<id đơn>}, {@code chapter-deleted:<chương>:<người>}.
     * Xem {@link com.storytts.backend.domain.Notification} về lý do.
     */
    public NotificationDraft event(String value) {
        this.eventId = value;
        return this;
    }

    public Long userId() {
        return userId;
    }

    public NotificationType type() {
        return type;
    }

    public NotificationPriority priority() {
        return priority;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public NotificationAction actionType() {
        return actionType;
    }

    public NotificationEntityType relatedEntityType() {
        return relatedEntityType;
    }

    public Long relatedEntityId() {
        return relatedEntityId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public String eventId() {
        return eventId;
    }

    /**
     * Chốt đơn.
     *
     * <p>Kiểm ngay tại đây chứ không đợi cơ sở dữ liệu từ chối: một
     * {@code eventId} bị quên sẽ chỉ lộ ra ở lần chạy thật, bên trong giao dịch
     * xóa chương, và lúc ấy nó kéo theo cả lệnh xóa.
     */
    public NotificationDraft build() {
        require(userId != null, "userId");
        require(type != null, "type");
        require(title != null && !title.isBlank(), "title");
        require(message != null && !message.isBlank(), "message");
        require(eventId != null && !eventId.isBlank(), "eventId");
        return this;
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("Thông báo thiếu trường bắt buộc: " + field);
        }
    }
}
