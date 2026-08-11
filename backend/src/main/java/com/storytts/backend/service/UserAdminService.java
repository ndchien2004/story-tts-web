package com.storytts.backend.service;

import com.storytts.backend.domain.Role;
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
 * VIP is granted and revoked by hand here; there is no payment integration.
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
        user.setVipGranted(vip);
        log.info("Admin {} quyền VIP cho tài khoản {}", vip ? "cấp" : "thu hồi", user.getUsername());
        return UserDto.from(userRepository.save(user));
    }

    /**
     * Nâng lên hoặc hạ khỏi quyền quản trị.
     *
     * <p>Hai lối thoát hiểm được canh ở đây, vì cả hai đều dẫn tới tình trạng không tự
     * sửa được bằng giao diện nữa: tự hạ quyền chính mình (đóng cửa từ bên trong), và
     * hạ quyền người quản trị cuối cùng (không còn ai vào được bảng quản trị). Sửa lúc
     * đó chỉ còn cách vào thẳng cơ sở dữ liệu.
     *
     * <p>Lên Admin thì bỏ luôn cờ VIP: Admin vốn đọc được mọi chương, giữ thêm cờ VIP
     * chỉ khiến bảng thành viên trông như có hai nguồn quyền khác nhau.
     */
    @Transactional
    public UserDto setRole(Long userId, Role role) {
        User user = findUser(userId);
        Long currentId = currentUserService.currentUserId().orElse(null);

        if (user.getId().equals(currentId)) {
            throw new BadRequestException(
                    "Bạn không thể tự đổi quyền của chính mình. Hãy nhờ một quản trị viên khác.");
        }
        if (user.isAdmin() && role != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException(
                    "Đây là quản trị viên cuối cùng. Hãy cấp quyền cho người khác trước khi hạ quyền tài khoản này.");
        }

        user.setRole(role);
        if (role == Role.ADMIN) {
            user.setVipGranted(false);
        }

        log.info("Đổi quyền tài khoản {} thành {}", user.getUsername(), role);
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
