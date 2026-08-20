package com.storytts.backend.service;

import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.service.notification.NotificationDraft;
import com.storytts.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String keyword, Pageable pageable) {
        return PageResponse.from(userRepository.search(keyword, pageable), UserDto::from);
    }

    /**
     * Admin bật/tắt cờ VIP cho một thành viên.
     *
     * <h3>Lời chúc mừng đi cùng giao dịch, không đi trước nó</h3>
     * Thông báo được ghi <i>sau</i> khi cờ đã đổi và <i>trong cùng</i> giao dịch
     * này — xem {@code NotificationService}. Giao dịch hỏng thì cả hai cùng biến
     * mất, nên không có đường nào để một người nhận lời chúc "bạn đã thành VIP"
     * cho một lần cấp quyền chưa từng commit.
     *
     * <h3>Chỉ báo khi trạng thái thật sự đổi</h3>
     * Bấm "Cấp VIP" trên một người đã là VIP là lệnh rỗng, và một lệnh rỗng thì
     * không có gì để chúc mừng. Đây cũng là lớp chống trùng thứ nhất: hai lần
     * bấm liên tiếp chỉ có lần đầu đi qua. Lớp thứ hai là {@code eventId} bên
     * dưới, cho trường hợp hai request chạy song song cùng thấy cờ đang tắt.
     *
     * <p>Thu hồi VIP thì không báo gì. Đó là một quyết định về mặt sản phẩm chứ
     * không phải một thiếu sót: lời báo duy nhất viết được ở đây là một câu xấu
     * cho người nhận mà họ không làm gì được với nó, và quyền đọc thì tự nó nói
     * ra ở lần mở chương kế tiếp.
     */
    @Transactional
    public UserDto setVip(Long userId, boolean vip) {
        User user = findUser(userId);
        if (user.isAdmin()) {
            throw new BadRequestException("Tài khoản quản trị viên đã có toàn quyền, không cần gán VIP.");
        }

        boolean becameVip = vip && !user.isVipGranted();
        user.setVipGranted(vip);
        log.info("Admin {} quyền VIP cho tài khoản {}", vip ? "cấp" : "thu hồi", user.getUsername());
        UserDto result = UserDto.from(userRepository.save(user));

        if (becameVip) {
            notificationService.notify(NotificationDraft.to(userId)
                    .type(NotificationType.VIP_GRANTED)
                    .priority(NotificationPriority.SUCCESS)
                    .title("Chúc mừng, bạn đã là thành viên VIP")
                    .message("Quản trị viên vừa cấp quyền VIP cho tài khoản của bạn. "
                            + "Từ giờ bạn mở được mọi chương dành cho VIP, không giới hạn thời hạn.")
                    .action(NotificationAction.VIEW_VIP)
                    // "Vĩnh viễn" là sự thật của cột is_vip, không phải một lời
                    // hứa marketing: quyền Admin cấp tay không có hạn và không bị
                    // một gói mua hết hạn cuốn theo — xem User.isVip().
                    .meta("grant", "PERMANENT")
                    // Không có khóa tự nhiên nào cho một lần bấm nút, nên mốc
                    // giây làm khóa: nó gộp được hai lần bấm sát nhau, còn hai
                    // lần cấp cách nhau thật (cấp → thu hồi → cấp lại) thì đáng
                    // được báo hai lần.
                    .event("vip-granted:" + userId + ":" + Instant.now().getEpochSecond())
                    .build());
        }

        return result;
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
