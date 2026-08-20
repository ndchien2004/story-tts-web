package com.storytts.backend.dto.gift;

/**
 * Kết quả một lượt đổi gift code thành công.
 *
 * <p>Chỉ có ba con số, và cả ba đều do máy chủ quyết định. {@code coinAmount}
 * lấy từ mã trong cơ sở dữ liệu, không phải từ bất cứ thứ gì client gửi lên —
 * xem {@code GiftCodeController}.
 *
 * <p>{@code balance} có mặt để giao diện khỏi phải gọi thêm một vòng nữa chỉ để
 * hỏi số dư mới. Nó là số dư <i>sau</i> khi cộng, đọc bên trong cùng giao dịch,
 * nên nó không thể là một con số cũ.
 *
 * <p>Thất bại không đi qua đây: nó là một {@code GiftCodeException} và trở thành
 * một câu trả lời lỗi có mã riêng. Một trường {@code success} ở đây sẽ mời gọi
 * việc trả về HTTP 200 kèm {@code success: false}, và từ đó mọi bên gọi phải nhớ
 * kiểm hai chỗ thay vì một.
 */
public record RedeemResultDto(
        String code,
        long coinAmount,
        long balance
) {
}
