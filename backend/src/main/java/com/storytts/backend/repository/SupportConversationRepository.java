package com.storytts.backend.repository;

import com.storytts.backend.domain.SupportConversation;
import com.storytts.backend.domain.SupportConversationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Truy vấn luồng hỗ trợ. */
public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {

    /**
     * Luồng của một người đọc.
     *
     * <p>{@code UNIQUE (user_id)} nên câu này trả về nhiều nhất một hàng, và đó
     * là điều kiện để "lấy hoặc tạo" không cần đoán xem lấy cái nào.
     */
    @Query("""
            SELECT c FROM SupportConversation c
            JOIN FETCH c.user
            WHERE c.user.id = :userId
            """)
    Optional<SupportConversation> findByUserId(@Param("userId") Long userId);

    /**
     * Khóa hàng cho tới hết giao dịch, rồi trả về nó.
     *
     * <h3>Vì sao khóa bi quan chứ không phải cột phiên bản</h3>
     * Đường gửi một tin nhắn vừa phải <i>đọc</i> {@code status} (được gửi không,
     * có phải mở lại không) vừa phải <i>ghi</i> bốn cột {@code lastMessage*}.
     * Hai việc ấy phải nhìn thấy nhau, nếu không thì hai lượt gửi song song sẽ
     * có một lượt ghi đè bộ nhớ đệm tin cuối của lượt kia, và hộp thư quản trị
     * hiện một câu không phải câu cuối.
     *
     * <p>Khóa ở đây cũng chính là thứ khiến cuộc đua đóng/gửi có kết cục xác
     * định, và khiến phép kiểm trùng {@code clientMessageId} bên trong đáng tin:
     * hai lượt gửi cùng một luồng xếp hàng, nên lượt sau nhìn thấy hàng lượt
     * trước vừa ghi.
     *
     * <p>Khóa được giữ trong một giao dịch chỉ gồm ba câu lệnh SQL — không có
     * lời gọi mạng nào, không có lượt gửi WebSocket nào, không có gì chờ trình
     * duyệt. Xem {@code SupportMessageStore} về ranh giới ấy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SupportConversation c WHERE c.id = :id")
    Optional<SupportConversation> lockById(@Param("id") Long id);

    /**
     * Một trang hộp thư của quản trị viên, hoạt động mới nhất trước.
     *
     * <p>{@code JOIN FETCH} người đọc vì mỗi dòng hiện tên và ảnh của họ; không
     * có nó thì một trang ba mươi dòng là ba mươi câu truy vấn thêm.
     *
     * <p>{@code status} null nghĩa là "tất cả" — một câu truy vấn cho cả bốn tab
     * thay vì bốn câu gần giống nhau. {@code keyword} null nghĩa là không lọc.
     *
     * <p>Thứ tự phụ theo {@code c.id DESC} để hai luồng có cùng
     * {@code lastMessageAt} — hoặc cùng chưa có tin nào, tức là cùng null —
     * không đổi chỗ cho nhau giữa hai lần lật trang.
     */
    @Query(value = """
            SELECT c FROM SupportConversation c
            JOIN FETCH c.user u
            WHERE (:status IS NULL OR c.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.lastMessageAt DESC, c.id DESC
            """,
            countQuery = """
                    SELECT count(c) FROM SupportConversation c
                    JOIN c.user u
                    WHERE (:status IS NULL OR c.status = :status)
                      AND (:keyword IS NULL
                           OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """)
    Page<SupportConversation> search(@Param("status") SupportConversationStatus status,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    /**
     * Số tin chưa đọc của phía hỗ trợ, cho cả một trang hộp thư trong <b>một</b> câu.
     *
     * <h3>Vì sao không hỏi từng dòng một</h3>
     * Vì mốc đã đọc nằm ở luồng còn tin nhắn nằm ở bảng khác, cách hiển nhiên là
     * gọi một hàm đếm cho mỗi dòng — tức là N+1, đúng thứ mà đặc tả cấm và đúng
     * thứ mà một hộp thư ba mươi dòng sẽ biến thành ba mươi lượt đi cơ sở dữ
     * liệu.
     *
     * <p>Phép nối có điều kiện dưới đây gộp chúng lại: điều kiện
     * {@code m.id > c.adminLastReadMessageId} nằm trong {@code ON}, nên mỗi
     * luồng tự mang mốc của nó vào phép đếm. Luồng không có tin nào chưa đọc vẫn
     * ra một dòng số 0 nhờ {@code LEFT JOIN}.
     */
    /**
     * Số chưa đọc của phía hỗ trợ, cho cả một trang hộp thư trong một câu.
     *
     * <p>Điều kiện {@code assistantMode <> AI} nằm trong mệnh đề {@code ON} chứ
     * không phải {@code WHERE}, và chỗ đặt ấy có chủ đích: ở {@code WHERE} thì
     * những luồng đang do trợ lý phụ trách sẽ <i>biến mất khỏi kết quả</i>, và
     * bên gọi — vốn dựng một {@code Map} từ danh sách này — sẽ không phân biệt
     * được "không chưa đọc gì" với "không có dòng nào". Ở {@code ON} thì chúng
     * ở lại với số không, đúng thứ cần.
     *
     * <p>Vì sao phải loại chúng: một con số đỏ cạnh một cuộc trò chuyện với AI
     * đọc ra là "bạn đang nợ người này một câu trả lời", mà người trực thì
     * không nợ gì cả. Xem {@code SupportAssistantMode#needsHumanAttention}.
     */
    @Query("""
            SELECT new com.storytts.backend.repository.SupportConversationRepository$UnreadRow(
                       c.id, count(m.id))
            FROM SupportConversation c
            LEFT JOIN SupportMessage m
                   ON m.conversation.id = c.id
                  AND m.id > c.adminLastReadMessageId
                  AND m.senderRole = com.storytts.backend.domain.SupportSenderRole.USER
                  AND m.messageType = com.storytts.backend.domain.SupportMessageType.TEXT
                  AND c.assistantMode <> com.storytts.backend.domain.SupportAssistantMode.AI
            WHERE c.id IN :ids
            GROUP BY c.id
            """)
    List<UnreadRow> countAdminUnread(@Param("ids") Collection<Long> ids);

    /**
     * Bao nhiêu luồng đang có tin người đọc chưa được trả lời.
     *
     * <p>Đây là con số trên tab "Hỗ trợ" của khu quản trị, nên nó được hỏi ở mỗi
     * lần mở trang. Đếm <i>luồng</i> chứ không đếm <i>tin</i>: người hỗ trợ cần
     * biết còn bao nhiêu việc phải làm, không cần biết mỗi việc dài mấy câu.
     */
    /**
     * Con số sau cái huy hiệu đỏ trên thẻ "Hỗ trợ" của khu quản trị.
     *
     * <h4>Hai nhánh, vì "đang chờ" có hai nghĩa khác nhau</h4>
     * <pre>
     *   mode = HANDOFF                     → đang chờ, luôn luôn
     *   mode = HUMAN và có tin chưa đọc    → đang chờ (quy tắc cũ của V15)
     *   mode = AI                          → không bao giờ
     * </pre>
     *
     * <p>Nhánh thứ nhất là phần V16 thêm vào, và nó vá một chỗ mà quy tắc cũ
     * bỏ sót: người bấm thẳng "Chat với tư vấn viên" rồi ngồi chờ chưa gõ câu
     * nào không có tin chưa đọc nào cả, nhưng rõ ràng đang có người chờ. Đếm
     * bằng tin nhắn thì họ vô hình.
     *
     * <p>Nó cũng sửa một chỗ hơi lệch vốn có: theo quy tắc cũ, quản trị viên
     * chỉ cần <i>mở ra đọc</i> là huy hiệu tắt, dù chưa trả lời câu nào. Một
     * luồng {@code HANDOFF} thì không tắt theo cách ấy — nó rời khỏi phép đếm
     * khi có người thật sự nhận, tức là khi mode chuyển sang {@code HUMAN}, và
     * điều đó chỉ xảy ra khi một quản trị viên gõ một câu hoặc đổi trạng thái
     * luồng. Xem {@code SupportConversation#takenOverByHuman}.
     *
     * <p>Nhánh thứ ba là cả lý do tính năng này tồn tại: một người hỏi trợ lý
     * "làm sao mở khóa chương" không được phép làm sáng đèn của ai.
     */
    @Query("""
            SELECT count(c) FROM SupportConversation c
            WHERE c.assistantMode = com.storytts.backend.domain.SupportAssistantMode.HANDOFF
               OR (c.assistantMode = com.storytts.backend.domain.SupportAssistantMode.HUMAN
                   AND EXISTS (SELECT 1 FROM SupportMessage m
                               WHERE m.conversation.id = c.id
                                 AND m.id > c.adminLastReadMessageId
                                 AND m.senderRole = com.storytts.backend.domain.SupportSenderRole.USER
                                 AND m.messageType = com.storytts.backend.domain.SupportMessageType.TEXT))
            """)
    long countConversationsAwaitingReply();

    /** Một dòng của {@link #countAdminUnread}. */
    record UnreadRow(Long conversationId, long unread) {
    }
}
