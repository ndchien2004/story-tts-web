package com.storytts.backend.exception;

/**
 * Lỗi khi gọi dịch vụ Text-to-Speech (hết quota, sai API key, mạng lỗi...).
 * Được xử lý thành thông báo rõ ràng cho người dùng thay vì làm crash server (mục 5 đề bài).
 */
public class TtsException extends RuntimeException {

    public TtsException(String message) {
        super(message);
    }

    public TtsException(String message, Throwable cause) {
        super(message, cause);
    }
}
