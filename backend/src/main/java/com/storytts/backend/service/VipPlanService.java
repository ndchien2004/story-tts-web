package com.storytts.backend.service;

import com.storytts.backend.domain.VipPlan;
import com.storytts.backend.dto.vip.VipPlanDto;
import com.storytts.backend.dto.vip.VipPlanRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Các gói VIP đem bán.
 *
 * <p>Toàn bộ là dữ liệu do Admin nhập: kỳ hạn và giá không nằm trong mã nguồn,
 * nên mở thêm gói "6 tháng" hay đổi giá dịp lễ chỉ là một thao tác trong bảng
 * quản trị.
 *
 * <p>Gói đã bán không xóa được. Đơn hàng trỏ tới nó và người dùng có quyền xem
 * lại mình đã mua gì; tắt gói là cách đúng để ngừng bán mà không viết lại lịch
 * sử.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VipPlanService {

    private final VipPlanRepository planRepository;

    /** Danh sách bán ra cho người đọc. */
    @Transactional(readOnly = true)
    public List<VipPlanDto> listActive() {
        return planRepository.findByActiveTrueOrderBySortOrderAscPriceVndAsc()
                .stream().map(VipPlanDto::from).toList();
    }

    /** Danh sách đầy đủ cho bảng quản trị, gồm cả gói đã tắt. */
    @Transactional(readOnly = true)
    public List<VipPlanDto> listAll() {
        return planRepository.findAllByOrderBySortOrderAscPriceVndAsc()
                .stream().map(VipPlanDto::from).toList();
    }

    @Transactional
    public VipPlanDto create(VipPlanRequest request) {
        if (planRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BadRequestException("Đã có gói tên “" + request.name().trim() + "”.");
        }

        VipPlan plan = VipPlan.builder()
                .name(request.name().trim())
                .months(request.months())
                .priceVnd(request.priceVnd())
                .description(blankToNull(request.description()))
                .active(request.active())
                .sortOrder(request.sortOrder())
                .build();

        log.info("Tạo gói VIP {} — {} tháng, {}đ", plan.getName(), plan.getMonths(), plan.getPriceVnd());
        return VipPlanDto.from(planRepository.save(plan));
    }

    @Transactional
    public VipPlanDto update(Long id, VipPlanRequest request) {
        VipPlan plan = findPlan(id);

        String name = request.name().trim();
        if (!plan.getName().equalsIgnoreCase(name) && planRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Đã có gói tên “" + name + "”.");
        }

        plan.setName(name);
        plan.setMonths(request.months());
        plan.setPriceVnd(request.priceVnd());
        plan.setDescription(blankToNull(request.description()));
        plan.setActive(request.active());
        plan.setSortOrder(request.sortOrder());

        return VipPlanDto.from(planRepository.save(plan));
    }

    /** Ngừng bán mà vẫn giữ nguyên các đơn đã phát sinh từ gói này. */
    @Transactional
    public VipPlanDto setActive(Long id, boolean active) {
        VipPlan plan = findPlan(id);
        plan.setActive(active);
        return VipPlanDto.from(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public VipPlan requireActivePlan(Long id) {
        VipPlan plan = findPlan(id);
        if (!plan.isActive()) {
            throw new BadRequestException("Gói này hiện không bán nữa.");
        }
        return plan;
    }

    private VipPlan findPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói VIP với id " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
