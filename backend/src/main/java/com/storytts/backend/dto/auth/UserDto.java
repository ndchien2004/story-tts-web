package com.storytts.backend.dto.auth;

import com.storytts.backend.domain.User;

import java.time.Instant;

/** Thông tin người dùng trả về client — tuyệt đối không chứa password_hash. */
public record UserDto(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String role,
        /** Quyền VIP đang có hiệu lực, bất kể do được cấp hay do mua gói. */
        boolean vip,
        /** Riêng phần Admin cấp tay — bảng quản trị cần phân biệt hai nguồn. */
        boolean vipGranted,
        /** Hạn của gói đã mua; null nghĩa là chưa mua gói nào. */
        Instant vipUntil,
        boolean enabled,
        Instant createdAt
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.isVip(),
                user.isVipGranted(),
                user.getVipUntil(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
