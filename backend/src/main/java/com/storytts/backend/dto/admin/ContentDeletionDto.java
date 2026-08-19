package com.storytts.backend.dto.admin;

/**
 * Kết quả một lượt xóa chương hoặc truyện.
 *
 * <h3>Vì sao đường xóa trả về một thân response thay vì 204</h3>
 * Xóa một chương đã bán có thể trả lại Xu cho hàng chục người — một việc đụng
 * tới tiền, xảy ra như hệ quả phụ của một cú bấm nút. Trả về 204 nghĩa là việc
 * ấy diễn ra hoàn toàn im lặng: quản trị viên không biết mình vừa hoàn bao
 * nhiêu, và chỉ phát hiện ra khi đọc báo cáo doanh thu tháng sau.
 *
 * <p>Hai con số này đủ để màn hình nói lại một câu, và cũng đủ để nó <i>không</i>
 * nói gì khi không có gì xảy ra — chương không bán bằng Xu thì cả hai bằng 0.
 *
 * @param refundedCoins   tổng số Xu đã trả lại
 * @param refundedReaders số người nhận được tiền hoàn; một người mua nhiều chương
 *                        của cùng một truyện vẫn chỉ đếm là một
 */
public record ContentDeletionDto(long refundedCoins, int refundedReaders) {

    public static ContentDeletionDto nothingRefunded() {
        return new ContentDeletionDto(0L, 0);
    }
}
