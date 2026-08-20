package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.gift.GiftCodeDetailDto;
import com.storytts.backend.dto.gift.GiftCodeDto;
import com.storytts.backend.dto.gift.GiftCodeRedemptionDto;
import com.storytts.backend.dto.gift.GiftCodeRequest;
import com.storytts.backend.dto.gift.GiftCodeStatsDto;
import com.storytts.backend.service.GiftCodeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

/**
 * Quản lý gift code.
 *
 * <p>Nằm dưới {@code /api/admin/**}, nên {@code SecurityConfig} đã đòi
 * {@code hasRole("ADMIN")} cho toàn bộ lớp này ở tầng URL — trước khi có
 * controller nào chạy. Không có kiểm tra quyền nào lặp lại trong thân các
 * phương thức: hai chỗ kiểm cùng một điều là hai chỗ có thể lệch nhau, và chỗ
 * duy nhất mọi request đều đi qua là tầng filter.
 *
 * <p>Việc giấu nút bên frontend không tham gia vào phần bảo vệ này; nó chỉ khiến
 * bảng quản trị gọn hơn cho người không dùng tới.
 */
@RestController
@RequestMapping("/api/admin/gift-codes")
@RequiredArgsConstructor
@Tag(name = "Admin - Gift code", description = "Tạo, sửa, bật/tắt gift code và xem lượt đổi")
public class AdminGiftCodeController {

    /** Chặn một tham số {@code size} quá lớn biến một trang thành cả bảng. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Cột được phép sắp — danh sách trắng, không phải chuỗi client gửi sao dùng vậy. */
    private static final Set<String> SORTABLE =
            Set.of("createdAt", "code", "coinAmount", "usedCount", "startAt", "endAt");

    private final GiftCodeAdminService giftCodeAdminService;

    /* ----- Danh sách và thống kê ----- */

    /**
     * Bảng gift code.
     *
     * @param status tên một tình trạng (ACTIVE, SCHEDULED, EXPIRED, DISABLED,
     *               EXHAUSTED); bỏ trống là không lọc
     * @param from   lọc theo <i>ngày tạo</i>, không theo hạn dùng
     * @param sort   tên cột, phải thuộc {@link #SORTABLE}
     */
    @GetMapping
    @Operation(summary = "Danh sách gift code, lọc theo mã, tình trạng và ngày tạo")
    public PageResponse<GiftCodeDto> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "createdAt") String sort,
                                          @RequestParam(defaultValue = "desc") String direction,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        // Tên cột đi thẳng vào ORDER BY, nên nó phải đến từ một danh sách đóng.
        // Một chuỗi tùy ý ở đây là một lỗi 500 cho mọi giá trị không phải tên
        // thuộc tính, và là một cửa để dò cấu trúc entity.
        String column = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction way = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return giftCodeAdminService.list(keyword, status, from, to,
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(way, column)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Tổng số mã, số mã đang phát, tổng lượt đổi và tổng Xu đã phát")
    public GiftCodeStatsDto stats() {
        return giftCodeAdminService.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Một gift code, kèm số lượt đổi và tổng Xu đã phát")
    public GiftCodeDetailDto detail(@PathVariable Long id) {
        return giftCodeAdminService.detail(id);
    }

    /** Có phân trang, và cố ý bắt buộc: một mã sự kiện có thể có hàng nghìn lượt. */
    @GetMapping("/{id}/redemptions")
    @Operation(summary = "Danh sách tài khoản đã đổi một gift code, mới nhất trước")
    public PageResponse<GiftCodeRedemptionDto> redemptions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return giftCodeAdminService.redemptions(id,
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /* ----- Ghi ----- */

    @PostMapping
    @Operation(summary = "Tạo gift code")
    public GiftCodeDto create(@Valid @RequestBody GiftCodeRequest request) {
        return giftCodeAdminService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa gift code")
    public GiftCodeDto update(@PathVariable Long id,
                              @Valid @RequestBody GiftCodeRequest request) {
        return giftCodeAdminService.update(id, request);
    }

    /** Tách riêng khỏi PUT: dừng gấp một đợt đang chạy không nên đòi điền lại cả biểu mẫu. */
    @PatchMapping("/{id}/enabled")
    @Operation(summary = "Bật hoặc tắt một gift code")
    public GiftCodeDto setEnabled(@PathVariable Long id,
                                  @Valid @RequestBody EnabledRequest request) {
        return giftCodeAdminService.setEnabled(id, request.enabled());
    }

    /**
     * Xóa một mã <b>chưa ai đổi</b>.
     *
     * <p>Có người đổi rồi thì máy chủ từ chối và bảo tắt mã — lịch sử Xu của họ
     * trỏ về nó. Xem {@code GiftCodeAdminService.delete}.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa một gift code chưa ai đổi")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        giftCodeAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sinh một mã ngẫu nhiên chưa dùng.
     *
     * <p>POST chứ không GET dù nó không ghi gì: mỗi lần gọi trả về một kết quả
     * khác, nên nó không phải một tài nguyên đọc được và không nên bị bộ nhớ đệm
     * nào giữ lại.
     */
    @PostMapping("/generate")
    @Operation(summary = "Sinh một gift code ngẫu nhiên chưa tồn tại")
    public GeneratedCode generate(@RequestBody(required = false) GenerateRequest request) {
        return new GeneratedCode(
                giftCodeAdminService.generateCode(request == null ? null : request.prefix()));
    }

    public record EnabledRequest(@NotNull(message = "Thiếu trạng thái") Boolean enabled) {
    }

    public record GenerateRequest(
            @Size(max = 24, message = "Tiền tố tối đa 24 ký tự") String prefix) {
    }

    public record GeneratedCode(String code) {
    }
}
