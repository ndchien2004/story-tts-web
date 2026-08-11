package com.storytts.backend.service;

import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.UserDto;
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
     */
    @Transactional
    public UserDto updateAvatar(MultipartFile file) {
        User user = currentUserService.requireCurrentUser();
        String url = cloudinaryService.uploadAvatar(file, user.getId());

        user.setAvatarUrl(url);
        log.info("Người dùng id={} đã đổi ảnh đại diện", user.getId());
        return UserDto.from(userRepository.save(user));
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
