package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <h2>Lớp kiểm tra quyền truy cập chương — dùng chung cho toàn hệ thống</h2>
 *
 * Mọi luồng đụng tới nội dung chương đều phải đi qua đây, không lặp lại logic ở nhiều nơi
 * (yêu cầu mục 7 và mục 10 đề bài):
 * <ul>
 *   <li>Đọc nội dung chương — {@code GET /api/chapters/{id}}</li>
 *   <li>Nghe audio đã upload — {@code GET /api/chapters/{id}/audio}</li>
 *   <li>Tạo/nghe audio bằng TTS — {@code POST /api/chapters/{id}/tts}</li>
 * </ul>
 *
 * <h3>Quy tắc (mục 8 đề bài)</h3>
 * <table border="1">
 *   <tr><th>access_level</th><th>Khách</th><th>Member</th><th>VIP</th><th>Admin</th></tr>
 *   <tr><td>PUBLIC</td><td>✔</td><td>✔</td><td>✔</td><td>✔</td></tr>
 *   <tr><td>MEMBER</td><td>✘</td><td>✔</td><td>✔</td><td>✔</td></tr>
 *   <tr><td>VIP</td>   <td>✘</td><td>✘</td><td>✔</td><td>✔</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final CurrentUserService currentUserService;

    /**
     * Kiểm tra người dùng hiện tại có được đọc/nghe chương này không.
     *
     * @return true nếu đủ quyền
     */
    public boolean canAccess(Chapter chapter) {
        return canAccess(chapter.getAccessLevel(), currentUserService.currentPrincipal());
    }

    public boolean canAccess(AccessLevel accessLevel) {
        return canAccess(accessLevel, currentUserService.currentPrincipal());
    }

    /**
     * Phiên bản thuần logic, không phụ thuộc SecurityContext — dễ viết unit test (mục 6 đề bài).
     *
     * @param accessLevel mức khóa chương do Admin đặt
     * @param principal   người dùng hiện tại, {@code Optional.empty()} nghĩa là Khách
     */
    public boolean canAccess(AccessLevel accessLevel, Optional<AppUserPrincipal> principal) {
        AccessLevel level = accessLevel == null ? AccessLevel.PUBLIC : accessLevel;

        // Admin luôn xem được mọi chương để còn kiểm duyệt nội dung.
        if (principal.map(AppUserPrincipal::isAdmin).orElse(false)) {
            return true;
        }

        return switch (level) {
            case PUBLIC -> true;
            case MEMBER -> principal.isPresent();
            case VIP -> principal.map(AppUserPrincipal::isVip).orElse(false);
        };
    }

    /**
     * Chặn cứng: không đủ quyền thì ném {@link ChapterLockedException} → HTTP 403.
     * Nội dung chương không bao giờ được trả về client trong trường hợp này (mục 4.3 [BB] đề bài).
     */
    public void requireAccess(Chapter chapter) {
        if (!canAccess(chapter)) {
            throw new ChapterLockedException(
                    chapter.getAccessLevel(),
                    currentUserService.isAuthenticated());
        }
    }
}
