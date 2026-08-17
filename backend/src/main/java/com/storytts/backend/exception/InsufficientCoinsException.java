package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Không đủ Xu để mở chương → HTTP 402 Payment Required.
 *
 * <p>Mang theo giá và số dư để trang đọc dựng được câu "Chương này 50 Xu, bạn còn
 * 20 Xu" cùng nút dẫn sang trang nạp — thay vì một lời từ chối trống không buộc
 * người dùng tự đi tìm xem mình còn bao nhiêu.
 */
@Getter
public class InsufficientCoinsException extends RuntimeException {

    private final long required;
    private final long balance;

    public InsufficientCoinsException(long required, long balance) {
        super("Chương này cần %,d Xu nhưng bạn chỉ còn %,d Xu.".formatted(required, balance));
        this.required = required;
        this.balance = balance;
    }
}
