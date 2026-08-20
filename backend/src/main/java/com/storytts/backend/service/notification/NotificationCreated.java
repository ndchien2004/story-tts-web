package com.storytts.backend.service.notification;

import com.storytts.backend.dto.notification.NotificationDto;

/**
 * Một thông báo vừa được ghi vào hộp thư của ai đó.
 *
 * <h3>Phát trong giao dịch, nhận sau khi commit</h3>
 * Cùng quy tắc với {@code ChapterContentUpdated}, và ở đây nó là điều kiện của
 * cả tính năng: sự kiện được phát bên trong giao dịch nghiệp vụ, còn
 * {@code UserEventStream} đăng ký ở {@code AFTER_COMMIT}. Nghĩa là một lần hoàn
 * Xu cuộn ngược sẽ không kịp nói với ai rằng tiền đã về — đúng cái kiểu sai mà
 * cả thiết kế này sinh ra để ngăn.
 *
 * <h3>Vì sao mang theo cả DTO chứ không chỉ một id</h3>
 * Bên nhận chạy sau {@code COMMIT}, tức là ngoài giao dịch và có thể ngoài
 * {@code EntityManager}. Đưa một id thì nó phải tự đi tra lại — một câu truy vấn
 * cho một hàng vừa mới nằm trong tay bên phát. Đưa sẵn bản đã dựng thì đường gửi
 * không chạm vào cơ sở dữ liệu lần nào.
 *
 * <p>{@code unread} đi kèm vì cái chuông cần cả hai thứ cùng lúc: một dòng mới,
 * và con số mới. Tính ở đây — trong giao dịch, ngay sau khi ghi — thì nó là con
 * số của máy chủ; để trình duyệt tự cộng thêm một là mời nó lệch dần sau mỗi
 * khung tin bị bỏ lỡ.
 *
 * @param userId       người nhận
 * @param notification bản đã dựng sẵn, đúng hình dạng REST trả về
 * @param unread       số chưa đọc của người ấy sau khi ghi
 */
public record NotificationCreated(Long userId, NotificationDto notification, long unread) {
}
