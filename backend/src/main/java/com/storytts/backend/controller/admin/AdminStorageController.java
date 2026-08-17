package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.admin.StorageAuditDto;
import com.storytts.backend.service.StorageAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đối chiếu cơ sở dữ liệu với nơi lưu file.
 *
 * <p>Cả hai đường ở đây đều đi qua {@code /api/admin/**}, tức đã nằm sau
 * {@code hasRole("ADMIN")} trong SecurityConfig. Chúng đọc trạng thái toàn hệ
 * thống và tiêu một lượt gọi mạng cho mỗi bản ghi, nên không phải thứ để mở cho
 * ai khác.
 */
@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
@Tag(name = "Quản trị - Lưu trữ", description = "Đối chiếu cơ sở dữ liệu với nơi lưu file audio")
public class AdminStorageController {

    private final StorageAuditService storageAuditService;

    @GetMapping("/audit")
    @Operation(summary = "Tìm những bản ghi trỏ tới file không còn tồn tại",
            description = "Chạy tay, không theo lịch: mỗi bản ghi tốn một lượt hỏi nơi lưu trữ. "
                    + "Chỉ đọc, không sửa gì.")
    public StorageAuditDto audit() {
        return storageAuditService.audit();
    }

    @PostMapping("/repair")
    @Operation(summary = "Đánh dấu các bản audio mất file là hỏng",
            description = "Sau bước này, chương liên quan dựng lại audio được bằng nút "
                    + "'Nghe bằng AI' — hàng READY trỏ vào hư không vốn chặn mất đường ấy. "
                    + "Không xóa gì, và không đụng tới nhạc nền.")
    public StorageAuditDto repair() {
        return storageAuditService.repairMissingAudio();
    }
}
