package com.storytts.backend.exception;

/** Dữ liệu đầu vào không hợp lệ về mặt nghiệp vụ → HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
