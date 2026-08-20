package com.storytts.backend.dto.gift;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.GiftCodeStatus;

import java.time.Instant;

/**
 * Một dòng trong bảng gift code của khu quản trị.
 *
 * <p>{@code status} được tính ở máy chủ chứ không để giao diện tự suy từ bốn
 * trường thời gian và hai con số. Suy ở hai nơi là hai bộ quy tắc phải giữ cho
 * khớp nhau, và bộ ở trình duyệt sẽ dùng đồng hồ của máy người xem — một chiếc
 * máy đặt sai giờ sẽ hiện "Đang phát" cho một mã mà máy chủ từ chối.
 *
 * <p>Mọi mốc thời gian là ISO-8601 theo UTC, như mọi endpoint khác của dự án.
 * Việc đổi sang giờ Việt Nam xảy ra đúng một lần, ở trình duyệt, lúc hiển thị.
 *
 * @param maxUses       null nghĩa là không giới hạn lượt
 * @param remainingUses null khi {@code maxUses} null
 */
public record GiftCodeDto(
        Long id,
        String code,
        long coinAmount,
        Instant startAt,
        Instant endAt,
        Integer maxUses,
        int usedCount,
        Integer remainingUses,
        boolean enabled,
        String status,
        String statusLabel,
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static GiftCodeDto from(GiftCode code, Instant now) {
        GiftCodeStatus status = code.status(now);
        return new GiftCodeDto(
                code.getId(),
                code.getCode(),
                code.getCoinAmount(),
                code.getStartAt(),
                code.getEndAt(),
                code.getMaxUses(),
                code.getUsedCount(),
                code.remainingUses(),
                code.isEnabled(),
                status.name(),
                status.getLabel(),
                code.getDescription(),
                // Người tạo là quan hệ lazy và có thể null; chỉ lấy tên để bảng
                // quản trị không phải nạp cả một tài khoản cho mỗi dòng.
                code.getCreatedBy() == null ? null : code.getCreatedBy().getUsername(),
                code.getCreatedAt(),
                code.getUpdatedAt());
    }
}
