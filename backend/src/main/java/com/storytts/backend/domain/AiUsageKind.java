package com.storytts.backend.domain;

/**
 * Ba đường tiêu tiền vào nhà cung cấp AI, và cũng là ba hạn mức riêng.
 *
 * <p>Cùng một bảng vì chúng cùng một hình dạng — "người này, hôm nay, bao
 * nhiêu lượt" — nhưng không cùng một bộ đếm: một câu hỏi cho trợ lý rẻ hơn hẳn
 * một chương đem đi tổng hợp giọng nói, nên mỗi trần được đặt riêng trong
 * {@code application.properties}.
 */
public enum AiUsageKind {

    /** Một bản audio mới do người đọc bấm "Nghe bằng AI". */
    TTS,

    /** Một câu hỏi gửi tới trợ lý đọc truyện. */
    ASSISTANT,

    /**
     * Một câu hỏi gửi tới trợ lý hỗ trợ trong hộp thư. Thêm ở V16.
     *
     * <p>Bộ đếm riêng chứ không dùng chung {@link #ASSISTANT}, và đây là một
     * quyết định nghiệp vụ chứ không phải chuyện kế toán: chung một bộ đếm thì
     * một người vừa hỏi hết lượt về chương đang đọc sẽ bị từ chối đúng lúc cần
     * hỏi một câu hỗ trợ. Hai việc không liên quan gì tới nhau, và cái thứ hai
     * là đường người ta đi tìm giúp đỡ.
     *
     * <p>Hết lượt loại này không bao giờ là ngõ cụt: nút "Chat với tư vấn viên"
     * luôn ở đó và không tốn lượt nào. Đó là điều khiến trần này đặt được mà
     * không sợ nhốt ai lại.
     */
    SUPPORT
}
