package com.storytts.backend.controller;

import com.storytts.backend.dto.support.SupportConversationDto;
import com.storytts.backend.dto.support.SupportReadRequest;
import com.storytts.backend.dto.support.SupportSendRequest;
import com.storytts.backend.dto.support.SupportSendResponse;
import com.storytts.backend.dto.support.SupportSummaryDto;
import com.storytts.backend.dto.support.SupportThreadDto;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.support.SupportService;
import com.storytts.backend.service.support.SupportStore;
import com.storytts.backend.service.support.SupportStreamTickets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hộp thư hỗ trợ của người đang đăng nhập.
 *
 * <h3>Không có tham số {@code conversationId} ở bất kỳ đường nào</h3>
 * Và đó là toàn bộ phần phân quyền của nhóm này. Danh tính lấy từ
 * {@link CurrentUserService} — tức là từ token đã được lọc và đã được đối chiếu
 * với cơ sở dữ liệu ở mỗi request — rồi luồng được suy ra từ quyền sở hữu. Một
 * cuộc tấn công IDOR cần một tham số để sửa; ở đây không có tham số nào.
 *
 * <p>Cùng cách chia đã dùng ở {@code NotificationController}, và vì cùng lý do.
 *
 * <h3>Vì sao có đường REST khi đã có WebSocket</h3>
 * Ba việc mà WebSocket không làm, hoặc không nên làm:
 *
 * <ol>
 *   <li><b>Đồng bộ ban đầu và phục hồi.</b> Mở trang, cuộn lên xem tin cũ, và
 *       lấy phần bỏ lỡ sau mỗi lần nối lại — cả ba là truy vấn lịch sử, và
 *       chúng phải chạy được <i>trước</i> khi có kết nối nào.</li>
 *   <li><b>Đường lui.</b> {@code WebSocket} vắng mặt hoặc bị chặn ở vài webview
 *       nhúng và vài mạng doanh nghiệp. Ở đó hộp thư vẫn gửi được, chỉ là không
 *       thấy tin của bên kia ngay lập tức.</li>
 *   <li><b>Cái vé.</b> Nó phải đi bằng header để chứng minh danh tính, nên nó
 *       không thể đi bằng chính đường mà nó mở ra.</li>
 * </ol>
 *
 * <p>Hai đường gọi vào cùng một {@link SupportService}, nên không có phép kiểm
 * nào chỉ có ở một bên.
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "Hỗ trợ", description = "Hộp thư giữa người đọc và quản trị viên")
public class SupportController {

    private final SupportService supportService;
    private final SupportStreamTickets streamTickets;
    private final CurrentUserService currentUserService;

    /**
     * Luồng của người đang gọi, mở mới nếu chưa có.
     *
     * <h3>Ba cách gọi, một đường</h3>
     * <pre>
     *   không tham số        → trang mới nhất  (mở lần đầu, và MỖI LẦN NỐI LẠI)
     *   ?before=&lt;id&gt;   → tin cũ hơn      (cuộn lên)
     *   ?after=&lt;id&gt;    → tin mới hơn     (bắt kịp sau khi mất kết nối lâu)
     * </pre>
     *
     * <h3>Vì sao nối lại thì gọi cách thứ nhất, không phải cách thứ ba</h3>
     * Đây là chỗ vá một khoảng hở có thật. Số thứ tự tin nhắn được cấp lúc
     * {@code INSERT}, không phải lúc commit, nên hai giao dịch song song có thể
     * commit ngược thứ tự id. Một trình duyệt đã đồng bộ tới id 11 sẽ không bao
     * giờ hỏi lại id 10 nếu id 10 commit muộn hơn — và {@code ?after=11} vĩnh
     * viễn không mang nó về.
     *
     * <p>Tải lại trang cuối thì không có vấn đề ấy: nó đọc trạng thái đã commit
     * tại một thời điểm <i>sau</i> khoảnh khắc hở, nên nó thấy cả hai. Trình
     * duyệt gộp theo id và bỏ trùng, nên việc tải lại vài chục tin đã có không
     * gây ra gì. Xem {@code SupportMessage} và {@code useSupportThread}.
     */
    @GetMapping("/conversation")
    @Operation(summary = "Luồng hỗ trợ của người đang đăng nhập, kèm một trang tin nhắn",
            description = "Không tham số = trang mới nhất. before = cuộn lên. after = lấy phần bỏ lỡ.")
    public SupportThreadDto conversation(@RequestParam(required = false) Long before,
                                         @RequestParam(required = false) Long after,
                                         @RequestParam(required = false) Integer limit) {
        return supportService.threadForUser(actor(), before, after, limit);
    }

    /**
     * Số chưa đọc và trạng thái luồng — không tạo gì cả.
     *
     * <p>Đường mà cái bong bóng ở góc màn hình gọi, trên mọi trang. Nó cố ý
     * <i>không</i> dùng {@link #conversation}: đường kia mở luồng nếu chưa có,
     * và gọi nó ở đây sẽ sinh ra một hàng trong cơ sở dữ liệu cho mỗi người
     * đăng nhập rồi đi đọc truyện. Luồng chỉ được tạo khi người dùng thật sự mở
     * hộp thoại ra và gửi.
     */
    @GetMapping("/summary")
    @Operation(summary = "Số chưa đọc và trạng thái luồng hỗ trợ. Không tạo luồng mới.")
    public SupportSummaryDto summary() {
        return supportService.summaryForUser(actor());
    }

    /**
     * Gửi một tin.
     *
     * <p>Gọi lại với cùng {@code clientMessageId} không tạo tin thứ hai — nó trả
     * về đúng tin đã ghi, kèm {@code duplicate = true}. Đó là điều khiến việc
     * thử lại sau khi mất mạng an toàn, và là lý do định danh ấy bắt buộc.
     */
    @PostMapping("/messages")
    @Operation(summary = "Gửi tin cho bộ phận hỗ trợ",
            description = "Gửi lại cùng clientMessageId là lệnh rỗng: một lần bấm gửi = một tin nhắn.")
    public SupportSendResponse send(@Valid @RequestBody SupportSendRequest request) {
        SupportStore.Appended appended = supportService.sendAsUser(actor(), request);
        return new SupportSendResponse(appended.userView(), appended.userState(),
                appended.duplicate());
    }

    /**
     * Đánh dấu đã đọc tới một tin.
     *
     * <p>Trả về trạng thái luồng mới thay vì 204: con số chưa đọc phải đến từ
     * máy chủ. Trình duyệt trừ đi cho mượt mắt thì được, nhưng con số cuối cùng
     * thì không — nhất là khi một tin mới vừa tới trong cùng khoảnh khắc ấy.
     * Cùng lựa chọn đã ghi ở {@code NotificationController}.
     */
    @PatchMapping("/read")
    @Operation(summary = "Đánh dấu đã đọc tới một tin. Mốc chỉ tiến, không lùi.")
    public SupportConversationDto markRead(@Valid @RequestBody SupportReadRequest request) {
        return supportService.markReadAsUser(actor(), request.lastMessageId());
    }

    /**
     * Xin một vé để mở kết nối WebSocket.
     *
     * <p>Lời gọi này đi bằng header như mọi lời gọi khác, nên nó chứng minh được
     * danh tính. Cái vé nó trả về là thứ duy nhất được phép xuất hiện trên URL —
     * xem {@code OneTimeTicketStore} về lý do không dùng thẳng token phiên.
     *
     * <p>Đường này dùng chung cho cả người đọc lẫn quản trị viên, và cố ý không
     * nhận vai trò nào từ trình duyệt: vai trò được xác định lúc bắt tay, từ cơ
     * sở dữ liệu. Một người đọc xin vé rồi tự nhận là quản trị viên chỉ nhận
     * được một kết nối vai người đọc.
     */
    @PostMapping("/ws-ticket")
    @Operation(summary = "Vé một lần, sống 90 giây, để mở WebSocket hộp thư hỗ trợ")
    public ResponseEntity<StreamTicketDto> websocketTicket() {
        String ticket = streamTickets.issue(currentUserId());
        if (ticket == null) {
            // Hết chỗ giữ vé. Trang vẫn chạy bằng REST, chỉ mất phần thời gian
            // thực — cùng cách xử lý với việc chạm trần kết nối.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(new StreamTicketDto(ticket, streamTickets.ttlSeconds()));
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Ai đang gọi, đã đối chiếu với cơ sở dữ liệu.
     *
     * <p>Một câu SELECT theo khóa chính, không phải hai: đọc id từ phiên rồi mới
     * nạp, thay vì gọi {@code requireCurrentUser()} rồi lại nạp lần nữa ở tầng
     * service.
     */
    private SupportService.Actor actor() {
        return supportService.resolveActor(currentUserId());
    }

    private Long currentUserId() {
        return currentUserService.currentUserId().orElseThrow(() ->
                new LoginRequiredException("Bạn cần đăng nhập để dùng hộp thư hỗ trợ."));
    }

    /** @param expiresInSeconds để trình duyệt biết mình có bao lâu, khỏi phải đoán. */
    public record StreamTicketDto(String ticket, long expiresInSeconds) {
    }
}
