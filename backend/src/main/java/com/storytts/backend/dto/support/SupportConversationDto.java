package com.storytts.backend.dto.support;

import com.storytts.backend.domain.SupportAssistantMode;
import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.domain.SupportSenderRole;

import java.time.Instant;

/**
 * Trạng thái một luồng hỗ trợ, nhìn từ phía một bên.
 *
 * <h3>"Nhìn từ phía một bên" là phần quan trọng</h3>
 * Ba trong số các trường dưới đây phụ thuộc vào <i>ai đang hỏi</i>:
 *
 * <pre>
 *   unread                → số tin của bên kia mà tôi chưa đọc
 *   lastReadMessageId     → mốc của tôi
 *   peerLastReadMessageId → mốc của bên kia (thứ vẽ ra dấu "đã xem")
 * </pre>
 *
 * Nên lớp này luôn được dựng qua {@link #of(SupportConversation, SupportSenderRole, long)},
 * và {@code viewer} <b>không bao giờ</b> đến từ trình duyệt: nó do đường đi
 * quyết định — {@code /api/support/**} luôn là {@code USER},
 * {@code /api/admin/support/**} luôn là {@code ADMIN}.
 *
 * <h3>Vì sao {@code unread} là tham số chứ không tự tính ở đây</h3>
 * Vì tính nó là một câu truy vấn, và DTO không được phép chạm vào cơ sở dữ liệu.
 * Quan trọng hơn: danh sách hộp thư quản trị tính số ấy cho cả một trang bằng
 * <i>một</i> câu ({@code countAdminUnread}), và một DTO tự tính sẽ lặng lẽ biến
 * việc đó thành N+1 — đúng thứ mà cả thiết kế này tránh.
 *
 * <h3>Những trường cố ý vắng mặt</h3>
 * Không có {@code userId} trong dạng người đọc nhìn thấy: chủ luồng là chính
 * người đang gọi, và nói lại điều đó chỉ mời gọi một trình duyệt tin vào nó thay
 * vì tin vào phiên đăng nhập. Cùng lập luận với {@code NotificationDto}.
 * Khu quản trị cần biết luồng của ai thì đọc {@link SupportInboxItemDto#user()}.
 *
 * <p>Không có {@code lastMessagePreview}: người đọc đang nhìn thẳng vào luồng
 * nên không cần bản xem trước, và một bản sao nội dung tin nhắn không có ai đọc
 * là một chỗ rò dữ liệu không đổi lại được gì. Nó chỉ xuất hiện ở
 * {@link SupportInboxItemDto}, nơi danh sách thật sự cần nó.
 */
public record SupportConversationDto(
        Long id,
        SupportConversationStatus status,
        /**
         * Ai đang nợ câu trả lời: {@code AI}, {@code HANDOFF} hay {@code HUMAN}.
         *
         * <p>Trường thêm ở V16. Giao diện dùng nó cho ba việc: chọn ô soạn tin
         * nào để vẽ, vẽ dải băng "đang kết nối tư vấn viên…", và biết có nên
         * hỏi câu "bạn muốn chat với AI hay gặp tư vấn viên?" hay không — câu
         * cuối cùng cần thêm {@link #lastMessageId} null, xem
         * {@code SupportConversation#awaitingFirstWord}.
         *
         * <p>Một trường cũ không nào diễn đạt được nó, và đó là lý do nó có
         * mặt: {@link #status} nói luồng đang ở giai đoạn nào, không nói ai
         * đang trả lời.
         */
        SupportAssistantMode assistantMode,
        Long lastMessageId,
        Instant lastMessageAt,
        SupportSenderRole lastMessageSenderRole,
        long unread,
        Long lastReadMessageId,
        Long peerLastReadMessageId,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {

    /**
     * @param viewer phía đang xem — do đường đi quyết định, không do trình duyệt
     * @param unread số tin chưa đọc của phía ấy, đã tính sẵn bởi bên gọi
     */
    public static SupportConversationDto of(SupportConversation conversation,
                                            SupportSenderRole viewer,
                                            long unread) {
        return new SupportConversationDto(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getAssistantMode(),
                conversation.getLastMessageId(),
                conversation.getLastMessageAt(),
                conversation.getLastMessageSenderRole(),
                unread,
                conversation.readMarkOf(viewer),
                conversation.readMarkOf(viewer.other()),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getClosedAt());
    }
}
