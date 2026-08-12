package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Hết lượt tạo audio trong ngày → HTTP 429.
 *
 * <p>Phân biệt hai chuyện khác nhau: người đọc đã dùng hết phần của mình, và cả
 * hệ thống đã đạt trần trong ngày. Trường hợp thứ hai không phải lỗi của họ nên
 * câu trả lời cũng phải khác.
 */
@Getter
public class TtsQuotaExceededException extends RuntimeException {

    public enum Scope {
        /** Người đọc này hết phần của mình. */
        USER,
        /** Cả hệ thống đã đạt trần trong ngày. */
        GLOBAL
    }

    private final Scope scope;
    private final int limit;

    public TtsQuotaExceededException(Scope scope, int limit) {
        super(message(scope, limit));
        this.scope = scope;
        this.limit = limit;
    }

    private static String message(Scope scope, int limit) {
        return switch (scope) {
            case USER -> ("Bạn đã dùng hết %d lượt tạo audio trong hôm nay. "
                    + "Mời bạn quay lại vào ngày mai, hoặc nâng cấp VIP để có thêm lượt. "
                    + "Các chương đã có audio thì vẫn nghe được bình thường.").formatted(limit);
            case GLOBAL -> "Hôm nay hệ thống đã tạo hết số audio cho phép. "
                    + "Bạn vẫn nghe được những bản đã có, mời bạn thử lại vào ngày mai.";
        };
    }
}
