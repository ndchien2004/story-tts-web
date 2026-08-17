package com.storytts.backend.service;

import com.storytts.backend.domain.CoinPackage;
import com.storytts.backend.dto.wallet.CoinPackageDto;
import com.storytts.backend.dto.wallet.CoinPackageRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.CoinPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Các gói nạp Xu đem bán.
 *
 * <p>Cùng hình dạng và cùng nguyên tắc với {@link VipPlanService}: tỉ lệ quy đổi
 * là dữ liệu do quản trị viên nhập, không nằm trong mã nguồn. Đổi "10.000đ → 100
 * Xu" thành "10.000đ → 120 Xu" dịp lễ là một thao tác trong bảng quản trị.
 *
 * <p>Gói đã bán không xóa được — đơn hàng trỏ tới nó và người mua có quyền xem
 * lại mình đã mua gì. Tắt gói là cách đúng để ngừng bán mà không viết lại lịch sử.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoinPackageService {

    private final CoinPackageRepository packageRepository;

    /** Danh sách bán ra cho người đọc. */
    @Transactional(readOnly = true)
    public List<CoinPackageDto> listActive() {
        return packageRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
                .stream().map(CoinPackageDto::from).toList();
    }

    /** Danh sách đầy đủ cho bảng quản trị, gồm cả gói đã tắt. */
    @Transactional(readOnly = true)
    public List<CoinPackageDto> listAll() {
        return packageRepository.findAllByOrderBySortOrderAscIdAsc()
                .stream().map(CoinPackageDto::from).toList();
    }

    @Transactional
    public CoinPackageDto create(CoinPackageRequest request) {
        CoinPackage pack = CoinPackage.builder()
                .name(request.name().trim())
                .priceVnd(request.priceVnd())
                .coins(request.coins())
                .bonusCoins(request.bonusCoins() == null ? 0L : request.bonusCoins())
                .description(blankToNull(request.description()))
                .active(request.active() == null || request.active())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .build();

        log.info("Tạo gói Xu {} — {}đ đổi {} Xu", pack.getName(), pack.getPriceVnd(), pack.totalCoins());
        return CoinPackageDto.from(packageRepository.save(pack));
    }

    @Transactional
    public CoinPackageDto update(Long id, CoinPackageRequest request) {
        CoinPackage pack = findPackage(id);

        pack.setName(request.name().trim());
        pack.setPriceVnd(request.priceVnd());
        pack.setCoins(request.coins());
        pack.setBonusCoins(request.bonusCoins() == null ? 0L : request.bonusCoins());
        pack.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            pack.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            pack.setSortOrder(request.sortOrder());
        }

        return CoinPackageDto.from(packageRepository.save(pack));
    }

    /** Ngừng bán mà vẫn giữ nguyên các đơn đã phát sinh từ gói này. */
    @Transactional
    public CoinPackageDto setActive(Long id, boolean active) {
        CoinPackage pack = findPackage(id);
        pack.setActive(active);
        return CoinPackageDto.from(packageRepository.save(pack));
    }

    @Transactional(readOnly = true)
    public CoinPackage requireActivePackage(Long id) {
        CoinPackage pack = findPackage(id);
        if (!pack.isActive()) {
            throw new BadRequestException("Gói nạp này hiện không bán nữa.");
        }
        return pack;
    }

    private CoinPackage findPackage(Long id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói Xu với id " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
