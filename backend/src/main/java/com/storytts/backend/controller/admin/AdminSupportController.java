package com.storytts.backend.controller.admin;

import com.storytts.backend.domain.SupportConversationStatus;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportInboxItemDto;
import com.storytts.backend.dto.support.SupportReadRequest;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.dto.support.SupportSendResponse;
import com.storytts.backend.dto.support.SupportStatusRequest;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.support.SupportService;
import com.storytts.backend.service.support.SupportSocketRegistry;
import com.storytts.backend.service.support.SupportStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hộp thư hỗ trợ nhìn từ phía quản trị viên.
 *
 * <h3>Hai lớp kiểm quyền, và cả hai đều cần</h3>
 * Nhóm này nằm dưới {@code /api/admin/**}, vốn đã có {@code hasRole('ADMIN')} ở
 * tầng URL trong {@code SecurityConfig}. Nhưng mọi lời gọi bên dưới vẫn đi qua
 * {@code SupportService}, nơi vai trò được đọc lại từ cơ sở dữ liệu và kiểm lại
 * một lần nữa.
 *
 * <p>Lớp thứ hai không thừa. Tầng URL kiểm authority trong token đã được nạp
 * cho request này; tầng service kiểm {@code users.role} <i>lúc này</i>. Khoảng
 * cách giữa hai thứ ấy nhỏ với HTTP nhưng không bằng không — và nó lớn hẳn với
 * WebSocket, nơi cùng những lời gọi này chạy trên một kết nối mở từ nửa tiếng
 * trước. Một lớp kiểm chỉ đúng ở một trong hai cửa vào là một lớp kiểm không
 * đáng tin.
 *
 * <h3>Vì sao {@code conversationId} ở đây là an toàn</h3>
 * Khác {@code SupportController}, nhóm này <i>có</i> nhận id từ trình duyệt —
 * bắt buộc, vì quản trị viên trả lời được mọi luồng. Nó an toàn không phải vì
 * id khó đoán (nó là số tự tăng), mà vì quyền xem <b>mọi</b> luồng là đúng
 * quyền của vai trò này. Không có luồng nào mà một quản trị viên hợp lệ không
 * được xem, nên không có gì để leo thang tới.
 */
@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
@Tag(name = "Quản trị — Hỗ trợ", description = "Hộp thư hỗ trợ dùng chung của cả đội")
public class AdminSupportController {

    private final SupportService supportService;
    private final SupportSocketRegistry socketRegistry;
    private final CurrentUserService currentUserService;

    /**
     * Một trang hộp thư, hoạt động mới nhất trước.
     *
     * @param status lọc theo trạng thái; bỏ trống là "tất cả"
     * @param q      tìm theo tên hiển thị, tên đăng nhập hoặc email của chủ luồng
     */
    @GetMapping("/conversations")
    @Operation(summary = "Danh sách luồng hỗ trợ, xếp theo hoạt động mới nhất")
    public PageResponse<SupportInboxItemDto> inbox(
            @RequestParam(required = false) SupportConversationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return supportService.inbox(actor(), status, q, page, size);
    }

    /**
     * Vài con số cho thanh điều hướng và cho việc theo dõi vận hành.
     *
     * <h3>Vì sao số kết nối đang mở nằm ở đây</h3>
     * Trang này chỉ mở {@code /actuator/health}; các endpoint khác của actuator
     * đọc được biến môi trường, tức là đọc được cả API key lẫn mật khẩu cơ sở
     * dữ liệu, nên chúng cố ý không được phép xuất hiện. Nhưng "có bao nhiêu
     * kết nối WebSocket đang mở" là con số duy nhất phát hiện được rò rỉ kết
     * nối và bão nối lại — hai thứ hỏng âm thầm cho tới lúc chạm trần.
     *
     * <p>Nên nó đi ra bằng đường này: sau {@code hasRole('ADMIN')}, không mang
     * theo gì về nội dung tin nhắn, và không mở thêm bề mặt nào.
     */
    @GetMapping("/summary")
    @Operation(summary = "Số luồng đang chờ trả lời, và số kết nối thời gian thực đang mở")
    public SupportSummaryDto summary() {
        SupportService.Actor actor = actor();
        return new SupportSummaryDto(
                supportService.awaitingReplyCount(actor),
                socketRegistry.openConnections(),
                socketRegistry.openAdminConnections());
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Một luồng: trạng thái, chủ luồng, số chưa đọc")
    public SupportInboxItemDto conversation(@PathVariable Long id) {
        return supportService.conversationForAdmin(actor(), id);
    }

    /** Cùng ba cách gọi với đường của người đọc — xem {@code SupportController.conversation}. */
    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Một trang tin nhắn của luồng",
            description = "Không tham số = trang mới nhất. before = cuộn lên. after = lấy phần bỏ lỡ.")
    public SupportThreadDto messages(@PathVariable Long id,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(required = false) Long after,
                                     @RequestParam(required = false) Integer limit) {
        return supportService.threadForAdmin(actor(), id, before, after, limit);
    }

    /** Trả lời. Chống trùng theo {@code clientMessageId}, y như đường của người đọc. */
    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Trả lời một luồng")
    public SupportSendResponse reply(@PathVariable Long id,
                                     @Valid @RequestBody SupportSendRequest request) {
        SupportStore.Appended appended = supportService.sendAsAdmin(actor(), id, request);
        return new SupportSendResponse(appended.adminView(),
                appended.adminState().conversation(), appended.duplicate());
    }

    /**
     * Đánh dấu phía hỗ trợ đã đọc tới một tin.
     *
     * <p>Mốc dùng chung cho cả đội: một người đã đọc thì việc ấy đã xong với mọi
     * người. Đó là cách một hàng đợi hỗ trợ vận hành, và cũng là thứ giữ cho hai
     * quản trị viên không cùng nhảy vào trả lời một câu.
     */
    @PatchMapping("/conversations/{id}/read")
    @Operation(summary = "Đánh dấu phía hỗ trợ đã đọc tới một tin")
    public SupportConversationDto markRead(@PathVariable Long id,
                                           @Valid @RequestBody SupportReadRequest request) {
        return supportService.markReadAsAdmin(actor(), id, request.lastMessageId());
    }

    /**
     * Đóng, mở lại, khóa hoặc bỏ khóa.
     *
     * <p>Mỗi lần đổi thật sự sinh ra một tin hệ thống trong chính luồng ấy, ghi
     * trong cùng giao dịch — nên người đọc luôn biết vì sao ô soạn tin của họ
     * vừa đổi. Bấm lại cùng một trạng thái là lệnh rỗng: không ghi gì, không đẩy
     * khung tin nào, và vẫn trả về trạng thái hiện tại.
     */
    @PatchMapping("/conversations/{id}/status")
    @Operation(summary = "Đổi trạng thái luồng. CLOSED không chặn gửi — BLOCKED mới chặn.")
    public SupportInboxItemDto changeStatus(@PathVariable Long id,
                                            @Valid @RequestBody SupportStatusRequest request) {
        return supportService.changeStatus(actor(), id, request.status());
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    private SupportService.Actor actor() {
        return supportService.resolveActor(currentUserService.currentUserId()
                .orElseThrow(() -> new LoginRequiredException(
                        "Bạn cần đăng nhập để dùng hộp thư hỗ trợ.")));
    }

    /**
     * @param awaitingReply    số luồng có tin của người đọc mà phía hỗ trợ chưa đọc
     * @param openConnections  tổng số kết nối WebSocket đang mở trên bản ứng dụng này
     * @param adminConnections phần trong số ấy thuộc về quản trị viên
     */
    public record SupportSummaryDto(long awaitingReply, int openConnections,
                                    int adminConnections) {
    }
}
