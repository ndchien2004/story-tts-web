package com.storytts.backend.service.storage;

/**
 * Khóa lưu trữ không còn ứng với dữ liệu nào.
 *
 * <p>Tách riêng khỏi các lỗi vào/ra khác vì nó có nghĩa nghiệp vụ hẳn hoi: cơ sở
 * dữ liệu và nơi lưu file đã lệch nhau. Bên gọi bắt đúng ngoại lệ này để đánh
 * dấu bản ghi là hỏng, nhờ đó chương ấy dựng lại được — xem
 * {@code AudioAssetRepair}. Một {@code IOException} chung chung thì không phân
 * biệt được chuyện đó với "mạng đang chập chờn", và đánh dấu hỏng vì mạng chập
 * chờn là làm mất một bản audio còn nguyên vẹn.
 */
public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(String message) {
        super(message);
    }

    public MediaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
