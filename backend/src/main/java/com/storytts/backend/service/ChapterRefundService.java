package com.storytts.backend.service;

import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.service.notification.NotificationDraft;
import com.storytts.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trả lại Xu cho những người đã mua một chương sắp bị gỡ khỏi trang, và báo cho họ.
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
 * <h3>Một thông báo cho mỗi người, không phải cho mỗi dòng</h3>
 * Sổ cái ghi theo lượt mua vì nó là chứng từ; hộp thư thì nói với một
 * <i>người</i>, và ba mươi dòng "chương của bạn đã bị gỡ" liền nhau là spam chứ
 * không phải thông tin. Nên các lượt hoàn được gom theo người nhận, và mỗi người
 * nhận đúng một câu mang tổng số Xu — con số ấy lấy từ chính các dòng vừa ghi
 * vào sổ, không phải từ một phép tính riêng.
 *
 * <p>Thông báo được ghi <b>sau</b> khi ví đã được cộng và <b>trong cùng giao
 * dịch</b>. Đó là điều kiện, không phải thứ tự tình cờ: lệnh cộng Xu hỏng thì cả
 * giao dịch cuộn ngược và lời báo "đã hoàn" biến mất cùng. Không có đường nào để
 * một thông báo nói rằng tiền đã về trong khi nó chưa về.
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
    private final NotificationService notificationService;

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
        return refund(entitlementRepository.findPaidByChapter(chapterId),
                "chương " + chapterId, false, chapterId);
    }

    /** Hoàn cho mọi người đã mua bất kỳ chương nào của một truyện. */
    @Transactional
    public Refunds refundStory(Long storyId) {
        return refund(entitlementRepository.findPaidByStory(storyId),
                "truyện " + storyId, true, storyId);
    }

    /**
     * @param wholeStory cả truyện bị gỡ, không chỉ một chương — quyết định câu
     *                   chữ, và quyết định thông báo còn chỗ nào để đưa người ta
     *                   về hay không
     * @param scopeId    id của thứ vừa bị gỡ; đi vào {@code eventId} để một lần
     *                   xóa được xử lý lại không sinh thông báo thứ hai
     */
    private Refunds refund(List<ChapterEntitlement> paid, String what,
                           boolean wholeStory, Long scopeId) {
        if (paid.isEmpty()) {
            return Refunds.NONE;
        }

        // Gom theo người trong lúc đi qua danh sách, để không phải duyệt lần hai.
        // LinkedHashMap giữ nguyên thứ tự gặp, nên log và thông báo ra theo cùng
        // một thứ tự ở mọi lần chạy — thứ đáng có khi phải đọc lại log về sau.
        Map<Long, Recipient> byUser = new LinkedHashMap<>();

        long coins = 0;
        for (ChapterEntitlement entitlement : paid) {
            Long userId = entitlement.getUser().getId();

            walletService.credit(
                    userId,
                    entitlement.getCoinsSpent(),
                    WalletTransactionType.REFUND_CHAPTER,
                    WalletReferenceType.CHAPTER,
                    entitlement.getChapter().getId(),
                    "Hoàn Xu: " + entitlement.getChapter().getTitle());
            coins += entitlement.getCoinsSpent();

            byUser.computeIfAbsent(userId, ignored -> new Recipient())
                    .add(entitlement.getChapter().getTitle(), entitlement.getCoinsSpent());
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

        // Id lấy được từ proxy mà không phải nạp gì; tên thì phải nạp thật, nên
        // chỉ hỏi khi câu chữ cần tới nó. Và phải hỏi ở đây, trước lệnh xóa: bên
        // nhận thông báo chạy ở AFTER_COMMIT, lúc ấy không còn gì để tra.
        Long storyId = paid.get(0).getChapter().getStory().getId();
        String storyTitle = wholeStory ? paid.get(0).getChapter().getStory().getTitle() : null;

        notificationService.notifyAll(
                announcements(byUser, wholeStory, scopeId, storyId, storyTitle));

        // Đếm theo người chứ không theo dòng: một người mua ba mươi chương của
        // cùng một truyện là một người được hoàn, không phải ba mươi.
        int readers = byUser.size();

        log.info("Hoàn {} Xu cho {} người khi gỡ {}", coins, readers, what);
        return new Refunds(coins, readers);
    }

    /**
     * Một câu cho mỗi người, dựng từ đúng những gì vừa được ghi vào sổ.
     *
     * <p>Số Xu trong câu chữ là tổng của các dòng sổ cái vừa tạo cho người ấy —
     * không phải một con số tính lại từ giá chương, vốn có thể đã đổi. Người đọc
     * vẫn được mời sang trang <b>Lịch sử Xu</b> để đối chiếu, vì đó mới là nơi
     * con số có thể tin được.
     */
    private List<NotificationDraft> announcements(Map<Long, Recipient> byUser, boolean wholeStory,
                                                  Long scopeId, Long storyId, String storyTitle) {
        List<NotificationDraft> drafts = new ArrayList<>(byUser.size());

        for (Map.Entry<Long, Recipient> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            Recipient recipient = entry.getValue();

            NotificationDraft draft = NotificationDraft.to(userId)
                    .type(NotificationType.CHAPTER_DELETED)
                    // Đụng tới tiền và tới quyền truy cập — hai lý do đủ để nó
                    // không được lẫn vào giữa những tin thường.
                    .priority(NotificationPriority.IMPORTANT)
                    .action(NotificationAction.VIEW_REFUND_HISTORY)
                    .meta("refundedCoins", recipient.coins)
                    .meta("chapters", recipient.chapters)
                    .event((wholeStory ? "story-deleted:" : "chapter-deleted:")
                            + scopeId + ":" + userId);

            if (wholeStory) {
                drafts.add(draft
                        .title("Truyện bạn đã mua đã bị gỡ")
                        .message(("Truyện “%s” vừa được quản trị viên gỡ khỏi thư viện, nên %d "
                                + "chương bạn đã mở khóa không còn nữa. %d Xu đã được hoàn lại "
                                + "vào ví của bạn.")
                                .formatted(storyTitle, recipient.chapters, recipient.coins))
                        .meta("storyTitle", storyTitle)
                        // Cố ý không gắn thực thể liên quan: truyện đã biến mất,
                        // nên một nút "về danh sách chương" sẽ dẫn tới 404.
                        .build());
            } else {
                drafts.add(draft
                        .title("Chương bạn đã mua đã bị gỡ")
                        .message(("Chương “%s” vừa được quản trị viên gỡ khỏi trang. %d Xu đã "
                                + "được hoàn lại vào ví của bạn. Những chương còn lại của truyện "
                                + "vẫn đọc được bình thường.")
                                .formatted(recipient.firstChapterTitle, recipient.coins))
                        .meta("chapterTitle", recipient.firstChapterTitle)
                        // Truyện vẫn còn sống, nên có một chỗ để quay về — trình
                        // duyệt dựng nút thứ hai từ đúng cặp này.
                        .about(NotificationEntityType.STORY, storyId)
                        .build());
            }
        }

        return drafts;
    }

    /** Phần của một người trong một lượt gỡ: bao nhiêu chương, bao nhiêu Xu. */
    private static final class Recipient {
        private long coins;
        private int chapters;
        private String firstChapterTitle;

        void add(String chapterTitle, long refunded) {
            coins += refunded;
            chapters++;
            if (firstChapterTitle == null) {
                firstChapterTitle = chapterTitle;
            }
        }
    }
}
