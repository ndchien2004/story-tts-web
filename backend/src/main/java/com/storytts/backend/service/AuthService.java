package com.storytts.backend.service;

import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.AuthResponse;
import com.storytts.backend.dto.auth.LoginRequest;
import com.storytts.backend.dto.auth.RegisterRequest;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đăng ký / đăng nhập / lấy thông tin tài khoản hiện tại. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Tên đăng nhập '%s' đã được sử dụng.".formatted(username));
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email '%s' đã được đăng ký.".formatted(email));
        }

        User user = User.builder()
                .username(username)
                .email(email)
                // Mật khẩu luôn được băm bằng BCrypt, không lưu plaintext.
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(Role.MEMBER)
                .vip(false)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("Tài khoản mới đăng ký: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Sai thông tin đăng nhập"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Sai thông tin đăng nhập");
        }
        if (!user.isEnabled()) {
            throw new BadRequestException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDto currentUser() {
        return UserDto.from(currentUserService.requireCurrentUser());
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.getExpirationMs(), UserDto.from(user));
    }
}
