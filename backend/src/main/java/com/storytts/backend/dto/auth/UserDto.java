package com.storytts.backend.dto.auth;

import com.storytts.backend.domain.User;

import java.time.Instant;

/** Thông tin người dùng trả về client — tuyệt đối không chứa password_hash. */
public record UserDto(
        Long id,
        String username,
        String email,
        String displayName,
        String role,
        boolean vip,
        boolean enabled,
        Instant createdAt
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isVip(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
