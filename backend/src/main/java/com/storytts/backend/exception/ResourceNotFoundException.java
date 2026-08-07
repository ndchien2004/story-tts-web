package com.storytts.backend.exception;

/** Không tìm thấy tài nguyên → HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("Không tìm thấy %s với id = %s".formatted(resource, id));
    }
}
