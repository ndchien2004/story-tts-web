package com.storytts.backend.controller;

import com.storytts.backend.dto.auth.AuthProvidersDto;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.ForgotPasswordRequest;
import com.storytts.backend.dto.auth.GoogleLoginRequest;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.ResetPasswordRequest;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.service.AuthService;
import com.storytts.backend.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    @GetMapping("/providers")
    @Operation(summary = "Các cách đăng nhập máy chủ đang bật (Google, quên mật khẩu)")
    public AuthProvidersDto providers() {
        return authService.providers();
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới (mặc định là Thành viên thường)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
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
     * Endpoint này giữ lại để frontend gọi cho thống nhất luồng.
     */
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất (client tự xóa token đã lưu)")
    public Map<String, String> logout() {
        return Map.of("message", "Đã đăng xuất. Vui lòng xóa token ở phía client.");
    }
}
