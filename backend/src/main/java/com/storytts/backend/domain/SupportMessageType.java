package com.storytts.backend.domain;

/**
 * Kiểu của một tin nhắn: người viết, hay máy chủ tường thuật.
 *
 * <h3>Hai giá trị, và vì sao chúng khác nhau ở nhiều chỗ hơn là giao diện</h3>
 * <pre>
 *   TEXT   → do người gõ  → tính vào số chưa đọc → qua lớp kiểm tần suất
 *   SYSTEM → do máy chủ   → không tính            → không qua
 * </pre>
 *
 * Phép đếm chưa đọc cố ý bỏ qua {@code SYSTEM}: "quản trị viên đã đóng cuộc trò
 * chuyện" không phải một câu chờ ai đó trả lời, và để nó bật một con số đỏ trên
 * cái chuông là dạy người dùng rằng con số ấy không có nghĩa gì.
 *
 * <h3>Không có {@code IMAGE}, {@code FILE}, {@code ATTACHMENT}</h3>
 * Tệp đính kèm không nằm trong phạm vi tính năng. Thêm một giá trị ở đây cho
 * một thứ chưa có đường tải lên, chưa có đường phát lại và chưa có hạn dung
 * lượng là ghi một lời hứa vào lược đồ mà không có mã nào giữ.
 */
public enum SupportMessageType {

    /** Một câu do người dùng hoặc quản trị viên gõ. Văn bản thuần. */
    TEXT,

    /** Máy chủ tường thuật một lần đổi trạng thái. Nội dung do máy chủ sinh. */
    SYSTEM
}
