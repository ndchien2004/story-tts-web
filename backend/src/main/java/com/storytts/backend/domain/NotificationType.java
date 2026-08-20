package com.storytts.backend.domain;

/**
 * Chuyện gì đã xảy ra — phần quyết định biểu tượng và câu chữ ở thanh thông báo.
 *
 * <h3>Vì sao là enum chứ không phải một bảng loại thông báo</h3>
 * Mỗi giá trị ở đây tương ứng với một đoạn mã <i>đã có</i> ở backend phát ra
 * nó: không ai tạo được một loại mới bằng cách thêm một dòng vào cơ sở dữ liệu,
 * vì loại mới cần một chỗ trong nghiệp vụ để sinh ra. Một bảng cấu hình chỉ
 * thêm một nguồn sự thật thứ hai cho danh sách này, và một cách để hai bên lệch
 * nhau.
 *
 * <p>Lưu xuống dạng chuỗi ({@code @Enumerated(EnumType.STRING)}) như mọi enum
 * khác của lược đồ này, nên thêm một giá trị mới không đụng tới hàng cũ và
 * không phụ thuộc vào thứ tự khai báo.
 *
 * <h3>Mở rộng về sau</h3>
 * Thêm một loại là thêm một hằng ở đây cộng một dòng ở bảng tra biểu tượng phía
 * trình duyệt. Không có cột nào phải thêm, không có migration nào phải chạy:
 * những thứ khác nhau giữa các loại đã nằm ở {@code metadata},
 * {@code relatedEntityType} và {@code actionType} chứ không ở lược đồ.
 * {@code NEW_CHAPTER}, {@code COMMENT_REPLY}, {@code ACCOUNT_SECURITY} về sau
 * đi đúng đường ấy.
 */
public enum NotificationType {

    /** Quản trị viên cấp VIP, hoặc một đơn nâng cấp vừa thanh toán xong. */
    VIP_GRANTED,

    /** Chương đã mua bị gỡ khỏi trang; đi kèm số Xu đã hoàn. */
    CHAPTER_DELETED,

    /** Nội dung một chương đã mua vừa được viết lại. */
    CHAPTER_UPDATED,

    /** Một đơn thanh toán đã hoàn tất và hàng đã về tài khoản. */
    PAYMENT,

    /** Xu quay lại ví vì một lý do không phải mua bán. */
    REFUND,

    /** Máy chủ nói về chính tài khoản hoặc chính hệ thống. */
    SYSTEM,

    /** Quản trị viên loan một tin chung cho người đọc. */
    ANNOUNCEMENT
}
