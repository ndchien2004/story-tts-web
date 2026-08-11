package com.storytts.backend.dto.vip;

import com.storytts.backend.domain.VipPlan;

/** Một gói VIP như trang nâng cấp và bảng quản trị nhìn thấy. */
public record VipPlanDto(
        Long id,
        String name,
        int months,
        long priceVnd,
        long pricePerMonth,
        String description,
        boolean active,
        int sortOrder
) {

    public static VipPlanDto from(VipPlan plan) {
        return new VipPlanDto(
                plan.getId(),
                plan.getName(),
                plan.getMonths(),
                plan.getPriceVnd(),
                plan.pricePerMonth(),
                plan.getDescription(),
                plan.isActive(),
                plan.getSortOrder());
    }
}
