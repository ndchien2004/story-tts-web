package com.storytts.backend.repository;

import com.storytts.backend.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Truy vấn hộp thư của một người.
 *
 * <p>Mọi câu ở đây đều bắt đầu bằng {@code user.id}, và đó không phải trùng
 * hợp: cả bảng chỉ được đọc theo một chiều — của ai — nên hai chỉ mục
 * {@code (user_id, created_at)} và {@code (user_id, read_at)} phủ hết đường
 * đọc, và không có câu nào phải quét bảng.
 *
 * <p>Điều kiện {@code user.id} cũng chính là hàng rào phân quyền: không có
 * phương thức nào ở đây nhận riêng một {@code notificationId}, nên không có
 * đường nào đọc hay sửa thông báo của người khác kể cả khi tầng trên quên kiểm.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Một trang hộp thư, mới nhất trước.
     *
     * <p>{@code JOIN FETCH} người nhận vì {@code @ManyToOne} là LAZY và DTO sẽ
     * chạm tới nó; không có nó thì mỗi dòng sinh thêm một truy vấn.
     */
    @Query(value = """
            SELECT n FROM Notification n
            JOIN FETCH n.user
            WHERE n.user.id = :userId
            ORDER BY n.createdAt DESC, n.id DESC
            """,
            countQuery = "SELECT count(n) FROM Notification n WHERE n.user.id = :userId")
    Page<Notification> findInbox(@Param("userId") Long userId, Pageable pageable);

    /**
     * Số thông báo chưa đọc.
     *
     * <p>Một câu đếm trên chỉ mục {@code (user_id, read_at)}, không nạp hàng nào
     * lên bộ nhớ. Con số này được hỏi ở mỗi lần mở trang và mỗi lần nối lại
     * luồng, nên nó phải rẻ.
     */
    @Query("SELECT count(n) FROM Notification n WHERE n.user.id = :userId AND n.readAt IS NULL")
    long countUnread(@Param("userId") Long userId);

    /**
     * Tra một thông báo <b>của chính người này</b>.
     *
     * <p>Hai điều kiện chứ không phải một, và đó là điểm chính: id thông báo là
     * số tự tăng nên đoán được. Tra theo id rồi mới so chủ sở hữu ở tầng service
     * cũng ra cùng kết quả, nhưng nó để ngỏ khả năng một đường mới quên mất phép
     * so ấy.
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Sự kiện này đã sinh ra thông báo cho người này chưa. Xem {@code Notification.eventId}. */
    boolean existsByUserIdAndEventId(Long userId, String eventId);

    /**
     * Đánh dấu đã đọc toàn bộ hộp thư, bằng một câu UPDATE.
     *
     * <p>Không nạp từng hàng lên rồi sửa: một người đọc lâu năm có hàng nghìn
     * thông báo, và "đánh dấu tất cả" không có lý do gì để đụng tới bộ nhớ theo
     * số ấy. Điều kiện {@code readAt IS NULL} giữ cho mốc thời gian của những
     * thông báo đã đọc từ trước không bị viết đè.
     *
     * @return số hàng thật sự đổi trạng thái
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.readAt = :when
            WHERE n.user.id = :userId AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("when") Instant when);
}
