package com.storytts.backend.service;

import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Hồ sơ của chính người đang đăng nhập: xem thông tin và đổi ảnh đại diện. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final CurrentUserService currentUserService;

    /**
     * Đổi ảnh đại diện.
     *
     * <p>Ảnh lên Cloudinary trước, có URL rồi mới ghi vào cơ sở dữ liệu: tải lên hỏng
     * thì hồ sơ vẫn giữ nguyên ảnh cũ chứ không trỏ vào một đường dẫn không tồn tại.
     *
     * <p><b>Cố ý không có {@code @Transactional}.</b> Lệnh tải lên có hạn chờ 45
     * giây, và một giao dịch bao quanh nó là một kết nối cơ sở dữ liệu bị giữ suốt
     * quãng ấy mà không chạy câu lệnh nào. Ai đang gọi thì đọc từ token chứ không
     * phải từ bảng {@code users}, nên tới lúc gọi Cloudinary vẫn chưa có câu SELECT
     * nào được chạy; phần ghi phía sau là một câu UPDATE trong giao dịch riêng của
     * nó ({@code UserRepository.updateAvatarUrl}).
     */
    public UserDto updateAvatar(MultipartFile file) {
        Long userId = currentUserService.currentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("Chưa đăng nhập."));

        String url = cloudinaryService.uploadAvatar(file, userId);

        if (userRepository.updateAvatarUrl(userId, url) == 0) {
            throw ResourceNotFoundException.of("người dùng", userId);
        }

        log.info("Người dùng id={} đã đổi ảnh đại diện", userId);
        return UserDto.from(userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("người dùng", userId)));
    }

    /**
     * Gỡ ảnh đại diện, quay về chữ cái đầu.
     *
     * <p>Chỉ xóa đường dẫn trong cơ sở dữ liệu. File trên Cloudinary vẫn ở đó và sẽ bị
     * ghi đè ở lần tải lên sau, vì mỗi người dùng dùng cố định một public_id.
     */
    @Transactional
    public UserDto removeAvatar() {
        User user = currentUserService.requireCurrentUser();
        user.setAvatarUrl(null);
        return UserDto.from(userRepository.save(user));
    }

    public boolean avatarUploadAvailable() {
        return cloudinaryService.isConfigured();
    }
}
