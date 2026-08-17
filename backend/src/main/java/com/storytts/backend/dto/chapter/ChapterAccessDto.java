package com.storytts.backend.dto.chapter;

import com.storytts.backend.service.ChapterAccessDecision;

/**
 * Người đang gọi có mở được chương này không, và nếu không thì làm gì được.
 *
 * <p>Trả về đủ số liệu để trang đọc dựng xong màn hình mà không phải hỏi thêm:
 * giá bao nhiêu, mình còn bao nhiêu, thiếu bao nhiêu. Thiếu một trong ba thì giao
 * diện phải gọi tiếp sang ví rồi tự trừ — và tự trừ ở hai nơi là hai nơi có thể
 * tính sai.
 *
 * @param decision   tên trạng thái, xem {@link ChapterAccessDecision}
 * @param allowed    đọc được ngay hay không
 * @param purchasable mở được bằng Xu — thứ quyết định có hiện nút "Mở khóa" không
 * @param coinPrice  giá chương, 0 nếu chương không bán lẻ
 * @param balance    số dư Xu hiện tại của người gọi; 0 với khách chưa đăng nhập
 * @param shortfall  còn thiếu bao nhiêu Xu; 0 nghĩa là bấm mở được ngay
 */
public record ChapterAccessDto(
        Long chapterId,
        String decision,
        boolean allowed,
        boolean purchasable,
        long coinPrice,
        long balance,
        long shortfall
) {

    public static ChapterAccessDto of(Long chapterId, ChapterAccessDecision decision,
                                      long coinPrice, long balance) {
        long shortfall = decision.purchasable() ? Math.max(coinPrice - balance, 0L) : 0L;
        return new ChapterAccessDto(
                chapterId,
                decision.name(),
                decision.allowed(),
                decision.purchasable(),
                coinPrice,
                balance,
                shortfall);
    }
}
