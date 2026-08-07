package com.storytts.backend.service;

import com.storytts.backend.domain.User;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Cung cấp thông tin người dùng đang gọi API.
 * Trả về {@link Optional#empty()} nghĩa là "Khách" — chưa đăng nhập.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public Optional<Long> currentUserId() {
        return currentPrincipal().map(AppUserPrincipal::getId);
    }

    public boolean isAuthenticated() {
        return currentPrincipal().isPresent();
    }

    /** Bắt buộc phải đăng nhập, dùng cho các API cá nhân (yêu thích, tiến độ đọc...). */
    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Long id = currentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("Chưa đăng nhập."));
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("người dùng", id));
    }
}
