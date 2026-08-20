package com.storytts.backend.repository;

import com.storytts.backend.domain.GiftCodeRedemption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Sổ đổi mã: ai đã đổi mã nào, khi nào, nhận bao nhiêu.
 *
 * <p>Mọi phép đếm ở đây là để <i>báo cáo</i>, không để <i>quyết định</i>. Quyết
 * định "người này đã đổi chưa" thuộc về ràng buộc
 * {@code UNIQUE(gift_code_id, user_id)}; {@link #existsByGiftCodeIdAndUserId}
 * chỉ là đường nhanh để trả lời đẹp cho trường hợp thường gặp, chứ không phải
 * chốt chặn. Xem {@code GiftCodeRedemption}.
 */
public interface GiftCodeRedemptionRepository extends JpaRepository<GiftCodeRedemption, Long> {

    /** Đường nhanh, không phải chốt chặn — xem ghi chú ở đầu lớp. */
    boolean existsByGiftCodeIdAndUserId(Long giftCodeId, Long userId);

    /** Danh sách người đã đổi một mã, cho bảng quản trị. Có phân trang. */
    @Query("""
            SELECT r FROM GiftCodeRedemption r
              JOIN FETCH r.user
             WHERE r.giftCode.id = :giftCodeId
            """)
    Page<GiftCodeRedemption> findByGiftCode(@Param("giftCodeId") Long giftCodeId, Pageable pageable);

    long countByGiftCodeId(Long giftCodeId);

    /**
     * Tổng Xu một mã đã phát ra.
     *
     * <p>Cộng {@code coin_amount} của từng dòng chứ không nhân mệnh giá với số
     * lượt: mệnh giá sửa được giữa chừng, và những lượt đã đổi trước đó nhận số
     * cũ. Đây là con số duy nhất đúng.
     */
    @Query("""
            SELECT COALESCE(SUM(r.coinAmount), 0) FROM GiftCodeRedemption r
             WHERE r.giftCode.id = :giftCodeId
            """)
    long sumCoinsByGiftCode(@Param("giftCodeId") Long giftCodeId);

    /** Tổng Xu toàn bộ gift code đã phát ra, cho trang tổng quan. */
    @Query("SELECT COALESCE(SUM(r.coinAmount), 0) FROM GiftCodeRedemption r")
    long sumAllCoins();
}
