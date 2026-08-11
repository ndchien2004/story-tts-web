package com.storytts.backend.controller;

import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Hồ sơ của chính người đang đăng nhập.
 *
 * <p>Tách khỏi {@code AuthController} vì đó là chỗ của đăng ký/đăng nhập, còn đây là
 * thao tác trên tài khoản đã có. Mọi endpoint ở đây đều nằm sau {@code anyRequest().authenticated()}
 * trong {@code SecurityConfig}, nên không cần kiểm tra đăng nhập lần nữa.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Hồ sơ", description = "Thông tin và ảnh đại diện của tài khoản đang đăng nhập")
public class ProfileController {

    private final UserProfileService userProfileService;

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    @Operation(summary = "Tải lên ảnh đại diện mới (lưu trên Cloudinary)")
    public UserDto uploadAvatar(@RequestPart("file") MultipartFile file) {
        return userProfileService.updateAvatar(file);
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Gỡ ảnh đại diện, quay về chữ cái đầu")
    public UserDto removeAvatar() {
        return userProfileService.removeAvatar();
    }

    /**
     * Cho giao diện biết có nên hiện nút đổi ảnh hay không.
     * Máy chủ chưa điền khóa Cloudinary thì nút đó chỉ dẫn tới một thông báo lỗi.
     */
    @GetMapping("/avatar/available")
    @Operation(summary = "Máy chủ đã cấu hình Cloudinary hay chưa")
    public Map<String, Boolean> avatarAvailable() {
        return Map.of("available", userProfileService.avatarUploadAvailable());
    }
}
