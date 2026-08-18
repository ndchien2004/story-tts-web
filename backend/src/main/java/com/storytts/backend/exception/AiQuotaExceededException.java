package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Hết lượt hỏi trợ lý AI trong ngày → HTTP 429.
 *
 * <p>Cùng hình dạng với {@link TtsQuotaExceededException} và cố ý không dùng
 * chung lớp với nó: hai đường tiêu tiền khác nhau, hai hạn mức khác nhau, và
 * câu nói với người dùng cũng khác — một bên là "hết lượt tạo audio", bên này
 * là "hết lượt hỏi". Gộp lại thì thông điệp phải nói chung chung tới mức không
 * còn nói được gì.
 */
@Getter
public class AiQuotaExceededException extends RuntimeException {

    public enum Scope {
        /** Người này hết phần của mình. */
        USER,
        /** Cả hệ thống đã đạt trần trong ngày. */
        GLOBAL
    }

    private final Scope scope;
    private final int limit;

    public AiQuotaExceededException(Scope scope, int limit) {
        super(message(scope, limit));
        this.scope = scope;
        this.limit = limit;
    }

    private static String message(Scope scope, int limit) {
        return switch (scope) {
            case USER -> ("Bạn đã dùng hết %d lượt hỏi trợ lý AI trong hôm nay. "
                    + "Mời bạn quay lại vào ngày mai, hoặc nâng cấp VIP để có thêm lượt.")
                    .formatted(limit);
            case GLOBAL -> "Hôm nay trợ lý AI đã trả lời hết số lượt cho phép. "
                    + "Mời bạn thử lại vào ngày mai.";
        };
    }
}
