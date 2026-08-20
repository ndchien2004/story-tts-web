package com.storytts.backend.service.support;

import org.springframework.web.socket.CloseStatus;

/**
 * Vì sao máy chủ đóng một kết nối, nói bằng một con số trình duyệt đọc được.
 *
 * <h3>Vì sao cần mã riêng thay vì đóng suông</h3>
 * Trình duyệt phải phân biệt được ba chuyện dẫn tới ba hành vi ngược nhau:
 *
 * <pre>
 *   hết hạn phiên kết nối → xin vé mới, nối lại ngay
 *   tài khoản bị khóa      → đăng xuất, KHÔNG nối lại
 *   quá nhiều kết nối      → nối lại chậm hơn, hoặc thôi
 * </pre>
 *
 * Không có mã thì cả ba đều là {@code onclose}, và cách xử lý duy nhất còn lại
 * là nối lại — tức là một tài khoản vừa bị khóa sẽ nối lại mãi mãi, mỗi lần đều
 * bị từ chối, tạo đúng cái vòng lặp mà {@code ApiError} với mã
 * {@code ACCOUNT_LOCKED} sinh ra để tránh ở phía HTTP.
 *
 * <h3>Vì sao dải 4000–4999</h3>
 * Chuẩn WebSocket dành hẳn dải ấy cho ứng dụng: 1000–2999 thuộc về giao thức và
 * về trình duyệt, 3000–3999 thuộc về các thư viện đã đăng ký. Dùng một mã ngoài
 * dải 4xxx là mượn một con số mà một tầng bên dưới có thể phát ra vì lý do khác
 * hẳn.
 */
final class SupportCloseCodes {

    /**
     * Vé sai, đã dùng, hoặc đã hết hạn.
     *
     * <p>Trên thực tế trình duyệt hiếm khi thấy mã này: bắt tay hỏng thì kết nối
     * không bao giờ mở, và trình duyệt nhận một lỗi HTTP. Nó ở đây cho nhánh
     * còn lại — bắt tay xong nhưng tài khoản không nạp được nữa.
     */
    static final CloseStatus UNAUTHORIZED = new CloseStatus(4001, "UNAUTHORIZED");

    /**
     * Tài khoản bị khóa, hoặc quyền vừa đổi.
     *
     * <p>Đây là mã của lệnh "đá ra ngay" — xem {@code SupportSocketRegistry.revoke}.
     * Trình duyệt <b>không</b> được nối lại khi thấy nó.
     */
    static final CloseStatus ACCESS_REVOKED = new CloseStatus(4002, "ACCESS_REVOKED");

    /** Đã chạm trần số kết nối của tài khoản này hoặc của cả máy chủ. */
    static final CloseStatus TOO_MANY_CONNECTIONS = new CloseStatus(4003, "TOO_MANY_CONNECTIONS");

    /**
     * Kết nối đã sống hết hạn cho phép, hoặc đã im lặng quá lâu.
     *
     * <p>Lời mời nối lại, không phải lời từ chối: trình duyệt xin một vé mới —
     * việc đòi một phiên đăng nhập còn hiệu lực — rồi mở kết nối khác.
     */
    static final CloseStatus RECONNECT = new CloseStatus(4004, "RECONNECT");

    /**
     * Đầu bên kia gửi rác liên tục.
     *
     * <p>Khác {@code SUPPORT_RATE_LIMITED}: mã kia là một câu trả lời cho một
     * lệnh hợp lệ gửi quá nhanh, và kết nối vẫn sống. Mã này dành cho khung tin
     * không phân tích được hoặc không có kiểu — thứ mà một máy khách viết đúng
     * không bao giờ gửi, nên không có lý do gì để giữ kết nối lại.
     */
    static final CloseStatus PROTOCOL_ABUSE = new CloseStatus(4005, "PROTOCOL_ABUSE");

    private SupportCloseCodes() {
    }
}
