package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.dto.gift.RedeemResultDto;
import com.storytts.backend.exception.GiftCodeException;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Người đọc đổi một gift code lấy Xu.
 *
 * <h3>Bốn điều phải đúng cùng lúc</h3>
 * <ol>
 *   <li><b>Một tài khoản đổi một mã đúng một lần.</b> Bấm ba lần, mở hai tab,
 *       hay trình duyệt tự gửi lại request — chỉ được cộng Xu một lần.</li>
 *   <li><b>Không vượt quá {@code maxUses}.</b> Một nghìn người cùng gõ một mã
 *       giới hạn 100 lượt thì đúng 100 người nhận được.</li>
 *   <li><b>Cộng Xu, ghi sổ đổi mã và tăng cột đếm cùng sống hoặc cùng chết.</b>
 *       Không có trạng thái "đã trừ lượt mà chưa nhận Xu", và cũng không có
 *       trạng thái ngược lại.</li>
 *   <li><b>Số Xu đến từ cơ sở dữ liệu.</b> Request chỉ mang một trường
 *       {@code code}; người nhận là người đang đăng nhập, đọc từ token.</li>
 * </ol>
 *
 * <h3>Ba điều đầu do cơ sở dữ liệu bảo đảm, không do mã nguồn kiểm tra</h3>
 * Kiểm tra trước bằng Java không giải quyết được gì khi hai request chạy song
 * song: cả hai đều thấy "chưa đổi" và "còn lượt" trước khi bên nào kịp ghi. Nên
 * mỗi điều tựa vào một cơ chế của cơ sở dữ liệu:
 *
 * <ul>
 *   <li>{@code UNIQUE(gift_code_id, user_id)} — dòng đổi mã thứ hai bị từ chối.</li>
 *   <li>{@code UPDATE ... WHERE used_count < max_uses} — lệnh chiếm lượt thứ
 *       101 sửa 0 dòng.</li>
 *   <li>Một giao dịch ngắn bao cả ba lệnh ghi — hỏng bất cứ đâu là cuộn lại
 *       hết, kể cả Xu đã cộng.</li>
 * </ul>
 *
 * <p>Phần kiểm tra bằng Java ở lớp này vẫn còn, nhưng vai trò của nó chỉ là trả
 * lời nhanh và trả lời đẹp cho trường hợp thường gặp. Phần bảo đảm nằm ở dưới.
 * Cùng cách chia — và cùng lý do — với {@link ChapterPurchaseService}.
 *
 * <p>Lớp này là bên điều phối và <b>không mở giao dịch nào</b>; phần ghi nằm ở
 * {@link GiftCodeRedemptionStore}. Nhờ vậy khối {@code catch} bên dưới bắt được
 * lỗi ràng buộc <i>sau khi</i> giao dịch đã cuộn ngược xong.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GiftCodeService {

    private final GiftCodeRepository giftCodeRepository;
    private final GiftCodeRedemptionRepository redemptionRepository;
    private final GiftCodeRedemptionStore store;
    private final CurrentUserService currentUserService;

    /**
     * Người đọc bấm "Đổi mã".
     *
     * @param rawCode nguyên văn người dùng gõ; được chuẩn hóa ở đây
     * @throws GiftCodeException nếu mã không tồn tại hoặc không đổi được lúc này
     */
    public RedeemResultDto redeem(String rawCode) {
        Long userId = currentUserService.currentUserId()
                .orElseThrow(() -> new LoginRequiredException(
                        "Bạn cần đăng nhập để sử dụng gift code."));

        // Chuẩn hóa bằng đúng hàm mà đường tạo mã đã dùng. Hai cách chuẩn hóa hơi
        // khác nhau ở hai đầu là cách tạo ra một mã không bao giờ đổi được.
        String code = GiftCodes.normalize(rawCode);
        if (code == null || code.length() > GiftCodes.MAX_LENGTH) {
            // Mã rỗng hay dài quá cột đều không thể khớp dòng nào; trả lời như
            // với một mã không tồn tại, chứ không rò rỉ giới hạn của lược đồ.
            throw new GiftCodeException(GiftCodeException.Reason.INVALID_GIFT_CODE);
        }

        GiftCode giftCode = giftCodeRepository.findByCode(code)
                .orElseThrow(() -> new GiftCodeException(
                        GiftCodeException.Reason.INVALID_GIFT_CODE));

        // Đường nhanh cho lời từ chối hay gặp nhất. Không phải chốt chặn — chốt
        // chặn là ràng buộc duy nhất bên dưới — mà là cách tránh ném một ngoại lệ
        // ràng buộc cho một thao tác mà người dùng hoàn toàn có thể lặp lại vì
        // quên mất mình đã đổi rồi.
        if (redemptionRepository.existsByGiftCodeIdAndUserId(giftCode.getId(), userId)) {
            throw new GiftCodeException(GiftCodeException.Reason.GIFT_CODE_ALREADY_REDEEMED);
        }

        try {
            return store.redeem(giftCode, userId);
        } catch (DataIntegrityViolationException ex) {
            // Hỏi lại trước khi kết luận. Ràng buộc duy nhất là lời giải thích
            // *thường gặp* cho một lỗi toàn vẹn ở đây, không phải lời giải thích
            // duy nhất — một khóa ngoại không thỏa cũng ném ra đúng kiểu ngoại lệ
            // này. Dịch mọi thứ thành "bạn đã đổi rồi" sẽ biến một hỏng hóc thật
            // thành một câu trả lời bình thường, và giấu nó khỏi cả log lẫn người
            // dùng.
            if (!redemptionRepository.existsByGiftCodeIdAndUserId(giftCode.getId(), userId)) {
                throw ex;
            }

            // Thua cuộc đua với chính mình: một request song song của cùng người
            // này vừa ghi xong dòng đổi mã. Giao dịch vừa rồi đã cuộn ngược
            // nguyên vẹn — lượt đã chiếm được trả lại và Xu không cộng hai lần —
            // nên câu trả lời đúng là câu mà lần đổi thứ hai vẫn luôn nhận được.
            log.info("Đổi trùng gift code {} của người dùng {} — một request khác đã ghi trước",
                    giftCode.getCode(), userId);
            throw new GiftCodeException(GiftCodeException.Reason.GIFT_CODE_ALREADY_REDEEMED);
        }
    }

    /**
     * Người này đã đổi mã ấy chưa.
     *
     * <p>Dùng cho kiểm thử và cho những chỗ cần biết mà không muốn thử đổi. Không
     * nằm trên đường quyết định của {@link #redeem} theo nghĩa bảo đảm — xem ghi
     * chú ở đầu lớp.
     */
    @Transactional(readOnly = true)
    public boolean hasRedeemed(Long giftCodeId, Long userId) {
        return redemptionRepository.existsByGiftCodeIdAndUserId(giftCodeId, userId);
    }
}
