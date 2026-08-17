package com.storytts.backend.controller;

import com.storytts.backend.dto.chapter.ChapterAccessDto;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.wallet.ChapterPurchaseDto;
import com.storytts.backend.service.ChapterPurchaseService;
import com.storytts.backend.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
