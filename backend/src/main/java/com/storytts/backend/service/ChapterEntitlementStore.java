package com.storytts.backend.service;

import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.EntitlementSource;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.dto.wallet.ChapterPurchaseDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần ghi của việc mở khóa chương, mỗi thao tác một giao dịch ngắn.
 *
 * <h3>Vì sao là bean riêng chứ không phải method của {@link ChapterPurchaseService}</h3>
 * {@code @Transactional} của Spring chạy bằng proxy: một method gọi method khác
 * <i>trong cùng một bean</i> thì không đi qua proxy và annotation lặng lẽ vô hiệu.
 * Ở đây mất giao dịch nghĩa là mất tính nguyên tử giữa việc trừ Xu và việc cấp
 * quyền — tức là mở đúng cánh cửa dẫn tới trạng thái "đã mất Xu mà chưa đọc
 * được". Cùng lý do với {@code PaymentOrderLedger} và {@code TtsGenerationRecords}.
 *
 * <p>Bên gọi cần bắt được lỗi vi phạm ràng buộc duy nhất, mà lỗi ấy chỉ nổ ra khi
 * câu lệnh xuống tới cơ sở dữ liệu. Giao dịch nằm trọn trong bean này nghĩa là nó
 * đã đóng — và đã cuộn ngược nếu hỏng — trước khi ngoại lệ tới tay bên gọi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterEntitlementStore {

    private final ChapterService chapterService;
    private final ChapterAccessService chapterAccessService;
    private final ChapterEntitlementRepository entitlementRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;

    /**
     * Trừ Xu và cấp quyền, trong một giao dịch.
     *
     * <p>Hỏng ở bất cứ đâu là cuộn lại hết, kể cả số Xu đã trừ. Không có trạng
     * thái giữa chừng nào lọt ra ngoài.
     */
    @Transactional
    public ChapterPurchaseDto settle(Long chapterId, Long userId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        long price = chapter.getCoinPrice();

        if (price <= 0) {
            throw new BadRequestException("Chương này không bán bằng Xu.");
        }

        // Xét lại quyền ngay tại đường ghi thay vì tin vào lần xét ở đường đọc:
        // đây là chỗ tiền đổi chủ, và nó phải tự đứng vững.
        ChapterAccessDecision decision = chapterAccessService.decide(chapter);
        if (decision.allowed()) {
            // VIP hoặc quản trị viên: đã đọc được rồi, không có gì để bán.
            return ChapterPurchaseDto.alreadyAccessible(chapterId, walletService.balanceOf(userId));
        }
        if (!decision.purchasable()) {
            throw new ChapterLockedException(chapter.getAccessLevel(), true);
        }

        // Trừ trước, cấp quyền sau. Thứ tự không ảnh hưởng tới tính đúng đắn —
        // giao dịch bao cả hai — nhưng lỗi thường gặp nhất là hết Xu, và trừ
        // trước thì nó lộ ra ngay mà chưa phải chèn dòng nào.
        long balanceAfter = walletService.debit(
                userId, price,
                WalletTransactionType.PURCHASE_CHAPTER,
                WalletReferenceType.CHAPTER, chapterId,
                "Mở khóa: " + chapter.getTitle());

        entitlementRepository.save(ChapterEntitlement.builder()
                .user(userRepository.getReferenceById(userId))
                .chapter(chapter)
                .source(EntitlementSource.COIN_PURCHASE)
                .coinsSpent(price)
                .build());

        // Đẩy xuống cơ sở dữ liệu ngay thay vì đợi lúc commit, để lỗi ràng buộc
        // duy nhất — nếu có — nổ ra ở đây, bên trong giao dịch sẽ cuộn ngược nó.
        entitlementRepository.flush();

        log.info("Người dùng {} mở khóa chương {} với {} Xu, còn {} Xu",
                userId, chapterId, price, balanceAfter);
        return ChapterPurchaseDto.purchased(chapterId, price, balanceAfter);
    }

    /**
     * Quản trị viên mở một chương cho một người mà không tính Xu.
     *
     * <p>Không đi qua ví, và cố ý không đi: một dòng sổ cái mô tả giao dịch Xu
     * chưa từng xảy ra sẽ làm sai lệch mọi báo cáo doanh thu về sau. Quyền có thể
     * đến từ nơi khác ngoài tiền — đó chính là lý do {@link EntitlementSource}
     * tồn tại.
     */
    @Transactional
    public void grant(Long userId, Long chapterId) {
        if (entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)) {
            return;
        }
        entitlementRepository.save(ChapterEntitlement.builder()
                .user(userRepository.getReferenceById(userId))
                .chapter(chapterService.findDetailEntity(chapterId))
                .source(EntitlementSource.ADMIN_GRANT)
                .coinsSpent(0L)
                .build());
        log.info("Quản trị viên cấp quyền đọc chương {} cho người dùng {}", chapterId, userId);
    }
}
