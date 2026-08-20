package com.storytts.backend.domain;

/**
 * Mức độ đáng dừng lại của một thông báo.
 *
 * <h3>Vì sao tách khỏi {@link NotificationType}</h3>
 * Loại nói <i>chuyện gì</i>, mức nói <i>có đáng ngắt lời không</i>. Phần lớn
 * thời gian hai thứ đi cùng nhau, nhưng không phải luôn: một tin chung của
 * quản trị viên có thể là "bảo trì lúc 2 giờ sáng" (đọc lúc nào cũng được) hoặc
 * "tài khoản của bạn sắp bị khóa" (không được bỏ lỡ), và cả hai đều là
 * {@code ANNOUNCEMENT}. Gộp hai câu hỏi vào một cột nghĩa là phải đẻ thêm loại
 * chỉ để phân biệt sắc thái.
 *
 * <p>Giao diện dùng nó cho đúng một việc: quyết định cái chấm bên trái đậm tới
 * đâu. Không có mức nào bật hộp thoại, và đó là chủ ý — thông báo là thứ người
 * đọc mở ra xem, không phải thứ chặn đường họ. Việc chặn đường (chương đang đọc
 * vừa bị gỡ) do trang đọc lo, bằng một cơ chế riêng đã có sẵn.
 */
public enum NotificationPriority {

    /** Biết cũng được, không biết cũng không mất gì. */
    INFO,

    /** Một việc người đọc mong đợi đã xong. */
    SUCCESS,

    /** Có thứ cần để mắt tới, nhưng chưa mất gì. */
    WARNING,

    /** Đụng tới tiền hoặc tới quyền truy cập — không nên bỏ lỡ. */
    IMPORTANT
}
