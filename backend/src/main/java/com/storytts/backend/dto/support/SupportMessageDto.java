package com.storytts.backend.dto.support;

import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportMessageType;
import com.storytts.backend.domain.SupportSenderRole;
import com.storytts.backend.domain.User;

import java.time.Instant;

/**
 * Một tin nhắn như trình duyệt nhìn thấy nó.
 *
 * <h3>Hai cách dựng, vì hai bên được biết những thứ khác nhau</h3>
 * Đây là điểm đáng đọc kỹ nhất của lớp này. Cùng một hàng trong cơ sở dữ liệu
 * đi ra hai màn hình khác nhau, và danh tính người gửi <b>không</b> giống nhau ở
 * hai chỗ ấy:
 *
 * <pre>
 *   {@link #forUser}  → câu của quản trị viên hiện là "Hỗ trợ viên", không id,
 *                       không ảnh đại diện
 *   {@link #forAdmin} → hiện đúng tên người đã trả lời
 * </pre>
 *
 * Lý do của vế đầu là quyền riêng tư của nhân sự: người đọc gửi một yêu cầu hỗ
 * trợ không có nhu cầu — và không nên có khả năng — biết tên tài khoản của từng
 * quản trị viên, lập danh sách họ, hay nhắm vào một người cụ thể. Lý do của vế
 * sau là vận hành: hộp thư hỗ trợ là hàng đợi dùng chung, và một đội không phân
 * biệt được ai đã trả lời câu nào thì hai người sẽ cùng trả lời một câu.
 *
 * <p>Việc chọn dạng nào <b>không</b> do trình duyệt quyết định. Nó do đường đi
 * quyết định: {@code /api/support/**} luôn dựng dạng thứ nhất,
 * {@code /api/admin/support/**} luôn dựng dạng thứ hai, và khung tin thời gian
 * thực mang sẵn cả hai để mỗi phía nhận đúng phần của mình — xem
 * {@code SupportMessageCreated}.
 *
 * <h3>Những trường cố ý vắng mặt</h3>
 * Không có {@code status} kiểu SENT/DELIVERED/READ. Trạng thái ấy không thuộc về
 * tin nhắn mà thuộc về quan hệ giữa nó và người đọc bên kia, nên nó được suy ra
 * ở trình duyệt từ {@code peerLastReadMessageId} của cuộc trò chuyện: một con số
 * cho cả luồng, thay vì một cột phải cập nhật cho từng hàng mỗi lần ai đó cuộn
 * xuống đáy.
 *
 * @param senderId     null với câu của quản trị viên khi người xem là người đọc
 * @param senderName   tên hiển thị, hoặc {@link #SUPPORT_DISPLAY_NAME}
 * @param content      văn bản thuần. Giao diện dựng nó bằng text node, không bao
 *                     giờ bằng HTML — xem {@code SupportThread.jsx}
 */
public record SupportMessageDto(
        Long id,
        Long conversationId,
        SupportSenderRole senderRole,
        SupportMessageType type,
        Long senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        String clientMessageId,
        Instant createdAt
) {

    /** Cái tên mà cả phía hỗ trợ dùng chung khi nói với người đọc. */
    public static final String SUPPORT_DISPLAY_NAME = "Hỗ trợ viên";

    /**
     * Cái tên của trợ lý, và nó không bao giờ được phép trông giống một người.
     *
     * <p>Đặc tả gọi "giả làm tư vấn viên" là một điều cấm chứ không phải một
     * tùy chọn giao diện, và chỗ thi hành điều cấm ấy là ở đây — trong lớp mà
     * <i>mọi</i> đường đọc đều đi qua — chứ không phải ở một thành phần React
     * mà một màn hình khác có thể quên.
     */
    public static final String ASSISTANT_DISPLAY_NAME = "Trợ lý AI";

    /** Dạng người đọc nhìn thấy. Xem ghi chú ở đầu lớp. */
    public static SupportMessageDto forUser(SupportMessage message) {
        if (message.getSenderRole() == SupportSenderRole.AI) {
            return assistantView(message);
        }
        boolean fromSupport = message.getSenderRole() == SupportSenderRole.ADMIN;
        return new SupportMessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderRole(),
                message.getMessageType(),
                fromSupport ? null : message.getSender().getId(),
                fromSupport ? SUPPORT_DISPLAY_NAME : displayNameOf(message.getSender()),
                fromSupport ? null : message.getSender().getAvatarUrl(),
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt());
    }

    /** Dạng khu quản trị nhìn thấy. Xem ghi chú ở đầu lớp. */
    public static SupportMessageDto forAdmin(SupportMessage message) {
        // Quản trị viên cũng thấy đúng nhãn ấy, và đó không phải chuyện thẩm mỹ:
        // một luồng đã chuyển giao là bản ghi trộn lẫn câu của người và câu của
        // máy, và người trực phải đọc ra được đoạn nào là đoạn nào trước khi
        // trả lời tiếp.
        //
        // Phép kiểm là hasHumanSender() chứ không phải so vai với AI: nó cũng
        // bắt luôn trường hợp một hàng cũ nào đó mất người gửi, và một bong bóng
        // vô danh vẫn hơn một NullPointerException giữa hộp thư.
        if (!message.hasHumanSender()) {
            return assistantView(message);
        }
        return new SupportMessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderRole(),
                message.getMessageType(),
                message.getSender().getId(),
                displayNameOf(message.getSender()),
                message.getSender().getAvatarUrl(),
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt());
    }

    /**
     * Dạng chung cho tin của trợ lý, giống hệt ở cả hai phía.
     *
     * <p>{@code senderId} và {@code senderAvatarUrl} để null vì không có gì
     * thật để đặt vào đó: trợ lý không có hàng trong {@code users}. Xem V16 về
     * vì sao đó là câu trả lời đúng thay vì một tài khoản ma.
     */
    private static SupportMessageDto assistantView(SupportMessage message) {
        return new SupportMessageDto(
                message.getId(),
                message.getConversation().getId(),
                SupportSenderRole.AI,
                message.getMessageType(),
                null,
                ASSISTANT_DISPLAY_NAME,
                null,
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt());
    }

    /**
     * Tên để hiện, có đường lui.
     *
     * <p>{@code display_name} được điền lúc tạo tài khoản và người dùng sửa được,
     * nên nó có thể bị xóa trắng. Lùi về {@code username} thay vì để một bong
     * bóng chat không có tên.
     */
    private static String displayNameOf(User user) {
        String name = user.getDisplayName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }
}
