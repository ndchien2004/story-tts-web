package com.storytts.backend.domain;

/**
 * Tình trạng của một gift code — suy ra, không lưu.
 *
 * <h3>Vì sao không có cột {@code status} trong bảng</h3>
 * Bốn trong năm giá trị dưới đây là hệ quả của những cột đã có: {@code enabled},
 * {@code start_at}, {@code end_at}, {@code max_uses}, {@code used_count}. Lưu
 * thêm một cột trạng thái nghĩa là có hai nguồn sự thật cho cùng một câu hỏi, và
 * chúng sẽ lệch nhau — không phải vì ai đó cẩu thả, mà vì hai trong số đó
 * ({@link #SCHEDULED} → {@link #ACTIVE} → {@link #EXPIRED}) đổi <b>theo thời
 * gian</b>, tức là không có lệnh ghi nào để móc vào. Giữ đồng bộ thì phải có một
 * tác vụ định kỳ, mà máy chủ này ngủ sau 15 phút vắng khách — xem ghi chú cùng
 * chủ đề ở {@link Publishable}.
 *
 * <p>Suy ra lúc đọc thì không có trạng thái nào bất khả thi, và một mã hẹn 20:00
 * trở nên đổi được đúng 20:00:00 với mọi người, chứ không phải "trong vòng một
 * phút sau đó, tùy lúc job chạy".
 *
 * <h3>Thứ tự xét, và vì sao nó phải cố định</h3>
 * Một mã có thể vừa hết hạn vừa phát hết cùng lúc. Thứ tự ở
 * {@code GiftCode.status()} — tắt → chưa tới giờ → hết hạn → phát hết → đang
 * chạy — trùng đúng với thứ tự kiểm tra lúc đổi mã, nên nhãn mà quản trị viên
 * nhìn thấy và câu lỗi mà người dùng nhận được không bao giờ nói hai chuyện
 * khác nhau về cùng một cái mã.
 */
public enum GiftCodeStatus {

    /** Đã tạo nhưng chưa tới {@code startAt}. */
    SCHEDULED("Chờ tới giờ"),

    /** Đang đổi được. */
    ACTIVE("Đang phát"),

    /** Đã qua {@code endAt}. */
    EXPIRED("Hết hạn"),

    /** Quản trị viên tắt bằng tay. */
    DISABLED("Đã tắt"),

    /** Đã đủ {@code maxUses} lượt đổi. */
    EXHAUSTED("Hết lượt");

    private final String label;

    GiftCodeStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt cho bảng quản trị. */
    public String getLabel() {
        return label;
    }

    /** Chỉ {@link #ACTIVE} mới đổi được; bốn giá trị còn lại đều là một lời từ chối. */
    public boolean redeemable() {
        return this == ACTIVE;
    }
}
