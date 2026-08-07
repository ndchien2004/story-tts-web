package com.storytts.backend.exception;

import com.storytts.backend.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Gom toàn bộ xử lý lỗi về một chỗ, trả JSON thống nhất cho React.
 * Đáp ứng yêu cầu "có xử lý lỗi hợp lý, không crash server" (mục 5 đề bài).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Chương bị khóa → 403 kèm mức quyền còn thiếu, tuyệt đối không kèm nội dung chương. */
    @ExceptionHandler(ChapterLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleChapterLocked(ChapterLockedException ex,
                                                                HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "CHAPTER_LOCKED",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getRequiredAccessLevel().name(),
                null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                "Tên đăng nhập hoặc mật khẩu không đúng.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Bạn không có quyền thực hiện thao tác này.", request);
    }

    /** Lỗi gọi API TTS bên ngoài → 502, kèm thông báo dễ hiểu cho người dùng. */
    @ExceptionHandler(TtsException.class)
    public ResponseEntity<ApiErrorResponse> handleTts(TtsException ex, HttpServletRequest request) {
        log.warn("Lỗi TTS: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "TTS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Dữ liệu gửi lên không hợp lệ.",
                request.getRequestURI(),
                null,
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "File tải lên vượt quá dung lượng cho phép.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleOther(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không lường trước tại {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã có lỗi xảy ra ở máy chủ. Vui lòng thử lại sau.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error,
                                                   String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
