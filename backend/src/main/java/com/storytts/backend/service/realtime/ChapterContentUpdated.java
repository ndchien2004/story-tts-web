package com.storytts.backend.service.realtime;

/**
 * Nội dung một chương vừa đổi, và phiên bản mới của nó là bao nhiêu.
 *
 * <p>Chỉ phát khi <b>nội dung</b> đổi. Sửa tiêu đề, đổi mức khóa hay đặt giá Xu
 * không sinh ra sự kiện nào ở đây: người đang đọc không cần biết, và mỗi lần
 * phát nhầm là một lần trang đọc mời họ tải lại một thứ không hề đổi.
 *
 * <p>Sự kiện được phát bên trong giao dịch sửa chương, nhưng người nhận đăng ký
 * ở {@code AFTER_COMMIT} — nên một lần lưu hỏng rồi cuộn ngược sẽ không gửi đi
 * lời báo nào. Báo "chương đã cập nhật" cho một lần cập nhật chưa từng xảy ra là
 * kiểu sai tệ nhất ở đây: người đọc bỏ dở đoạn đang nghe để tải lại đúng nội
 * dung họ đang có.
 *
 * <p>Cố ý <b>không</b> mang theo nội dung chương. Sự kiện này đi tới mọi trình
 * duyệt đang mở chương, kể cả những trình duyệt chưa trả Xu để mở nó; ba trường
 * số ở đây không nói gì mà danh sách chương chưa nói. Muốn đọc chữ thì vẫn phải
 * đi qua đường cũ và qua đúng cửa kiểm quyền cũ.
 *
 * @param chapterId      chương vừa đổi
 * @param storyId        truyện chứa nó, để trình duyệt lọc nhanh mà không phải tra
 * @param contentVersion phiên bản mới, luôn lớn hơn phiên bản trình duyệt đang giữ
 */
public record ChapterContentUpdated(Long chapterId, Long storyId, int contentVersion) {
}
