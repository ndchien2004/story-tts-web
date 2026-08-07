package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.auth.UserDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/** Quản lý thành viên và cấp/thu hồi VIP thủ công. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Thành viên", description = "Danh sách thành viên, cấp/thu hồi VIP")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(summary = "Danh sách thành viên, tìm theo username hoặc email")
    public PageResponse<UserDto> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return userAdminService.list(keyword,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PatchMapping("/{id}/vip")
    @Operation(summary = "Cấp hoặc thu hồi quyền VIP cho một thành viên")
    public UserDto setVip(@PathVariable Long id, @Valid @RequestBody FlagRequest request) {
        return userAdminService.setVip(id, request.value());
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "Khóa hoặc mở khóa tài khoản")
    public UserDto setEnabled(@PathVariable Long id, @Valid @RequestBody FlagRequest request) {
        return userAdminService.setEnabled(id, request.value());
    }

    public record FlagRequest(@NotNull(message = "Thiếu giá trị") Boolean value) {
    }
}
