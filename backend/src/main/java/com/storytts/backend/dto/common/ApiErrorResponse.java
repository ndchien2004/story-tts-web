package com.storytts.backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Định dạng lỗi thống nhất cho toàn bộ REST API.
 *
 * @param requiredAccessLevel chỉ có mặt khi lỗi do khóa chương (PUBLIC/MEMBER/VIP),
 *                            giúp React chọn đúng màn hình yêu cầu đăng nhập hay nâng cấp VIP.
 * @param fieldErrors         lỗi kiểm tra dữ liệu, theo từng trường của form
 * @param details             số liệu kèm theo mà giao diện cần để dựng màn hình lỗi —
 *                            giá chương và số dư Xu chẳng hạn. Tách khỏi
 *                            {@code fieldErrors} vì đây không phải lỗi nhập liệu,
 *                            và giá trị không phải lúc nào cũng là chuỗi.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requiredAccessLevel,
        Map<String, String> fieldErrors,
        Map<String, Object> details
) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, null, null);
    }

    /** Lỗi kèm số liệu cho giao diện — xem {@link #details}. */
    public static ApiErrorResponse withDetails(int status, String error, String message,
                                               String path, Map<String, Object> details) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, null, details);
    }
}
