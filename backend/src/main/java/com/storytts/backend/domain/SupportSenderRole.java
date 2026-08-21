package com.storytts.backend.domain;

import java.util.EnumSet;
import java.util.Set;

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
    ADMIN,

    /**
     * Trợ lý AI. Thêm ở V16.
     *
     * <p>Đây là bên gửi duy nhất không có hàng trong {@code users}, nên
     * {@code sender_id} của những tin này là {@code NULL}. Xem ghi chú dài ở
     * V16 về vì sao {@code NULL} là câu trả lời đúng thay vì một tài khoản ma,
     * và vì sao nó không làm hỏng ràng buộc chống trùng mà V15 dựng lên.
     *
     * <p>Giá trị này không bao giờ đến từ trình duyệt, y như hai giá trị trên:
     * chỉ {@code SupportAssistant} sinh ra nó, và chỉ sau khi đã kiểm — dưới
     * khóa hàng — rằng luồng vẫn đang ở {@link SupportAssistantMode#AI}.
     */
    AI;

    /**
     * Những bên mà một người xem coi là "tin của người khác gửi cho tôi".
     *
     * <p>Thay cho cách dùng {@link #other()} để đếm chưa đọc, vốn giả định
     * đúng hai bên. Với ba giá trị thì phép ánh xạ ấy không còn là một phép
     * lật, và nó cũng không đối xứng:
     *
     * <pre>
     *   người đọc  ← ADMIN và AI   (cả hai đều là câu trả lời gửi cho họ)
     *   quản trị   ← USER          (câu của trợ lý không phải việc phải đọc)
     * </pre>
     *
     * Vế thứ hai là chỗ quan trọng. Nếu tin của trợ lý tính vào số chưa đọc
     * của quản trị viên thì mỗi lượt trò chuyện với AI sẽ đẩy con số ấy lên
     * hai, và huy hiệu đỏ — thứ lẽ ra nghĩa là "có người đang chờ bạn" — sẽ
     * đếm luôn những cuộc mà không ai chờ ai cả.
     */
    public Set<SupportSenderRole> incomingFor() {
        return this == USER ? EnumSet.of(ADMIN, AI) : EnumSet.of(USER);
    }

    /**
     * Phía bên kia của luồng, theo nghĩa "ai là người còn lại đang đọc".
     *
     * <p>Chỉ có nghĩa với hai bên biết đọc, nên nó ném khi được hỏi về
     * {@link #AI}: trợ lý không có mốc đã đọc, không có số chưa đọc, và không
     * bao giờ là người xem một luồng. Ném thay vì trả về một giá trị nghe cho
     * hợp lý, vì một lời gọi như thế là lỗi lập trình chứ không phải một cảnh
     * nghiệp vụ.
     */
    public SupportSenderRole other() {
        return switch (this) {
            case USER -> ADMIN;
            case ADMIN -> USER;
            case AI -> throw new IllegalStateException(
                    "Trợ lý AI không phải một phía đọc luồng hỗ trợ.");
        };
    }

    /** Bên này có mốc đã đọc và số chưa đọc không. */
    public boolean isViewer() {
        return this != AI;
    }
}
