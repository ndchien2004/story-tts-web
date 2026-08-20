package com.storytts.backend.controller;

import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.notification.NotificationDto;
import com.storytts.backend.dto.notification.UnreadCountDto;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.notification.NotificationService;
import com.storytts.backend.service.notification.NotificationStreamTickets;
import com.storytts.backend.service.realtime.UserEventStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hộp thư của người đang đăng nhập.
 *
 * <h3>Không có tham số {@code userId} ở bất kỳ đường nào</h3>
 * Và đó là toàn bộ phần phân quyền của tính năng này. Danh tính lấy từ
 * {@link CurrentUserService} — tức là từ token đã được lọc và đã được đối chiếu
 * với cơ sở dữ liệu ở mỗi request — nên không tồn tại đường nào để một trình
 * duyệt nói rằng nó là người khác. Id thông báo có đoán được cũng không dùng
 * được: mọi truy vấn đều mang thêm điều kiện chủ sở hữu, và một id của người
 * khác trả về 404.
 *
 * <p>Cả nhóm nằm sau {@code anyRequest().authenticated()} của
 * {@code SecurityConfig}, trừ đúng đường luồng SSE — xem {@link #stream}.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Thông báo", description = "Hộp thư của người đọc và luồng đẩy thời gian thực")
public class NotificationController {

    /** Trần một trang. Cùng con số với các đường phân trang khác của API. */
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;
    private final NotificationStreamTickets streamTickets;
    private final UserEventStream userEventStream;
    private final CurrentUserService currentUserService;

    /**
     * Một trang hộp thư, mới nhất trước.
     *
     * <p>Phân trang chứ không trả tất cả, và cả hai bên gọi đều dùng đúng đường
     * này: thanh thông báo xin {@code size=10}, trang lịch sử xin nhiều hơn và
     * đi tiếp bằng {@code page}. Một người đọc lâu năm có hàng nghìn dòng ở đây,
     * và cái chuông không có lý do gì để tải hết chúng về.
     */
    @GetMapping
    @Operation(summary = "Hộp thư của người đang đăng nhập, mới nhất trước")
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        Long userId = currentUserService.requireCurrentUser().getId();
        // Không truyền Sort: thứ tự đã nằm trong chính câu truy vấn, và nó có
        // thêm `id DESC` để hai thông báo sinh ra trong cùng một mili giây
        // không đổi chỗ cho nhau giữa hai lần lật trang. Một Sort ở đây sẽ được
        // nối thêm vào sau, làm hỏng đúng cái mấu chốt ấy.
        return notificationService.inbox(userId,
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }

    /**
     * Chỉ con số trên cái chuông.
     *
     * <p>Đường riêng chứ không đọc {@code totalElements} của danh sách: trang nào
     * cũng cần con số này lúc mở, còn danh sách thì chỉ cần khi người ta bấm vào
     * chuông. Một câu đếm trên chỉ mục rẻ hơn hẳn một trang dữ liệu.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc")
    public UnreadCountDto unreadCount() {
        return notificationService.unreadCount(currentUserService.requireCurrentUser().getId());
    }

    /**
     * Đánh dấu một thông báo là đã đọc.
     *
     * <p>Trả về số chưa đọc mới thay vì 204: trình duyệt có thể trừ đi một cho
     * mượt mắt, nhưng con số cuối cùng phải đến từ máy chủ — nếu không, hai tab
     * cùng bấm sẽ trừ hai lần cho một thông báo.
     */
    @PatchMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc. Gọi trùng không đổi gì thêm.")
    public UnreadCountDto markRead(@PathVariable Long id) {
        Long userId = currentUserService.requireCurrentUser().getId();
        return notificationService.markRead(userId, id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Đánh dấu toàn bộ hộp thư đã đọc")
    public UnreadCountDto markAllRead() {
        return notificationService.markAllRead(currentUserService.requireCurrentUser().getId());
    }

    /**
     * Xin một vé để mở luồng thông báo.
     *
     * <p>Lời gọi này đi bằng header như mọi lời gọi khác, nên nó chứng minh được
     * danh tính. Cái vé nó trả về là thứ duy nhất được phép xuất hiện trên URL —
     * xem {@link NotificationStreamTickets} về lý do không dùng thẳng token phiên.
     */
    @PostMapping("/stream-ticket")
    @Operation(summary = "Vé một lần, sống 60 giây, để mở luồng SSE thông báo")
    public ResponseEntity<StreamTicketDto> streamTicket() {
        Long userId = currentUserService.requireCurrentUser().getId();
        String ticket = streamTickets.issue(userId);
        if (ticket == null) {
            // Hết chỗ giữ vé. Trang vẫn chạy, chỉ mất phần cập nhật tức thời —
            // cùng cách xử lý với việc chạm trần số kết nối bên dưới.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(new StreamTicketDto(ticket, streamTickets.ttlSeconds()));
    }

    /**
     * Luồng SSE mang thông báo mới và trạng thái đã đọc của chính người này.
     *
     * <h3>Vì sao đường này để công khai ở tầng URL</h3>
     * Vì {@code EventSource} không gửi được header, nên chuỗi lọc xác thực không
     * có gì để đọc và sẽ từ chối trước khi controller chạy. Việc kiểm quyền vì
     * thế chuyển vào trong: cái vé là bằng chứng danh tính, và nó được đổi ngay
     * dòng đầu tiên. Không có vé hợp lệ thì không có {@code userId}, và không có
     * {@code userId} thì không có gì được gửi đi.
     *
     * <p>Nói cách khác: "công khai" ở đây chỉ có nghĩa là chuỗi lọc không chặn
     * trước; đường này không trả về gì cho người không cầm vé.
     *
     * <p>Trả 503 khi máy chủ đã hết chỗ cho kết nối mới. Trình duyệt vẫn thấy đủ
     * thông báo — nó đọc hộp thư bằng REST — chỉ là không thấy ngay lập tức.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Luồng SSE thông báo cá nhân",
            description = "Đổi vé lấy danh tính. Vé dùng một lần; nối lại phải xin vé mới.")
    public ResponseEntity<SseEmitter> stream(@RequestParam String ticket) {
        Long userId = streamTickets.redeem(ticket);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SseEmitter emitter = userEventStream.subscribe(userId);
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(emitter);
    }

    /** @param expiresInSeconds để trình duyệt biết mình có bao lâu, khỏi phải đoán. */
    public record StreamTicketDto(String ticket, long expiresInSeconds) {
    }
}
