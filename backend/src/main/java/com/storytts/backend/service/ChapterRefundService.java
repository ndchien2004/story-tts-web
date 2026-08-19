package com.storytts.backend.service;

import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Trả lại Xu cho những người đã mua một chương sắp bị gỡ khỏi trang.
 *
 * <h3>Vì sao việc này phải tự động</h3>
 * Quyền đọc một chương nằm ở {@code chapter_entitlements}, và khóa ngoại của
 * bảng ấy có {@code ON DELETE CASCADE}. Nghĩa là xóa chương thì quyền biến mất —
 * <b>im lặng</b>, không dấu vết, và Xu thì không quay lại. Người mua mất tiền vì
 * một quyết định họ không tham gia và không được báo.
 *
 * <p>Đây cũng chính là điều kiện mà ghi chú cũ trong {@link WalletTransactionType}
 * nêu ra khi từ chối thêm một giá trị "hoàn tiền": nó chỉ đáng có khi trả lời
 * được câu <i>hoàn thì có thu lại quyền đọc không</i>. Ở đây câu trả lời không
 * cần ai quyết định — chương không còn thì không còn gì để đọc.
 *
 * <h3>Hoàn đúng số đã trả, không phải giá hiện tại</h3>
 * {@code coinsSpent} được chép lại vào từng dòng quyền tại thời điểm mua. Quản
 * trị viên hạ giá chương từ 50 xuống 20 rồi mới xóa thì người đã trả 50 vẫn nhận
 * lại 50. Dùng giá hiện tại là để một thao tác sau này viết lại một việc đã xong.
 *
 * <h3>Một dòng sổ cái cho mỗi lượt mua, không gộp</h3>
 * Xóa một truyện mà một người đã mua ba mươi chương sinh ra ba mươi dòng hoàn,
 * đối xứng với ba mươi dòng mua. Gộp thành một dòng thì gọn hơn nhưng mất mất
 * mối nối tới từng chương, và câu hỏi đầu tiên khi có người thắc mắc — "tôi được
 * hoàn cho chương nào" — không còn tra được.
 *
 * <p>Dòng sổ vẫn trỏ tới chương bằng {@code (CHAPTER, chapterId)} dù chương sắp
 * biến mất. Điều đó là cố ý và đã được lường trước: cặp tham chiếu ấy không có
 * khóa ngoại, chính vì lịch sử tiền bạc không được biến mất theo nội dung mà nó
 * đã trả tiền cho.
 *
 * <h3>Cùng giao dịch với lệnh xóa</h3>
 * Không có {@code @Transactional} mở giao dịch mới ở đây: lớp này được gọi từ
 * bên trong giao dịch xóa và phải ở lại trong đó. Xóa hỏng thì tiền không được
 * hoàn (chương vẫn còn, quyền vẫn còn); hoàn hỏng thì chương không được xóa.
 * Trạng thái ở giữa — chương mất mà tiền chưa về — đúng là thứ cả lớp này sinh
 * ra để ngăn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterRefundService {

    private final ChapterEntitlementRepository entitlementRepository;
    private final WalletService walletService;

    /**
     * Kết quả một lượt hoàn, để bên gọi nói lại với quản trị viên.
     *
     * @param coins   tổng số Xu đã trả lại
     * @param readers số người nhận được tiền hoàn
     */
    public record Refunds(long coins, int readers) {

        public static final Refunds NONE = new Refunds(0L, 0);

        public boolean any() {
            return coins > 0;
        }
    }

    /** Hoàn cho mọi người đã mua một chương. Gọi <b>trước</b> khi xóa chương. */
    @Transactional
    public Refunds refundChapter(Long chapterId) {
        return refund(entitlementRepository.findPaidByChapter(chapterId), "chương " + chapterId);
    }

    /** Hoàn cho mọi người đã mua bất kỳ chương nào của một truyện. */
    @Transactional
    public Refunds refundStory(Long storyId) {
        return refund(entitlementRepository.findPaidByStory(storyId), "truyện " + storyId);
    }

    private Refunds refund(List<ChapterEntitlement> paid, String what) {
        if (paid.isEmpty()) {
            return Refunds.NONE;
        }

        long coins = 0;
        for (ChapterEntitlement entitlement : paid) {
            walletService.credit(
                    entitlement.getUser().getId(),
                    entitlement.getCoinsSpent(),
                    WalletTransactionType.REFUND_CHAPTER,
                    WalletReferenceType.CHAPTER,
                    entitlement.getChapter().getId(),
                    "Hoàn Xu: " + entitlement.getChapter().getTitle());
            coins += entitlement.getCoinsSpent();
        }

        // Bỏ luôn những dòng quyền vừa hoàn, ngay tại đây.
        //
        // Khóa ngoại có ON DELETE CASCADE nên chúng sẽ biến mất khi chương bị xóa
        // dù không có dòng này — nhưng "biến mất dưới tay cơ sở dữ liệu" và "biến
        // mất trong tầm nhìn của Hibernate" là hai chuyện khác nhau. Chúng vừa
        // được nạp vào bối cảnh ở ngay trên, nên tới lúc chương bị xóa thì chúng
        // là những thực thể còn sống trỏ tới một thực thể đã chết, và lần flush
        // kế tiếp báo lỗi về đúng quan hệ ấy.
        //
        // Cùng lý do đã có sẵn ở ChapterEntitlementRepository.deleteByChapterId:
        // "quyền trỏ tới chương ấy phải đi trước".
        entitlementRepository.deleteAll(paid);
        entitlementRepository.flush();

        // Đếm theo người chứ không theo dòng: một người mua ba mươi chương của
        // cùng một truyện là một người được hoàn, không phải ba mươi.
        int readers = (int) paid.stream()
                .map(entitlement -> entitlement.getUser().getId())
                .distinct()
                .count();

        log.info("Hoàn {} Xu cho {} người khi gỡ {}", coins, readers, what);
        return new Refunds(coins, readers);
    }
}
