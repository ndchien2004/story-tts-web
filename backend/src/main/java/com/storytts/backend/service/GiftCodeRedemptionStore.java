package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.GiftCodeRedemption;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.dto.gift.RedeemResultDto;
import com.storytts.backend.exception.GiftCodeException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phần ghi của việc đổi gift code: một thao tác, một giao dịch.
 *
 * <h3>Vì sao là bean riêng chứ không phải method của {@link GiftCodeService}</h3>
 * {@code @Transactional} của Spring chạy bằng proxy: một method gọi method khác
 * <i>trong cùng một bean</i> thì không đi qua proxy và annotation lặng lẽ vô
 * hiệu. Ở đây mất giao dịch nghĩa là mất tính nguyên tử giữa việc chiếm lượt,
 * việc ghi sổ đổi mã và việc cộng Xu — tức là mở đúng ba cánh cửa mà cả tính
 * năng này tồn tại để đóng lại. Cùng lý do và cùng hình dạng với
 * {@link ChapterEntitlementStore}, {@code PaymentOrderLedger}.
 *
 * <p>Và bên gọi cần bắt được lỗi vi phạm ràng buộc duy nhất, mà lỗi ấy chỉ nổ ra
 * khi câu lệnh xuống tới cơ sở dữ liệu. Giao dịch nằm trọn trong bean này nghĩa
 * là nó đã đóng — và đã cuộn ngược nếu hỏng — trước khi ngoại lệ tới tay bên gọi.
 *
 * <h3>Thứ tự ba lệnh ghi, và vì sao là thứ tự này</h3>
 * <pre>
 *   1. chiếm một lượt   UPDATE ... WHERE còn hiệu lực AND used_count &lt; max_uses
 *   2. ghi sổ đổi mã    INSERT, đụng UNIQUE(gift_code_id, user_id)
 *   3. cộng Xu          WalletService.credit — số dư + dòng sổ cái, cùng lúc
 * </pre>
 *
 * Tính đúng đắn không phụ thuộc vào thứ tự: cả ba nằm trong một giao dịch, hỏng
 * ở bất cứ đâu là cuộn lại hết. Thứ tự này được chọn vì hai lý do khác:
 *
 * <ul>
 *   <li><b>Lệnh 1 vừa kiểm vừa ghi.</b> Nó gói toàn bộ phần kiểm tra phụ thuộc
 *       thời gian và trần lượt vào một câu lệnh nguyên tử, nên lời từ chối
 *       thường gặp nhất (hết lượt, hết hạn) xảy ra trước khi có dòng nào được
 *       chèn.</li>
 *   <li><b>Mọi giao dịch khóa các dòng theo cùng một thứ tự</b> — mã trước, ví
 *       sau. Thứ tự khóa nhất quán là thứ khiến hàng nghìn request đồng thời xếp
 *       hàng thay vì bế tắc lẫn nhau.</li>
 * </ul>
 *
 * <p>Cái giá là dòng {@code gift_codes} bị khóa cho tới hết giao dịch, kể cả
 * trong lúc cộng Xu. Đó không phải một lựa chọn có thể tránh: cột đếm lượt
 * <i>phải</i> bị giữ tới lúc commit, nếu không thì một lượt đã chiếm có thể được
 * trả lại sau khi một request khác đã đọc con số cũ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GiftCodeRedemptionStore {

    private final GiftCodeRepository giftCodeRepository;
    private final GiftCodeRedemptionRepository redemptionRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;

    /**
     * Chiếm một lượt, ghi sổ, cộng Xu — hoặc không làm gì cả.
     *
     * <p>Hỏng ở bất cứ đâu là cuộn lại hết, kể cả lượt vừa chiếm và Xu vừa cộng.
     * Không có trạng thái giữa chừng nào lọt ra ngoài, nên bốn thứ này luôn khớp
     * nhau: số dư ví, dòng sổ cái Xu, dòng sổ đổi mã, và cột đếm lượt.
     *
     * @param code mã <b>đã chuẩn hóa</b>, đã được bên gọi tra ra là có thật
     * @throws GiftCodeException nếu mã không đổi được lúc này
     */
    @Transactional
    public RedeemResultDto redeem(GiftCode code, Long userId) {
        Long codeId = code.getId();
        Instant now = Instant.now();

        // Đọc ra biến cục bộ *trước* câu UPDATE bên dưới: nó chạy với
        // clearAutomatically, nên sau nó entity này không còn gắn với phiên nào.
        long coinAmount = code.getCoinAmount();
        String label = code.getCode();

        // (1) Chiếm một lượt. Câu lệnh này vừa kiểm tra vừa ghi, và điều kiện của
        //     nó bao cả cờ bật/tắt lẫn hai mốc thời gian — nên một mã hết hạn
        //     *giữa* lúc tra và lúc ghi cũng không lọt qua được.
        if (giftCodeRepository.claimUse(codeId, now) == 0) {
            // 0 dòng nghĩa là một trong bốn: tắt, chưa tới giờ, hết hạn, hết lượt.
            // Đọc lại chỉ để biết là cái nào mà nói cho người dùng; việc từ chối
            // đã được quyết định bởi câu lệnh trên, không bởi lần đọc này.
            GiftCode fresh = giftCodeRepository.findById(codeId).orElse(code);
            throw new GiftCodeException(
                    GiftCodeException.Reason.forStatus(fresh.status(now)));
        }

        // (2) Ghi sổ đổi mã. Đây là chỗ UNIQUE(gift_code_id, user_id) chặn lần
        //     đổi thứ hai của cùng một người — kể cả lần đến từ một request song
        //     song mà phép kiểm ở GiftCodeService chưa thể nhìn thấy.
        redemptionRepository.save(GiftCodeRedemption.builder()
                .giftCode(giftCodeRepository.getReferenceById(codeId))
                .user(userRepository.getReferenceById(userId))
                // Chép lại mệnh giá tại thời điểm đổi: quản trị viên sửa được
                // mệnh giá của một mã đang chạy, và số Xu đã vào ví một người thì
                // không được đổi theo.
                .coinAmount(coinAmount)
                .build());

        // Đẩy xuống cơ sở dữ liệu ngay thay vì đợi lúc commit, để lỗi ràng buộc
        // duy nhất — nếu có — nổ ra ở đây, bên trong giao dịch sẽ cuộn ngược nó,
        // và trước khi ví bị đụng tới.
        redemptionRepository.flush();

        // (3) Cộng Xu. Đi qua WalletService như mọi đường tiền khác: nó ghi số dư
        //     và dòng sổ cái cùng nhau, và nó nhập vào chính giao dịch này thay
        //     vì mở một giao dịch riêng — xem ghi chú về propagation ở lớp ấy.
        long balance = walletService.credit(
                userId, coinAmount,
                WalletTransactionType.GIFT_CODE,
                WalletReferenceType.GIFT_CODE, codeId,
                "Đổi gift code " + label);

        log.info("Người dùng {} đổi gift code {} (id {}) nhận {} Xu, còn {} Xu",
                userId, label, codeId, coinAmount, balance);
        return new RedeemResultDto(label, coinAmount, balance);
    }
}
