package com.storytts.backend.dto.wallet;

/**
 * Kết quả một lần bấm "Mở khóa bằng Xu".
 *
 * <p>Ba kết cục đều là thành công về mặt HTTP, vì cả ba đều dẫn tới cùng một điều
 * người dùng muốn: chương mở ra. Chỉ có {@link #coinsSpent} khác nhau, và đó là
 * thứ giao diện cần để quyết định có hiện thông báo "đã trừ 50 Xu" hay không.
 *
 * @param chapterId  chương vừa được mở
 * @param outcome    xem {@link Outcome}
 * @param coinsSpent số Xu vừa bị trừ; 0 khi chương đã mở sẵn từ trước
 * @param balance    số dư sau thao tác, để giao diện cập nhật mà không phải hỏi lại
 */
public record ChapterPurchaseDto(
        Long chapterId,
        Outcome outcome,
        long coinsSpent,
        long balance
) {

    public enum Outcome {

        /** Vừa trừ Xu và cấp quyền. */
        PURCHASED,

        /** Đã mua từ trước — hoặc lần bấm này là lần thứ hai. */
        ALREADY_OWNED,

        /**
         * Không cần mua: người này vốn đã đọc được chương (VIP, hoặc quản trị
         * viên). Trả về thay vì trừ Xu — bán cho ai đó thứ họ đang có sẵn là một
         * lỗi, không phải một giao dịch.
         */
        ALREADY_ACCESSIBLE
    }

    public static ChapterPurchaseDto purchased(Long chapterId, long price, long balance) {
        return new ChapterPurchaseDto(chapterId, Outcome.PURCHASED, price, balance);
    }

    public static ChapterPurchaseDto alreadyOwned(Long chapterId, long balance) {
        return new ChapterPurchaseDto(chapterId, Outcome.ALREADY_OWNED, 0L, balance);
    }

    public static ChapterPurchaseDto alreadyAccessible(Long chapterId, long balance) {
        return new ChapterPurchaseDto(chapterId, Outcome.ALREADY_ACCESSIBLE, 0L, balance);
    }
}
