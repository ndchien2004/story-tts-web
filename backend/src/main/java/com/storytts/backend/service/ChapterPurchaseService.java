package com.storytts.backend.service;

import com.storytts.backend.dto.wallet.ChapterPurchaseDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Mở một chương bằng Xu.
 *
 * <h3>Ba điều phải đúng cùng lúc</h3>
 * <ol>
 *   <li><b>Không trừ Xu hai lần.</b> Người dùng bấm ba lần, hoặc trình duyệt tự
 *       gửi lại request, chỉ được mất tiền một lần.</li>
 *   <li><b>Không tiêu quá số dư.</b> Hai chương mua song song với số Xu chỉ đủ
 *       cho một chương thì đúng một chương được mở.</li>
 *   <li><b>Trừ tiền và cấp quyền cùng sống hoặc cùng chết.</b> Không có trạng
 *       thái "đã mất Xu mà chưa đọc được".</li>
 * </ol>
 *
 * <h3>Cả ba đều do cơ sở dữ liệu bảo đảm, không phải do mã nguồn kiểm tra</h3>
 * Kiểm tra trước bằng Java không giải quyết được gì khi hai request chạy song
 * song: cả hai đều thấy "chưa mua" và "còn đủ Xu" trước khi bên nào kịp ghi. Nên
 * mỗi điều ở trên tựa vào một cơ chế của cơ sở dữ liệu:
 *
 * <ul>
 *   <li>{@code UNIQUE(user_id, chapter_id)} — dòng quyền thứ hai bị từ chối.</li>
 *   <li>{@code UPDATE ... WHERE balance >= :giá} — lệnh trừ thứ hai sửa 0 dòng.</li>
 *   <li>Một giao dịch ngắn bao cả hai lệnh ghi — hỏng bất cứ đâu là cuộn lại
 *       hết, kể cả Xu đã trừ.</li>
 * </ul>
 *
 * <p>Phần kiểm tra bằng Java vẫn còn, nhưng vai trò của nó chỉ là trả lời nhanh
 * và trả lời đẹp cho trường hợp thường gặp. Phần bảo đảm nằm ở dưới.
 *
 * <p>Lớp này là bên điều phối và <b>không mở giao dịch nào</b>; phần ghi nằm ở
 * {@link ChapterEntitlementStore}. Nhờ vậy khối {@code catch} bên dưới bắt được
 * lỗi ràng buộc <i>sau khi</i> giao dịch đã cuộn ngược xong.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterPurchaseService {

    private final ChapterEntitlementStore store;
    private final ChapterEntitlementRepository entitlementRepository;
    private final WalletService walletService;
    private final CurrentUserService currentUserService;

    /**
     * Người đọc bấm "Mở khóa bằng Xu".
     *
     * <p>Gọi lại trên một chương đã mở là lệnh rỗng, không phải lỗi: người bấm hai
     * lần muốn đọc chương, không muốn một thông báo lỗi.
     */
    public ChapterPurchaseDto purchase(Long chapterId) {
        AppUserPrincipal principal = currentUserService.currentPrincipal()
                .orElseThrow(() -> new BadRequestException("Bạn cần đăng nhập để mở khóa chương."));
        Long userId = principal.getId();

        // Đường nhanh cho trường hợp thường gặp nhất: đã mua rồi. Không phải chốt
        // chặn — chốt chặn là ràng buộc duy nhất bên dưới — mà là cách tránh ném
        // một ngoại lệ ràng buộc cho một thao tác hoàn toàn bình thường.
        if (entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)) {
            return ChapterPurchaseDto.alreadyOwned(chapterId, walletService.balanceOf(userId));
        }

        try {
            return store.settle(chapterId, userId);
        } catch (DataIntegrityViolationException ex) {
            // Thua cuộc đua với chính mình: một request song song vừa cấp quyền
            // cho đúng chương này. Giao dịch vừa rồi đã cuộn ngược nguyên vẹn nên
            // Xu không bị trừ hai lần — và kết quả người dùng nhận được vẫn là
            // điều họ muốn: chương đã mở.
            log.info("Mua trùng chương {} của người dùng {} — quyền đã được cấp bởi request khác",
                    chapterId, userId);
            return ChapterPurchaseDto.alreadyOwned(chapterId, walletService.balanceOf(userId));
        }
    }

    /** Quản trị viên cấp quyền đọc một chương mà không tính Xu của ai. */
    public void grant(Long userId, Long chapterId) {
        store.grant(userId, chapterId);
    }
}
