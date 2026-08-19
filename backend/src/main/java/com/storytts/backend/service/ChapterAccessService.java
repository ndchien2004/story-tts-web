package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.exception.ChapterPurchaseRequiredException;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.repository.WalletRepository;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * <h2>Cửa quyền đọc chương — bản đầy đủ, có tính tới Xu</h2>
 *
 * Lớp này bọc {@link AccessControlService} chứ không thay thế nó, và sự phân
 * chia ấy có lý do: lớp kia là <i>logic thuần</i>, không chạm cơ sở dữ liệu,
 * trả lời câu "cấp bậc của người này có với tới chương này không". Lớp này thêm
 * vào đó câu hỏi cần đọc đĩa: "người này đã mua nó chưa".
 *
 * <h3>Luồng quyết định</h3>
 * <pre>
 *   Quản trị viên?                        → cho qua
 *
 *   Chương giá 0 (mọi chương hiện có):
 *       → giao lại nguyên vẹn cho AccessControlService
 *
 *   Chương có giá:
 *       chưa đăng nhập?                   → mời đăng nhập
 *       VIP còn hạn?                      → cho qua, không tính tiền
 *       đã mua rồi?                       → cho qua
 *       còn lại                           → mời mở khóa bằng Xu
 * </pre>
 *
 * <h3>Vì sao VIP đọc được cả chương có giá</h3>
 * Đây là lựa chọn nghiệp vụ, và là lựa chọn đơn giản nhất để nói thành lời: "VIP
 * đọc được mọi thứ". Nó khiến VIP luôn đáng giá hơn khi có thêm chương tính phí,
 * chứ không phải rẻ đi. Phương án ngược lại — VIP chỉ được giảm giá Xu — cần một
 * bảng giá hai chiều và một câu giải thích dài hơn hẳn ở phía người mua.
 *
 * <h3>Điều quan trọng nhất về lớp này</h3>
 * Nhánh "giá 0" đi đúng đường cũ, không sửa một dòng nào của nó. Mọi chương đang
 * có đều mang giá 0. Nghĩa là tính năng Xu <b>không thể</b> làm đổi hành vi của
 * một chương nào đang chạy; đường tính tiền chỉ mở ra ở chương mà quản trị viên
 * chủ động đặt giá.
 */
@Service
@RequiredArgsConstructor
public class ChapterAccessService {

    private final AccessControlService accessControlService;
    private final ChapterEntitlementRepository entitlementRepository;
    private final WalletRepository walletRepository;
    private final CurrentUserService currentUserService;
    private final PublicationService publicationService;

    /**
     * Xét quyền và trả về cả lý do.
     *
     * @see ChapterAccessDecision
     */
    @Transactional(readOnly = true)
    public ChapterAccessDecision decide(Chapter chapter) {
        // Chỉ hỏi cơ sở dữ liệu khi câu trả lời có thể đổi kết quả: chương không
        // có giá thì việc đã mua hay chưa không liên quan tới gì cả.
        boolean owned = chapter.getCoinPrice() > 0
                && currentUserService.currentUserId()
                        .map(userId -> entitlementRepository
                                .existsByUserIdAndChapterId(userId, chapter.getId()))
                        .orElse(false);
        return decide(chapter, owned);
    }

    /**
     * Cùng quyết định, nhưng nhận sẵn "đã mua chưa" thay vì tự đi hỏi.
     *
     * <p>Dành cho danh sách chương: một truyện hai trăm chương mà mỗi dòng tự hỏi
     * một câu là hai trăm câu truy vấn cho một lần mở trang. Bên gọi lấy trọn tập
     * quyền bằng {@link #ownedAmong} rồi truyền vào đây.
     *
     * <p>Không chạm cơ sở dữ liệu, nên cũng là phiên bản dễ kiểm thử nhất.
     */
    public ChapterAccessDecision decide(Chapter chapter, boolean owned) {
        Optional<AppUserPrincipal> principal = currentUserService.currentPrincipal();

        if (principal.map(AppUserPrincipal::isAdmin).orElse(false)) {
            return ChapterAccessDecision.ALLOWED_ADMIN;
        }

        AccessLevel level = chapter.getAccessLevel() == null
                ? AccessLevel.PUBLIC : chapter.getAccessLevel();

        if (chapter.getCoinPrice() <= 0) {
            return withoutPricing(level, principal);
        }

        // Từ đây là chương có giá Xu.
        if (principal.isEmpty()) {
            return ChapterAccessDecision.DENIED_LOGIN_REQUIRED;
        }
        if (principal.get().isVip()) {
            return ChapterAccessDecision.ALLOWED_VIP;
        }
        if (owned) {
            return ChapterAccessDecision.ALLOWED_PURCHASED;
        }
        return ChapterAccessDecision.DENIED_COINS_REQUIRED;
    }

    /** Số dư Xu của người đang gọi; 0 với khách chưa đăng nhập. */
    @Transactional(readOnly = true)
    public long balanceOfCaller() {
        return currentBalance();
    }

    /** Đúng hành vi trước khi có Xu, giao lại cho lớp logic thuần. */
    private ChapterAccessDecision withoutPricing(AccessLevel level,
                                                 Optional<AppUserPrincipal> principal) {
        if (accessControlService.canAccess(level, principal)) {
            return switch (level) {
                case PUBLIC -> ChapterAccessDecision.ALLOWED_FREE;
                case MEMBER -> ChapterAccessDecision.ALLOWED_MEMBER;
                case VIP -> ChapterAccessDecision.ALLOWED_VIP;
            };
        }
        return principal.isEmpty()
                ? ChapterAccessDecision.DENIED_LOGIN_REQUIRED
                : ChapterAccessDecision.DENIED_VIP_REQUIRED;
    }

    /**
     * Chặn cứng. Nội dung chương không bao giờ rời khỏi máy chủ khi lối này ném.
     *
     * @throws ChapterLockedException           403 — thiếu cấp bậc, không mua được
     * @throws ChapterPurchaseRequiredException 402 — mua được bằng Xu
     */
    public void requireAccess(Chapter chapter) {
        // Trước cả câu hỏi quyền: thứ chưa đăng thì coi như chưa tồn tại. Đặt ở
        // đây vì đây là cửa mà *mọi* đường vào nội dung chương đều đi qua — đọc
        // chữ, phát audio, dựng audio, hỏi trợ lý. Một bản nháp lọt ra qua đường
        // phát audio cũng là một bản nháp đã công bố.
        publicationService.requireChapterVisible(chapter);

        ChapterAccessDecision decision = decide(chapter);
        if (decision.allowed()) {
            return;
        }

        if (decision.purchasable()) {
            throw new ChapterPurchaseRequiredException(chapter.getCoinPrice(), currentBalance());
        }
        throw new ChapterLockedException(
                chapter.getAccessLevel(), currentUserService.isAuthenticated());
    }

    /**
     * Trong danh sách chương này, người đang gọi đã mở những chương nào.
     *
     * <p>Một câu truy vấn cho cả trang chi tiết truyện. Không có nó thì danh sách
     * chương sinh một câu hỏi quyền cho mỗi dòng, và một truyện hai trăm chương
     * là hai trăm câu.
     */
    @Transactional(readOnly = true)
    public Set<Long> ownedAmong(Collection<Long> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return Set.of();
        }
        return currentUserService.currentUserId()
                .map(userId -> Set.copyOf(entitlementRepository.findChapterIdsOwnedBy(userId, chapterIds)))
                .orElseGet(Set::of);
    }

    /**
     * Số dư Xu của người đang gọi, chỉ dùng để dựng thông báo lỗi cho đủ nghĩa.
     *
     * <p>Đọc thẳng {@code WalletRepository} chứ không qua {@code WalletService}:
     * ở đây chỉ cần một con số, không cần phần cộng trừ và ghi sổ cái của lớp ấy.
     */
    private long currentBalance() {
        return currentUserService.currentUserId()
                .flatMap(walletRepository::findBalanceByUserId)
                .orElse(0L);
    }
}
