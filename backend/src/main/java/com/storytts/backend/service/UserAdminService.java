package com.storytts.backend.service;

import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý thành viên phía Admin.
 * Trọng tâm: cấp/thu hồi VIP thủ công — đề bài không tích hợp cổng thanh toán (mục 4.1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String keyword, Pageable pageable) {
        return PageResponse.from(userRepository.search(keyword, pageable), UserDto::from);
    }

    /** Admin bật/tắt cờ VIP cho một thành viên. */
    @Transactional
    public UserDto setVip(Long userId, boolean vip) {
        User user = findUser(userId);
        if (user.isAdmin()) {
            throw new BadRequestException("Tài khoản quản trị viên đã có toàn quyền, không cần gán VIP.");
        }
        user.setVip(vip);
        log.info("Admin {} quyền VIP cho tài khoản {}", vip ? "cấp" : "thu hồi", user.getUsername());
        return UserDto.from(userRepository.save(user));
    }

    /** Khóa/mở khóa tài khoản. Không cho Admin tự khóa chính mình. */
    @Transactional
    public UserDto setEnabled(Long userId, boolean enabled) {
        User user = findUser(userId);
        Long currentId = currentUserService.currentUserId().orElse(null);
        if (!enabled && user.getId().equals(currentId)) {
            throw new BadRequestException("Bạn không thể tự khóa tài khoản của chính mình.");
        }
        user.setEnabled(enabled);
        return UserDto.from(userRepository.save(user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("người dùng", userId));
    }
}
