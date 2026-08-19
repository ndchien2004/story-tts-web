package com.storytts.backend.domain;

/**
 * Hai đường tiêu tiền vào nhà cung cấp AI, và cũng là hai hạn mức riêng.
 *
 * <p>Cùng một bảng vì chúng cùng một hình dạng — "người này, hôm nay, bao
 * nhiêu lượt" — nhưng không cùng một bộ đếm: một câu hỏi cho trợ lý rẻ hơn hẳn
 * một chương đem đi tổng hợp giọng nói, nên hai trần được đặt riêng trong
 * {@code application.properties}.
 */
public enum AiUsageKind {

    /** Một bản audio mới do người đọc bấm "Nghe bằng AI". */
    TTS,

    /** Một câu hỏi gửi tới trợ lý đọc truyện. */
    ASSISTANT
}
