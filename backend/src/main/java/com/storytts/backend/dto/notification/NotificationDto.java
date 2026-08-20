package com.storytts.backend.dto.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.domain.Notification;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * Một thông báo như trình duyệt nhìn thấy nó.
 *
 * <p>Đúng một hình dạng cho cả hai đường tới nơi — trang gọi
 * {@code GET /api/notifications} và khung tin đẩy xuống luồng SSE. Hai hình
 * dạng khác nhau sẽ nghĩa là hai nhánh dựng giao diện, và cái nhánh ít chạy hơn
 * sẽ là cái sai.
 *
 * <p>Không có {@code userId}: người nhận là người đang gọi, và nói lại điều đó
 * chỉ mời gọi một trình duyệt nào đó tin vào nó thay vì tin vào phiên đăng nhập.
 */
public record NotificationDto(
        Long id,
        NotificationType type,
        NotificationPriority priority,
        String title,
        String message,
        NotificationAction actionType,
        NotificationEntityType relatedEntityType,
        Long relatedEntityId,
        Map<String, Object> metadata,
        String eventId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> METADATA_SHAPE = new TypeReference<>() {
    };

    public static NotificationDto from(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getType(),
                entity.getPriority(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getActionType(),
                entity.getRelatedEntityType(),
                entity.getRelatedEntityId(),
                readMetadata(entity.getMetadata()),
                entity.getEventId(),
                entity.isRead(),
                entity.getReadAt(),
                entity.getCreatedAt());
    }

    /**
     * Trả metadata về dạng đối tượng để trình duyệt khỏi phải tự phân tích chuỗi.
     *
     * <p>Hàng hỏng thì trả về map rỗng chứ không ném: một chuỗi JSON không đọc
     * được là chuyện của <i>một</i> thông báo, và để nó làm hỏng cả trang hộp thư
     * là đổi một khiếm khuyết nhỏ lấy một màn hình trắng. Thứ duy nhất ghi vào
     * cột này là backend, nên nhánh này gần như không bao giờ chạy — nó ở đây
     * cho ngày lược đồ đổi mà dữ liệu cũ ở lại.
     */
    private static Map<String, Object> readMetadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, METADATA_SHAPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
