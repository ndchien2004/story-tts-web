package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.service.notification.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tin do quản trị viên soạn tay.
 *
 * <p>Nằm dưới {@code /api/admin/**}, tức là sau {@code hasRole("ADMIN")} của
 * {@code SecurityConfig} — cùng hàng rào với mọi thao tác quản trị khác, và
 * không có phép kiểm quyền nào lặp lại ở đây. Phía trình duyệt cũng giấu màn
 * hình này với người không phải quản trị viên, nhưng đó là chuyện giao diện:
 * thứ thật sự chặn nằm ở tầng URL.
 *
 * <p>Mọi thông báo khác của trang — VIP, gỡ chương, hoàn Xu, thanh toán — không
 * đi qua đây mà sinh ra ngay bên trong giao dịch nghiệp vụ của chúng. Đường này
 * chỉ dành cho thứ không có nghiệp vụ nào phía sau: một lời loan báo.
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin - Thông báo", description = "Gửi tin cho một người hoặc loan cho tất cả")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @PostMapping
    @Operation(summary = "Gửi một thông báo",
            description = "Bấm hai lần trong cùng một ngày với cùng nội dung chỉ tạo ra một "
                    + "thông báo cho mỗi người nhận.")
    public AdminNotificationService.Result send(@Valid @RequestBody SendRequest request) {
        return adminNotificationService.send(
                request.target(),
                request.userId(),
                request.type() == null ? NotificationType.ANNOUNCEMENT : request.type(),
                request.priority() == null ? NotificationPriority.INFO : request.priority(),
                request.title(),
                request.message());
    }

    /**
     * Thân yêu cầu.
     *
     * <p>Không có trường URL, và cố ý: một đường dẫn tự do do quản trị viên gõ
     * là đúng thứ mà {@code NotificationAction} sinh ra để tránh — xem ghi chú
     * ở enum ấy. Tin loan chung không cần nút bấm nào; loại tin cần nút thì đã
     * có nghiệp vụ riêng tạo ra nó.
     *
     * @param target ALL hay USER
     * @param userId chỉ dùng khi {@code target} là USER
     */
    public record SendRequest(
            @NotNull(message = "Vui lòng chọn người nhận")
            AdminNotificationService.Target target,

            Long userId,

            NotificationType type,

            NotificationPriority priority,

            @NotBlank(message = "Vui lòng nhập tiêu đề")
            @Size(max = 160, message = "Tiêu đề tối đa 160 ký tự")
            String title,

            @NotBlank(message = "Vui lòng nhập nội dung")
            @Size(max = 500, message = "Nội dung tối đa 500 ký tự")
            String message
    ) {
    }
}
