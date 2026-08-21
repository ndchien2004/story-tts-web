package com.storytts.backend.repository;

import com.storytts.backend.domain.SupportMessage;
import com.storytts.backend.domain.SupportSenderRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn tin nhắn hỗ trợ.
 *
 * <h3>Mọi câu ở đây bắt đầu bằng {@code conversation.id}, và đó không phải trùng hợp</h3>
 * Nó vừa là chiều đọc duy nhất của bảng — chỉ mục
 * {@code (conversation_id, id)} phủ hết — vừa là hàng rào phân quyền cuối cùng:
 * không có phương thức nào nhận riêng một {@code messageId}, nên không có đường
 * nào đọc được tin của luồng khác kể cả khi tầng trên quên kiểm.
 *
 * <h3>Phân trang bằng con trỏ {@code id}, không bằng {@code offset}</h3>
 * Một luồng hỗ trợ dài không có trần, và {@code OFFSET 5000} bắt cơ sở dữ liệu
 * đọc rồi bỏ đi năm nghìn hàng cho mỗi lần cuộn lên. Con trỏ thì luôn là một
 * lần dò chỉ mục rồi quét đúng số hàng cần, bất kể luồng dài bao nhiêu — và nó
 * còn đúng khi có tin mới chèn vào giữa lúc người ta đang cuộn, thứ mà
 * {@code offset} thì không.
 */
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /**
     * Cuộn lên: những tin <i>trước</i> con trỏ, mới nhất trước.
     *
     * <p>{@code before} null nghĩa là "trang cuối cùng" — đúng thứ trang chat
     * cần lúc mở. Bên gọi đảo lại thứ tự trước khi trả ra, vì giao diện dựng
     * theo chiều tăng dần.
     *
     * <p>{@code JOIN FETCH} người gửi vì DTO cần tên hiển thị của họ; không có
     * nó thì mỗi tin là một câu truy vấn thêm.
     *
     * <p>{@code LEFT} từ V16, và chữ ấy gánh nhiều hơn vẻ ngoài của nó: tin của
     * trợ lý không có người gửi, nên một phép nối trong sẽ <i>lặng lẽ bỏ hết
     * chúng ra khỏi lịch sử</i>. Không có lỗi nào được ném, không có dòng log
     * nào — chỉ là nửa cuộc trò chuyện biến mất sau khi tải lại trang.
     */
    @Query("""
            SELECT m FROM SupportMessage m
            LEFT JOIN FETCH m.sender
            WHERE m.conversation.id = :conversationId
              AND (:before IS NULL OR m.id < :before)
            ORDER BY m.id DESC
            """)
    List<SupportMessage> findPageBefore(@Param("conversationId") Long conversationId,
                                        @Param("before") Long before,
                                        Pageable limit);

    /**
     * Bắt kịp: những tin <i>sau</i> con trỏ, cũ nhất trước.
     *
     * <p>Đây là đường phục hồi sau khi mất kết nối. Nó có trần số dòng như mọi
     * đường khác — một trình duyệt vắng mặt ba ngày không được phép kéo cả luồng
     * về trong một lượt — và bên gọi biết mình còn thiếu nhờ so với
     * {@code lastMessageId} của luồng.
     */
    @Query("""
            SELECT m FROM SupportMessage m
            LEFT JOIN FETCH m.sender
            WHERE m.conversation.id = :conversationId
              AND m.id > :after
            ORDER BY m.id ASC
            """)
    List<SupportMessage> findPageAfter(@Param("conversationId") Long conversationId,
                                       @Param("after") Long after,
                                       Pageable limit);

    /**
     * Lần bấm gửi này đã được ghi chưa.
     *
     * <p>Ba điều kiện chứ không phải một, và cả ba đều cần thiết: cùng luồng,
     * cùng người gửi, cùng định danh của trình duyệt. Đây là bản sao ở tầng
     * truy vấn của ràng buộc {@code UNIQUE} trong lược đồ, và nó chạy <i>bên
     * trong</i> khóa hàng của luồng — nên hai lần thử lại song song không cùng
     * lọt qua.
     */
    @Query("""
            SELECT m FROM SupportMessage m
            LEFT JOIN FETCH m.sender
            WHERE m.conversation.id = :conversationId
              AND m.sender.id = :senderId
              AND m.clientMessageId = :clientMessageId
            """)
    Optional<SupportMessage> findByClientId(@Param("conversationId") Long conversationId,
                                            @Param("senderId") Long senderId,
                                            @Param("clientMessageId") String clientMessageId);

    /**
     * Câu trả lời của trợ lý cho một câu hỏi đã có chưa.
     *
     * <p>Đường chống trùng riêng của tin {@code AI}, và nó phải riêng vì
     * {@link #findByClientId} lọc theo {@code m.sender.id} — thứ mà tin của trợ
     * lý không có. Khóa ở đây là (luồng, vai, định danh), và định danh ấy được
     * suy ra từ id của chính câu hỏi ({@code "ai-<id>"}), nên nó chặt hơn chứ
     * không lỏng hơn: một câu hỏi sinh ra đúng một câu trả lời, dù người ta bấm
     * gửi lại bao nhiêu lần.
     *
     * <p>Không cần {@code LEFT JOIN FETCH m.sender}: hàng tìm được ở đây luôn
     * có {@code sender} null, và bên gọi không hỏi tới nó.
     */
    @Query("""
            SELECT m FROM SupportMessage m
            WHERE m.conversation.id = :conversationId
              AND m.senderRole = com.storytts.backend.domain.SupportSenderRole.AI
              AND m.clientMessageId = :clientMessageId
            """)
    Optional<SupportMessage> findAssistantMessage(@Param("conversationId") Long conversationId,
                                                  @Param("clientMessageId") String clientMessageId);

    /**
     * Số tin của một phía mà bên kia chưa đọc — "bao nhiêu tin của bên kia có
     * id lớn hơn mốc của tôi".
     *
     * <p>Một phép đếm dẫn xuất từ mốc, không phải một bộ đếm được cộng trừ —
     * xem {@code SupportConversation}. Tin {@code SYSTEM} bị loại: "đã đóng cuộc
     * trò chuyện" không phải một câu chờ ai trả lời.
     *
     * <p>{@code senderRoles} là một tập chứ không phải một giá trị, và V16 là
     * lý do: với người đọc, "bên kia" gồm cả quản trị viên lẫn trợ lý — cả hai
     * đều là câu trả lời gửi cho họ. Với quản trị viên thì "bên kia" vẫn chỉ là
     * {@code USER}: câu của trợ lý không phải việc người trực phải đọc, và tính
     * nó vào đây sẽ làm mỗi lượt trò chuyện với AI đẩy con số ấy lên hai.
     *
     * <p>Tập vai luôn do {@link SupportSenderRole#incomingFor()} dựng, không
     * bao giờ do bên gọi tự liệt kê — đó là chỗ duy nhất giữ phép ánh xạ bất
     * đối xứng ấy.
     *
     * <p>Chỉ mục {@code idx_support_messages_unread (conversation_id,
     * sender_role, id)} vẫn phục vụ được câu này: một tập hai phần tử thành hai
     * lần dò chỉ mục chứ không thành một lần quét bảng.
     */
    @Query("""
            SELECT count(m) FROM SupportMessage m
            WHERE m.conversation.id = :conversationId
              AND m.senderRole IN :senderRoles
              AND m.messageType = com.storytts.backend.domain.SupportMessageType.TEXT
              AND m.id > :afterId
            """)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("senderRoles") Collection<SupportSenderRole> senderRoles,
                     @Param("afterId") Long afterId);

    /**
     * Tin này có thuộc luồng này không.
     *
     * <p>Dùng khi một bên báo "tôi đã đọc tới tin số N": không có phép kiểm này
     * thì một id bất kỳ — kể cả id của tin trong luồng người khác — cũng đẩy
     * được mốc đã đọc lên, và số chưa đọc của chính người gửi lệnh sẽ sai theo
     * một cách không tự sửa được.
     */
    boolean existsByIdAndConversationId(Long id, Long conversationId);
}
