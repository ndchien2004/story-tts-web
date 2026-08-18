package com.storytts.backend.controller;

import com.storytts.backend.dto.chapter.ChapterAccessDto;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.wallet.ChapterPurchaseDto;
import com.storytts.backend.service.ChapterPurchaseService;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.realtime.ChapterEventStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API đọc nội dung chương, và mở khóa chương bằng Xu.
 *
 * <p>Nếu chương bị khóa và người dùng không đủ quyền, service ném lỗi — controller
 * không tự kiểm tra quyền để tránh lặp code. Hai loại từ chối, hai mã trạng thái:
 * 403 khi thiếu cấp bậc (không mua được), 402 khi chương có giá và mua được.
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
@Tag(name = "Chương truyện", description = "Đọc nội dung chương và mở khóa bằng Xu")
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterPurchaseService purchaseService;
    private final ChapterEventStream chapterEventStream;

    @GetMapping("/{id}")
    @Operation(summary = "Nội dung chương. Trả 403 hoặc 402 nếu chương bị khóa.")
    public ChapterDetailDto detail(@PathVariable Long id) {
        return chapterService.getDetail(id);
    }

    /**
     * Hỏi trước xem mở được chương này không, và nếu không thì vì sao.
     *
     * <p>Có endpoint riêng vì trang đọc cần dựng đúng màn hình <i>trước</i> khi
     * thử tải nội dung: mời đăng nhập, mời nâng cấp VIP, hay hiện nút "Mở khóa 50
     * Xu" kèm số dư hiện có. Không có nó thì cách duy nhất để biết là gọi vào
     * đường đọc rồi đọc mã lỗi trả về — dùng một lỗi làm luồng điều khiển.
     */
    @GetMapping("/{id}/access")
    @Operation(summary = "Quyền đọc chương của người đang gọi, kèm giá Xu nếu có")
    public ChapterAccessDto access(@PathVariable Long id) {
        return chapterService.accessInfo(id);
    }

    /**
     * Mở khóa chương bằng Xu.
     *
     * <p>Gọi lại trên chương đã mở là lệnh rỗng, không phải lỗi — xem
     * {@link ChapterPurchaseDto.Outcome}.
     */
    @PostMapping("/{id}/purchase")
    @Operation(summary = "Trừ Xu và mở khóa chương. Gọi trùng không trừ thêm lần nào.")
    public ChapterPurchaseDto purchase(@PathVariable Long id) {
        return purchaseService.purchase(id);
    }

    /**
     * Theo dõi chương này để biết ngay khi Admin sửa nội dung.
     *
     * <p>Không có đường này, người đang đọc chỉ phát hiện chương đã đổi ở lần
     * thao tác tiếp theo — mà một người đang nghe thì có thể không thao tác gì
     * suốt hai mươi phút. Trang đọc mở một kết nối lúc vào chương và đóng lúc rời
     * đi; thứ đi qua nó là {@code {type, chapterId, storyId, contentVersion}} và
     * không có gì khác.
     *
     * <p><b>Cố ý không đòi đăng nhập.</b> Bốn con số ấy không nói gì mà danh sách
     * chương chưa nói công khai, nên chương trả phí không lộ ra thêm chút nào.
     * Đổi lại là tránh được cả một mớ rắc rối có thật: {@code EventSource} của
     * trình duyệt không đặt được header {@code Authorization}, nên đường duy nhất
     * còn lại là nhét token vào query string — nơi nó nằm lại trong log truy cập
     * và trong lịch sử trình duyệt. Đường phát audio phải chịu điều đó vì nó chở
     * nội dung thật; đường này thì không cần.
     *
     * <p>Trả 503 khi máy chủ đã hết chỗ cho kết nối mới. Trang đọc chạy tiếp bình
     * thường, chỉ mất phần thông báo tức thời — nó vẫn phát hiện được chương đã
     * đổi ở lần gọi API kế tiếp.
     */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Luồng SSE báo khi nội dung chương đổi",
            description = "Chỉ gửi {type, chapterId, storyId, contentVersion} — không có nội dung "
                    + "chương, nên không cần đăng nhập. Trả 503 khi máy chủ hết chỗ.")
    public ResponseEntity<SseEmitter> events(@PathVariable Long id) {
        SseEmitter emitter = chapterEventStream.subscribe(id);
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(emitter);
    }
}
