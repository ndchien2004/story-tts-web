package com.storytts.backend.controller;

import com.storytts.backend.dto.auth.AuthProvidersDto;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.ForgotPasswordRequest;
import com.storytts.backend.dto.auth.GoogleLoginRequest;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.RegisterResponse;
import com.storytts.backend.dto.auth.ResendCodeRequest;
import com.storytts.backend.dto.auth.ResetPasswordRequest;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.dto.auth.VerifyRegistrationRequest;
import com.storytts.backend.service.AuthService;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.PasswordResetService;
import com.storytts.backend.service.RegistrationService;
import com.storytts.backend.service.tts.ReaderNarrationCleanup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Xác thực", description = "Đăng ký, đăng nhập, thông tin tài khoản")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final CurrentUserService currentUserService;
    private final ReaderNarrationCleanup readerNarrationCleanup;

    @GetMapping("/providers")
    @Operation(summary = "Các cách đăng nhập máy chủ đang bật (Google, quên mật khẩu)")
    public AuthProvidersDto providers() {
        return authService.providers();
    }

    /**
     * Chưa tạo tài khoản ở bước này: máy chủ gửi mã xác thực về email và chỉ ghi
     * vào cơ sở dữ liệu sau khi mã được nhập đúng ở {@code /register/verify}.
     * Ngoại lệ duy nhất là khi máy chủ chưa cấu hình email — xem
     * {@code RegistrationService}.
     */
    @PostMapping("/register")
    @Operation(summary = "Bắt đầu đăng ký: gửi mã xác thực về email")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registrationService.start(request);
        HttpStatus status = response.verificationRequired() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/register/verify")
    @Operation(summary = "Nhập mã xác thực; đúng mã thì tài khoản mới được tạo")
    public ResponseEntity<AuthResponse> verifyRegistration(
            @Valid @RequestBody VerifyRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.verify(request.email(), request.code()));
    }

    @PostMapping("/register/resend")
    @Operation(summary = "Gửi lại mã xác thực cho một lượt đăng ký đang chờ")
    public Map<String, String> resendRegistrationCode(@Valid @RequestBody ResendCodeRequest request) {
        registrationService.resendCode(request.email());
        return Map.of("message", "Đã gửi lại mã xác thực. Vui lòng kiểm tra cả hộp thư rác.");
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng username hoặc email, trả về JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    @Operation(summary = "Đăng nhập bằng Google; lần đầu thì tạo luôn tài khoản")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    /**
     * Trả lời như nhau dù email có tồn tại hay không. Nói thẳng "email này chưa
     * đăng ký" là biến trang quên mật khẩu thành công cụ dò danh sách người dùng.
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Gửi liên kết đặt lại mật khẩu tới email")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return Map.of("message",
                "Nếu email này đã đăng ký, chúng tôi vừa gửi liên kết đặt lại mật khẩu. "
                        + "Vui lòng kiểm tra cả hộp thư rác.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt mật khẩu mới bằng token trong email")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return Map.of("message", "Đã đổi mật khẩu. Bạn có thể đăng nhập bằng mật khẩu mới.");
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin tài khoản đang đăng nhập (gồm cờ VIP)")
    public UserDto me() {
        return authService.currentUser();
    }

    /**
     * JWT là stateless nên "đăng xuất" thực chất là client xóa token đang lưu.
     *
     * <p>Nhưng có một thứ ở phía máy chủ phải mất theo phiên: bản audio người
     * đọc tự dựng. Nó được tạo cho một buổi đọc, không phải để nằm lại trên đĩa.
     */
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất (client tự xóa token đã lưu, máy chủ bỏ audio tạm của phiên)")
    public Map<String, String> logout() {
        currentUserService.currentUserId().ifPresent(readerNarrationCleanup::discardFor);
        return Map.of("message", "Đã đăng xuất. Vui lòng xóa token ở phía client.");
    }
}
