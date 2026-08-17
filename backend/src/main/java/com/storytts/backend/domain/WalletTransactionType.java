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
 * <p>Hoàn tiền chưa có giá trị riêng, vì hệ thống chưa có quy tắc hoàn tiền nào.
 * Khi cần trả Xu lại cho ai đó ngay hôm nay, {@link #ADMIN_ADJUSTMENT} làm được
 * việc ấy và để lại đúng dấu vết cần thiết. Một giá trị {@code REFUND} riêng chỉ
 * đáng có khi kèm theo nó là quy tắc "hoàn thì có thu lại quyền đọc không".
 */
public enum WalletTransactionType {

    /** Nạp Xu: tiền thật đã về qua cổng thanh toán. Luôn dương. */
    DEPOSIT("Nạp Xu"),

    /** Mở một chương bằng Xu. Luôn âm. */
    PURCHASE_CHAPTER("Mở khóa chương"),

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
