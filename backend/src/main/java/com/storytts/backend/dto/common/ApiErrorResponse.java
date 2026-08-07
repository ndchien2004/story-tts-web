package com.storytts.backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Định dạng lỗi thống nhất cho toàn bộ REST API.
 *
 * @param requiredAccessLevel chỉ có mặt khi lỗi do khóa chương (PUBLIC/MEMBER/VIP),
 *                            giúp React chọn đúng màn hình yêu cầu đăng nhập hay nâng cấp VIP.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requiredAccessLevel,
        Map<String, String> fieldErrors
) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, null);
    }
}
