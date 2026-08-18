package com.storytts.backend.exception;

import lombok.Getter;

/**
 * Trợ lý AI không trả lời được — hỏng ở phía nhà cung cấp, hoặc chưa được bật.
 *
 * <p>Hai chuyện khác nhau nên hai mã trạng thái khác nhau, và sự khác nhau ấy
 * đọc được ở phía người dùng: {@link Kind#UNAVAILABLE} là "chỗ này chưa mở",
 * một câu nói xong là hết chuyện; {@link Kind#UPSTREAM} là "thử lại xem", một
 * lời mời bấm lần nữa.
 *
 * <p>Thông điệp trong lớp này luôn là câu viết sẵn cho người đọc. Lỗi thật của
 * Gemini — mã trạng thái, thân phản hồi — chỉ đi vào log; nó có thể mang theo
 * địa chỉ nội bộ hoặc một phần khoá, và không có gì trong đó giúp được người
 * đang ngồi trước màn hình.
 */
@Getter
public class AiAssistantException extends RuntimeException {

    public enum Kind {
        /** Chưa cấu hình, hoặc quản trị viên đã tắt → 503. */
        UNAVAILABLE,
        /** Gọi được nhưng hỏng: timeout, 4xx, 5xx, phản hồi dị dạng → 502. */
        UPSTREAM
    }

    private final Kind kind;

    public AiAssistantException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiAssistantException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /** Chưa có API key, hoặc tính năng bị tắt trong cấu hình. */
    public static AiAssistantException unavailable() {
        return new AiAssistantException(Kind.UNAVAILABLE,
                "Trợ lý AI chưa được bật trên máy chủ này. Bạn vẫn đọc và nghe chương bình thường.");
    }

    /** Câu nói chung cho mọi kiểu hỏng ở phía nhà cung cấp. */
    public static AiAssistantException upstream(Throwable cause) {
        return new AiAssistantException(Kind.UPSTREAM,
                "Trợ lý AI hiện không phản hồi. Vui lòng thử lại sau ít phút.", cause);
    }

    public static AiAssistantException upstream(String message) {
        return new AiAssistantException(Kind.UPSTREAM, message);
    }
}
