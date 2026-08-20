package com.storytts.backend.domain;

/**
 * Bên nào đã gửi một tin nhắn, chốt lại tại thời điểm gửi.
 *
 * <h3>Vì sao lưu chứ không suy ra từ {@code users.role}</h3>
 * Quyền của một tài khoản đổi được; lịch sử thì không. Một quản trị viên bị hạ
 * quyền về sau không được phép làm những câu họ đã trả lời biến thành câu của
 * người đọc — nếu điều đó xảy ra thì cả luồng hội thoại đọc ra sai, và phép đếm
 * chưa đọc (vốn lọc theo chính cột này) cũng sai theo.
 *
 * <h3>Vì sao đây không phải {@link Role}</h3>
 * {@link Role} nói tài khoản là ai <i>bây giờ</i>; cột này nói ai đã nói câu
 * này <i>lúc đó</i>. Hai câu hỏi khác nhau, và câu thứ hai phải đứng yên khi câu
 * thứ nhất đổi.
 *
 * <h3>Không có giá trị {@code SYSTEM} ở đây</h3>
 * Lời máy chủ tự nói ("đã đóng cuộc trò chuyện") là một
 * {@link SupportMessageType#SYSTEM}, và nó vẫn mang {@code sender_role = ADMIN}
 * của chính người đã bấm nút — nên không có hàng nào không truy được về một
 * người thật.
 *
 * <p>Đặt {@code SYSTEM} vào cả hai enum sẽ là hai cột luôn phải bằng nhau, tức
 * là hai cột có thể lệch nhau. Cùng lập luận đã dùng ở {@code notifications} khi
 * từ chối cột {@code is_read}, và ở {@code gift_codes} khi từ chối cột
 * {@code status}. Ở đây hai cột trả lời hai câu khác nhau: <i>ai</i> nói, và
 * nói <i>kiểu gì</i>.
 *
 * <h3>Giá trị này không bao giờ đến từ trình duyệt</h3>
 * Máy chủ tự điền từ người đã xác thực. Một khung tin gửi lên kèm
 * {@code senderRole: "ADMIN"} bị bỏ qua hoàn toàn — trường ấy thậm chí không có
 * mặt trong kiểu dữ liệu nhận vào. Xem {@code SupportSendCommand}.
 */
public enum SupportSenderRole {

    /** Chủ luồng — người đọc. */
    USER,

    /** Phía hỗ trợ. Nhiều quản trị viên khác nhau đều mang giá trị này. */
    ADMIN;

    /** Bên còn lại — dùng để đếm "bao nhiêu tin của người kia tôi chưa đọc". */
    public SupportSenderRole other() {
        return this == USER ? ADMIN : USER;
    }
}
