package com.storytts.backend.dto.wallet;

import com.storytts.backend.domain.CoinPackage;

/**
 * Một gói nạp Xu trên trang nạp tiền.
 *
 * @param coins      số Xu cơ bản
 * @param bonusCoins Xu tặng thêm; tách riêng để giao diện nói được "500 + 50 tặng"
 * @param totalCoins số Xu thực nhận — tính sẵn ở máy chủ để hai bên không bao giờ
 *                   hiển thị hai con số khác nhau cho cùng một gói
 */
public record CoinPackageDto(
        Long id,
        String name,
        long priceVnd,
        long coins,
        long bonusCoins,
        long totalCoins,
        String description,
        boolean active,
        int sortOrder
) {

    public static CoinPackageDto from(CoinPackage pack) {
        return new CoinPackageDto(
                pack.getId(),
                pack.getName(),
                pack.getPriceVnd(),
                pack.getCoins(),
                pack.getBonusCoins(),
                pack.totalCoins(),
                pack.getDescription(),
                pack.isActive(),
                pack.getSortOrder());
    }
}
