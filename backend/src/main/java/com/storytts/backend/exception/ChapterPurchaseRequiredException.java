package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Chương có giá Xu và người đọc chưa mở nó → HTTP 402 Payment Required.
 *
 * <p>Tách khỏi {@link ChapterLockedException} chứ không dùng chung, vì hai tình
 * huống dẫn tới hai màn hình khác hẳn nhau. Chương bị khóa theo cấp bậc là ngõ
 * cụt với người dùng thường: họ chỉ có thể đăng nhập hoặc đi mua VIP. Chương có
 * giá thì ngược lại — có một nút bấm ngay tại chỗ, và câu trả lời cần mang theo
 * đủ số liệu để dựng nút ấy.
 *
 * <p>Như {@code ChapterLockedException}, ngoại lệ này <b>không</b> chứa nội dung
 * chương.
 */
@Getter
public class ChapterPurchaseRequiredException extends RuntimeException {

    private final long coinPrice;
    private final long balance;

    public ChapterPurchaseRequiredException(long coinPrice, long balance) {
        super(balance >= coinPrice
                ? "Chương này cần %,d Xu để mở khóa.".formatted(coinPrice)
                : "Chương này cần %,d Xu để mở khóa, bạn còn %,d Xu.".formatted(coinPrice, balance));
        this.coinPrice = coinPrice;
        this.balance = balance;
    }

    /** Đủ Xu để bấm mở ngay, hay phải nạp thêm trước. */
    public boolean affordable() {
        return balance >= coinPrice;
    }
}
