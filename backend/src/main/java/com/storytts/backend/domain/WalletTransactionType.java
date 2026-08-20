package com.storytts.backend.domain;

/**
 * Vì sao một dòng sổ cái tồn tại.
 *
 * <p>Danh sách này cố ý ngắn. Mỗi giá trị ở đây tương ứng với một đường mã nguồn
 * thật sự ghi được ra nó; thêm {@code BONUS}, {@code PROMOTION} hay
 * {@code REFERRAL} trước khi có tính năng nào sinh ra chúng chỉ tạo ra những
 * nhánh {@code switch} không bao giờ chạy tới và những báo cáo có cột luôn bằng
 * không.
 *
 * <p>Ghi chú cũ ở đây từng nói hoàn tiền chưa đáng có giá trị riêng, vì chưa có
 * quy tắc nào trả lời được câu "hoàn thì có thu lại quyền đọc không". Giờ có:
 * chương bị xóa thì quyền đọc nó biến mất cùng, nên câu trả lời là <i>có</i>, và
 * nó không cần ai quyết định vì không còn gì để đọc. Đó là điều kiện khiến
 * {@link #REFUND_CHAPTER} ra đời — xem {@code ChapterRefundService}.
 *
 * <p>{@link #ADMIN_ADJUSTMENT} vẫn là chỗ cho những lần trả Xu lại không theo
 * quy tắc nào: nó mô tả một quyết định của con người, còn giá trị dưới đây mô tả
 * một hệ quả tự động.
 */
public enum WalletTransactionType {

    /** Nạp Xu: tiền thật đã về qua cổng thanh toán. Luôn dương. */
    DEPOSIT("Nạp Xu"),

    /** Mở một chương bằng Xu. Luôn âm. */
    PURCHASE_CHAPTER("Mở khóa chương"),

    /**
     * Trả lại Xu vì chương đã mua bị gỡ khỏi trang. Luôn dương.
     *
     * <p>Số hoàn là số đã trả lúc mua, không phải giá hiện tại của chương: giá
     * đổi về sau không được làm sai lệch điều đã xảy ra rồi.
     */
    REFUND_CHAPTER("Hoàn Xu do chương bị gỡ"),

    /**
     * Nhận Xu từ một gift code. Luôn dương.
     *
     * <p>Không gộp vào {@link #ADMIN_ADJUSTMENT} dù cả hai đều là Xu do quản trị
     * viên phát ra mà không có tiền thật đi kèm. Hai thứ mô tả hai việc khác
     * nhau: chỉnh tay là một quyết định về <i>một người</i>, còn gift code là một
     * đợt phát có ngân sách, có hạn dùng và có báo cáo riêng. Gộp lại thì câu
     * hỏi "đợt phát mã hè vừa rồi tốn bao nhiêu Xu" không còn trả lời được bằng
     * một câu truy vấn.
     *
     * <p>{@code reference_id} trỏ về {@code gift_codes.id}, nên tra ngược từ một
     * dòng sổ cái về đúng cái mã đã sinh ra nó là một lần đọc chỉ mục.
     */
    GIFT_CODE("Nhận Xu từ gift code"),

    /** Quản trị viên cộng hoặc trừ tay. Dấu nào cũng được. */
    ADMIN_ADJUSTMENT("Điều chỉnh từ quản trị viên");

    private final String label;

    WalletTransactionType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt cho trang lịch sử giao dịch. */
    public String getLabel() {
        return label;
    }
}
