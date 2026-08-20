package com.storytts.backend.exception;

import com.storytts.backend.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gom toàn bộ xử lý lỗi về một chỗ, trả JSON thống nhất cho React.
 * Đáp ứng yêu cầu "có xử lý lỗi hợp lý, không crash server".
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Hạn mức tạo audio tính theo ngày ở Việt Nam, không theo UTC. */
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
                null,
                null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Chương có giá Xu mà người đọc chưa mở → 402, kèm giá và số dư.
     *
     * <p>Mã riêng chứ không dùng chung 403 với {@code CHAPTER_LOCKED}: hai tình
     * huống dẫn tới hai màn hình khác hẳn nhau. 403 là ngõ cụt; 402 có một nút bấm
     * ngay tại chỗ, và câu trả lời mang theo đủ số liệu để dựng nút ấy.
     */
    @ExceptionHandler(ChapterPurchaseRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handlePurchaseRequired(ChapterPurchaseRequiredException ex,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiErrorResponse.withDetails(
                        HttpStatus.PAYMENT_REQUIRED.value(),
                        "CHAPTER_PURCHASE_REQUIRED",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Map.of("coinPrice", ex.getCoinPrice(),
                                "balance", ex.getBalance(),
                                "affordable", ex.affordable())));
    }

    /** Bấm mở khóa nhưng không đủ Xu → cũng là 402, cùng hình dạng dữ liệu. */
    @ExceptionHandler(InsufficientCoinsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientCoins(InsufficientCoinsException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiErrorResponse.withDetails(
                        HttpStatus.PAYMENT_REQUIRED.value(),
                        "INSUFFICIENT_COINS",
                        ex.getMessage(),
                        request.getRequestURI(),
                        Map.of("coinPrice", ex.getRequired(),
                                "balance", ex.getBalance(),
                                "affordable", false)));
    }

    /**
     * Gift code bị từ chối → 404 khi không có mã ấy, 409 cho mọi lý do còn lại.
     *
     * <p>Chia hai vì hai chuyện khác nhau: "không tồn tại" nói về việc gõ sai,
     * còn bốn lý do kia nói về <i>trạng thái</i> của một mã có thật — nó chưa tới
     * giờ, đã hết hạn, đã tắt, hoặc đã hết lượt. 409 là mã trạng thái cho đúng
     * loại xung đột ấy, và nó cũng giữ cho "đã đổi rồi" trả lời giống nhau dù đến
     * từ đường kiểm tra trước hay từ ràng buộc duy nhất.
     *
     * <p>Không ghi log ở mức cảnh báo: gõ nhầm một mã hết hạn là chuyện bình
     * thường của người dùng, không phải sự cố của máy chủ.
     */
    @ExceptionHandler(GiftCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleGiftCode(GiftCodeException ex,
                                                           HttpServletRequest request) {
        HttpStatus status = ex.getReason() == GiftCodeException.Reason.INVALID_GIFT_CODE
                ? HttpStatus.NOT_FOUND
                : HttpStatus.CONFLICT;
        return build(status, ex.code(), ex.getMessage(), request);
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

    /**
     * Hai người cùng lưu một chương, và người sau thua.
     *
     * <p>Không có chốt này thì lần lưu thua cuộc vẫn báo thành công trong khi
     * nội dung của nó biến mất — và tệ hơn: cả hai lần lưu đều tăng phiên bản
     * nội dung lên cùng một con số, nên hai nội dung khác nhau cùng mang nhãn
     * v8. Từ lúc ấy phiên bản không còn xác định được nội dung, và mọi kết luận
     * dựng trên nó (bản audio nào còn hợp lệ, trình duyệt nào đang xem bản cũ)
     * đều sai theo mà không có gì báo. Xem {@code Chapter.version}.
     *
     * <p>409 chứ không phải 500: đây không phải lỗi máy chủ mà là một câu trả
     * lời có nghĩa — "có người vừa sửa trước bạn". Thông điệp nói thẳng việc cần
     * làm, vì thứ duy nhất làm được ở đây là mở lại chương và soạn lại trên nền
     * nội dung mới.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Ghi đè đồng thời bị chặn tại {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Có người khác vừa lưu chương này trong lúc bạn đang sửa. "
                        + "Vui lòng mở lại chương để xem nội dung mới nhất rồi sửa lại — "
                        + "lưu đè bây giờ sẽ xóa mất thay đổi của họ.",
                request);
    }

    /**
     * Tài khoản bị khóa, phát hiện ở một đường đi qua controller — trên thực tế
     * là đăng nhập.
     *
     * <p>Mọi request đã mang token thì không tới được đây: chúng bị
     * {@code JwtAuthenticationFilter} chặn từ tầng filter, trước khi có
     * controller nào chạy. Cả hai chỗ trả về cùng một mã và cùng một câu, nên
     * frontend chỉ cần biết một quy tắc duy nhất.
     *
     * <p>401 chứ không phải 400 như trước. Đây là câu trả lời về danh tính chứ
     * không phải về dữ liệu gửi lên — người dùng gõ đúng mật khẩu, cái sai nằm ở
     * trạng thái tài khoản. Và nó phải trùng mã trạng thái với đường filter, vì
     * §30 của yêu cầu đòi mọi đường đều nói cùng một câu.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLocked(AccountLockedException ex,
                                                                HttpServletRequest request) {
        log.info("Từ chối đăng nhập vào một tài khoản bị khóa tại {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, AccountLockedException.CODE, ex.getMessage(), request);
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

    /**
     * Hết lượt tạo audio trong ngày → 429, kèm {@code Retry-After} tính tới nửa
     * đêm giờ Việt Nam — đúng lúc hạn mức được cấp lại.
     */
    @ExceptionHandler(TtsQuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTtsQuota(TtsQuotaExceededException ex,
                                                          HttpServletRequest request) {
        log.info("Chặn tạo audio vì hết lượt ({}, hạn mức {}) tại {}",
                ex.getScope(), ex.getLimit(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(secondsUntilQuotaReset()))
                .body(ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "TTS_QUOTA_EXCEEDED", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Tài khoản đang trong quãng nghỉ vì gõ sai quá nhiều → 429.
     *
     * <p>{@code Retry-After} ở đây tính bằng giây tới lúc hết nghỉ, không phải
     * tới nửa đêm như hạn mức theo ngày: hai con số trả lời hai câu hỏi khác
     * nhau, và trang đăng nhập hiển thị đúng con số này thành "thử lại sau N
     * phút".
     */
    @ExceptionHandler(LoginThrottledException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginThrottled(LoginThrottledException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),
                        LoginThrottledException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRequired(LoginRequiredException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    /** Lỗi gọi API TTS bên ngoài → 502, kèm thông báo dễ hiểu cho người dùng. */
    @ExceptionHandler(TtsException.class)
    public ResponseEntity<ApiErrorResponse> handleTts(TtsException ex, HttpServletRequest request) {
        log.warn("Lỗi TTS: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "TTS_ERROR", ex.getMessage(), request);
    }

    /**
     * Hết lượt hỏi trợ lý trong ngày → 429, kèm {@code Retry-After} tính tới
     * nửa đêm giờ Việt Nam — đúng lúc hạn mức được cấp lại.
     */
    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleAiQuota(AiQuotaExceededException ex,
                                                          HttpServletRequest request) {
        log.info("Chặn hỏi trợ lý AI vì hết lượt ({}, hạn mức {}) tại {}",
                ex.getScope(), ex.getLimit(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(secondsUntilQuotaReset()))
                .body(ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "AI_QUOTA_EXCEEDED", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Trợ lý AI không trả lời được.
     *
     * <p>Hai mã trạng thái cho hai chuyện khác nhau, và giao diện đọc được sự
     * khác nhau ấy: 503 là "chỗ này chưa mở" — nói xong là hết, đừng mời bấm
     * lại; 502 là "hỏng lúc này" — có một nút thử lại là hợp lý.
     *
     * <p>Thông điệp trả về luôn là câu viết sẵn trong {@link AiAssistantException}.
     * Nguyên văn lỗi của nhà cung cấp đã được ghi vào log ở tầng client và không
     * đi ra ngoài: nó có thể mang theo địa chỉ nội bộ hoặc một phần khoá.
     */
    @ExceptionHandler(AiAssistantException.class)
    public ResponseEntity<ApiErrorResponse> handleAiAssistant(AiAssistantException ex,
                                                              HttpServletRequest request) {
        boolean unavailable = ex.getKind() == AiAssistantException.Kind.UNAVAILABLE;
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        String code = unavailable ? "AI_UNAVAILABLE" : "AI_ERROR";

        if (unavailable) {
            log.debug("Trợ lý AI chưa được cấu hình, từ chối tại {}", request.getRequestURI());
        } else {
            log.warn("Trợ lý AI hỏng tại {}: {}", request.getRequestURI(), ex.getMessage());
        }
        return build(status, code, ex.getMessage(), request);
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
                fieldErrors,
                null);
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

    /** Số giây còn lại tới nửa đêm giờ Việt Nam — mốc hạn mức mỗi ngày được cấp lại. */
    private long secondsUntilQuotaReset() {
        ZonedDateTime now = ZonedDateTime.now(QUOTA_ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(QUOTA_ZONE))
                .getSeconds();
    }
}
