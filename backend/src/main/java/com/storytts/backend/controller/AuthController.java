package com.storytts.backend.controller;

import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.service.AuthService;
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
