package com.storytts.backend.service;

/**
 * Quyền của một tài khoản vừa đổi theo hướng cần cắt mọi thứ đang mở.
 *
 * <h3>Vì sao một sự kiện chứ không phải một lời gọi thẳng</h3>
 * Vì bên phát và bên nhận không nên biết nhau. {@code UserAdminService} lo việc
 * quản lý thành viên; nó không có lý do gì phải biết rằng trang này có một sổ
 * kết nối WebSocket, và càng không nên phải sửa lại mỗi lần có thêm một đường
 * thời gian thực thứ ba.
 *
 * <p>Ngược lại, tầng thời gian thực <i>phải</i> biết: không có nó thì một kết
 * nối đã mở là một lỗ hổng có thật. Quản trị viên khóa tài khoản, mọi request
 * HTTP của người ấy bị {@code JwtAuthenticationFilter} chặn ngay — nhưng cái
 * socket vẫn nằm đó và vẫn nhận được tin nhắn mới.
 *
 * <h3>Bên nhận đăng ký ở {@code AFTER_COMMIT}</h3>
 * Điều kiện, không phải chi tiết: đá người ta ra trước khi commit nghĩa là một
 * giao dịch cuộn ngược vẫn kịp cắt kết nối vì một lệnh khóa chưa từng xảy ra.
 * Cùng mốc mà {@code UserEventStream} dùng, và vì cùng lý do.
 *
 * <h3>Hai lý do, và vì sao cả hai đều phải cắt</h3>
 * <pre>
 *   LOCKED       → tài khoản bị khóa: không được nhận gì nữa, và không được nối lại
 *   ROLE_CHANGED → quyền vừa đổi: kết nối cũ mang một vai trò đã cũ
 * </pre>
 *
 * Vế thứ hai ít hiển nhiên hơn nên đáng nói rõ. Một kết nối ghi nhớ vai trò của
 * nó lúc bắt tay, và vai trò ấy quyết định <i>nhận được khung tin nào</i>. Một
 * quản trị viên vừa bị hạ quyền vẫn không <b>làm</b> được gì — mọi lệnh đều đọc
 * lại quyền từ cơ sở dữ liệu — nhưng kết nối của họ vẫn nằm trong nhóm "phía hỗ
 * trợ" và vẫn nhận được tin nhắn của mọi luồng. Cắt là cách duy nhất đóng đường
 * ấy ngay; nối lại sẽ cho họ một vai trò đúng.
 *
 * @param userId tài khoản bị ảnh hưởng
 * @param reason vì sao — đi vào nhật ký, và không đi ra ngoài trình duyệt: mã
 *               đóng kết nối nói vừa đủ cho việc trình duyệt phải làm, không nói
 *               thêm về quyết định quản trị đứng sau
 */
public record AccountAccessRevoked(Long userId, Reason reason) {

    public enum Reason {

        /** Quản trị viên khóa tài khoản. Trình duyệt phải đăng xuất, không nối lại. */
        LOCKED,

        /** Vai trò đổi. Trình duyệt nối lại và nhận đúng vai trò mới. */
        ROLE_CHANGED
    }

    public static AccountAccessRevoked locked(Long userId) {
        return new AccountAccessRevoked(userId, Reason.LOCKED);
    }

    public static AccountAccessRevoked roleChanged(Long userId) {
        return new AccountAccessRevoked(userId, Reason.ROLE_CHANGED);
    }
}
