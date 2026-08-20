package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.GiftCodeException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.WalletRepository;
import com.storytts.backend.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Đổi gift code khi nhiều request chạy <b>thật sự</b> song song.
 *
 * <h3>Vì sao lớp này không phải một {@code @DataJpaTest}</h3>
 * {@code @DataJpaTest} gói mỗi bài kiểm trong một giao dịch rồi cuộn nó lại ở
 * cuối. Tiện cho việc dọn dẹp, nhưng nó làm đúng một chuyện khiến nó vô dụng ở
 * đây: mọi lệnh ghi nằm trong <i>cùng một</i> giao dịch, nên không có hai giao
 * dịch nào để nhìn thấy nhau. Cảnh cần dựng — hai request cùng đọc được "chưa
 * đổi" trước khi bên nào kịp ghi — không tồn tại được trong mô hình ấy.
 *
 * <p>Nên lớp này chạy {@code @SpringBootTest} với các luồng thật, mỗi lượt đổi
 * là một giao dịch riêng thật sự commit. Đây là chỗ duy nhất trong bộ kiểm chứng
 * minh được ba lời hứa mà cả tính năng dựa vào:
 *
 * <ol>
 *   <li>một tài khoản không đổi được một mã hai lần, dù bấm bao nhiêu lần cùng
 *       lúc;</li>
 *   <li>một mã không bao giờ phát quá {@code maxUses};</li>
 *   <li>một giao dịch hỏng không để lại Xu, dòng sổ, hay lượt đã chiếm nào.</li>
 * </ol>
 *
 * <h3>Về H2</h3>
 * Cơ sở dữ liệu thật là MySQL/InnoDB. Hai cơ chế được dựa vào ở đây —
 * {@code UNIQUE} và {@code UPDATE ... WHERE} khóa dòng cho tới hết giao dịch —
 * có ở cả hai, và chúng là toàn bộ phần bảo đảm. Điều H2 không mô phỏng được là
 * mức cô lập và cách xếp hàng của InnoDB; đó là lý do bài kiểm bên dưới khẳng
 * định về <i>kết quả</i> ("đúng bao nhiêu lượt thành công") chứ không về thứ tự
 * hay về việc request nào thắng.
 */
@SpringBootTest
class GiftCodeConcurrencyTest {

    private static final long REWARD = 100L;

    @Autowired
    private GiftCodeService giftCodeService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private GiftCodeRepository giftCodeRepository;
    @Autowired
    private GiftCodeRedemptionRepository redemptionRepository;
    @Autowired
    private WalletTransactionRepository transactionRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private UserRepository userRepository;

    /**
     * "Ai đang đăng nhập" phải khác nhau giữa các luồng.
     *
     * <p>Mock được nạp một lần với một câu trả lời phụ thuộc luồng, thay vì đặt
     * lại giữa chừng: đặt lại một mock trong lúc các luồng đang gọi nó là cách
     * chắc chắn để bài kiểm hỏng vì lý do không liên quan gì tới thứ đang kiểm.
     */
    @MockitoBean
    private CurrentUserService currentUserService;

    /** Id của người mà luồng hiện tại đóng vai. */
    private static final ThreadLocal<Long> ACTING_AS = new ThreadLocal<>();

    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        redemptionRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        giftCodeRepository.deleteAllInBatch();

        userIds = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            userIds.add(userRepository.save(User.builder()
                    .username("dua-thi-" + i + "-" + System.nanoTime())
                    .email("dua-" + i + "-" + System.nanoTime() + "@test.local")
                    .passwordHash("hash")
                    .displayName("Người " + i)
                    .role(Role.MEMBER)
                    .vipGranted(false)
                    .enabled(true)
                    .build()).getId());
        }

        when(currentUserService.currentUserId())
                .thenAnswer(invocation -> Optional.ofNullable(ACTING_AS.get()));
    }

    @AfterEach
    void tearDown() {
        redemptionRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        giftCodeRepository.deleteAllInBatch();
        userRepository.deleteAllById(userIds);
    }

    /* ------------------------------------------------------------------ */
    /* 1. Một người, nhiều lần bấm                                          */
    /* ------------------------------------------------------------------ */

    /**
     * §26 trường hợp 1 và 2: bấm hai lần, hoặc gửi từ hai tab.
     *
     * <p>Tám request đồng thời của cùng một tài khoản. Đúng một cái được cộng Xu;
     * bảy cái còn lại nhận {@code GIFT_CODE_ALREADY_REDEEMED} — hoặc từ phép kiểm
     * ở đường nhanh, hoặc từ ràng buộc duy nhất, và với người dùng thì hai đường
     * ấy nói cùng một câu.
     */
    @Test
    @DisplayName("một tài khoản bấm 8 lần cùng lúc: đúng 1 lần cộng Xu, ví không nhân lên")
    void oneAccountClickingEightTimesIsRewardedOnce() throws Exception {
        GiftCode code = giftCode("DOUBLE-CLICK", null);
        Long userId = userIds.getFirst();

        Outcome outcome = runTogether(8, index -> attempt(userId, "DOUBLE-CLICK"));

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.alreadyRedeemed()).isEqualTo(7);
        assertThat(outcome.unexpected()).isEmpty();

        // Bốn con số phải khớp nhau, và mỗi con số là một cách hỏng khác nhau nếu
        // nó lệch: ví nhân đôi, sổ cái nhân đôi, sổ đổi mã nhân đôi, đếm lượt sai.
        assertThat(walletService.balanceOf(userId)).isEqualTo(REWARD);
        assertThat(walletService.ledgerTotalOf(userId)).isEqualTo(REWARD);
        assertThat(redemptionRepository.countByGiftCodeId(code.getId())).isEqualTo(1L);
        assertThat(reload(code).getUsedCount()).isEqualTo(1);
    }

    /* ------------------------------------------------------------------ */
    /* 2. Nhiều người, mã có trần                                           */
    /* ------------------------------------------------------------------ */

    /**
     * §26 trường hợp 3: trần lượt dưới sức ép.
     *
     * <p>Bốn mươi tài khoản khác nhau tranh nhau một mã còn 10 lượt. Đúng 10 người
     * nhận Xu, và tổng Xu phát ra bằng đúng 10 lần mệnh giá — không phải 11, không
     * phải 40.
     */
    @Test
    @DisplayName("40 người tranh một mã giới hạn 10 lượt: đúng 10 người nhận Xu")
    void fortyRacersNeverExceedTheCeiling() throws Exception {
        GiftCode code = giftCode("FLASH-SALE", 10);

        Outcome outcome = runTogether(userIds.size(),
                index -> attempt(userIds.get(index), "FLASH-SALE"));

        assertThat(outcome.successes()).isEqualTo(10);
        assertThat(outcome.exhausted()).isEqualTo(30);
        assertThat(outcome.unexpected()).isEmpty();

        assertThat(reload(code).getUsedCount()).isEqualTo(10);
        assertThat(redemptionRepository.countByGiftCodeId(code.getId())).isEqualTo(10L);
        assertThat(redemptionRepository.sumCoinsByGiftCode(code.getId()))
                .isEqualTo(10 * REWARD);

        // Và đúng 10 ví có tiền, mỗi ví đúng một lần thưởng.
        long funded = userIds.stream().filter(id -> walletService.balanceOf(id) > 0).count();
        assertThat(funded).isEqualTo(10L);
        assertThat(userIds).allSatisfy(id ->
                assertThat(walletService.balanceOf(id)).isIn(0L, REWARD));
    }

    @Test
    @DisplayName("trần đúng bằng số người tranh: tất cả đều nhận, không ai bị chặn nhầm")
    void aCeilingThatFitsEveryoneLetsEveryoneThrough() throws Exception {
        GiftCode code = giftCode("VUA-DU", userIds.size());

        Outcome outcome = runTogether(userIds.size(),
                index -> attempt(userIds.get(index), "VUA-DU"));

        assertThat(outcome.successes()).isEqualTo(userIds.size());
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(reload(code).getUsedCount()).isEqualTo(userIds.size());
    }

    /* ------------------------------------------------------------------ */
    /* 3. Cuộn ngược                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * §26 trường hợp 4: giao dịch hỏng giữa chừng.
     *
     * <p>Dựng bằng một tài khoản không tồn tại, nên khóa ngoại của sổ đổi mã
     * không thỏa — cùng hình dạng với bất kỳ hỏng hóc nào xảy ra sau khi lượt đã
     * được chiếm. Ở đây mỗi lượt đổi là một giao dịch thật, nên cuộn ngược là
     * cuộn ngược thật, và điều phải thấy là: lượt đã chiếm được trả lại, không có
     * dòng đổi mã nào, không có Xu nào.
     */
    @Test
    @DisplayName("hỏng sau khi đã chiếm lượt: lượt được trả lại, không Xu, không dòng sổ nào")
    void afailedTransactionLeavesNothingBehind() {
        GiftCode code = giftCode("ROLLBACK", 5);
        ACTING_AS.set(999_999_999L);
        try {
            // Và nó *không* được hóa thành một câu trả lời nghiệp vụ. Ràng buộc
            // duy nhất là lời giải thích thường gặp cho một lỗi toàn vẹn ở đây,
            // không phải lời giải thích duy nhất; dịch mọi thứ thành "bạn đã đổi
            // rồi" sẽ giấu một hỏng hóc thật khỏi cả log lẫn người dùng.
            assertThatThrownBy(() -> giftCodeService.redeem("ROLLBACK"))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(GiftCodeException.class);
        } finally {
            ACTING_AS.remove();
        }

        assertThat(reload(code).getUsedCount())
                .as("lượt đã chiếm phải được trả lại khi giao dịch cuộn ngược")
                .isZero();
        assertThat(redemptionRepository.countByGiftCodeId(code.getId())).isZero();
        assertThat(redemptionRepository.sumCoinsByGiftCode(code.getId())).isZero();
        assertThat(transactionRepository.count())
                .as("không dòng sổ cái Xu nào được ghi")
                .isZero();
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Cho {@code count} luồng cùng chạy, và chỉ thả chúng ra khi tất cả đã sẵn
     * sàng.
     *
     * <p>Cái chốt {@link CountDownLatch} là điểm chính: không có nó thì các luồng
     * khởi động lệch nhau vài mili giây và bài kiểm sẽ chạy tuần tự trong khi vẫn
     * xanh — tức là không kiểm được gì cả.
     */
    private Outcome runTogether(int count, java.util.function.IntFunction<Attempt> work)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();
        List<Throwable> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());

        // Một luồng cho mỗi việc, không phải một hồ nhỏ hơn. Hồ nhỏ hơn thì
        // những việc chưa tới lượt không bao giờ đếm lùi cái chốt, còn những
        // việc đang chạy thì đứng chờ chính cái chốt ấy — bài kiểm treo cứng,
        // và treo vì lỗi của chính nó chứ không phải của thứ nó đang kiểm.
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<?>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<Void> task = () -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        work.apply(index).run();
                        successes.incrementAndGet();
                    } catch (GiftCodeException ex) {
                        switch (ex.getReason()) {
                            case GIFT_CODE_ALREADY_REDEEMED -> already.incrementAndGet();
                            case GIFT_CODE_EXHAUSTED -> exhausted.incrementAndGet();
                            default -> unexpected.add(ex);
                        }
                    } catch (Throwable ex) {
                        unexpected.add(ex);
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("mọi luồng phải sẵn sàng trước khi thả").isTrue();
            go.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        return new Outcome(successes.get(), already.get(), exhausted.get(), unexpected);
    }

    /** Một lần đổi, đóng vai một tài khoản cụ thể trong luồng đang chạy. */
    private Attempt attempt(Long userId, String code) {
        return () -> {
            ACTING_AS.set(userId);
            try {
                giftCodeService.redeem(code);
            } finally {
                ACTING_AS.remove();
            }
        };
    }

    @FunctionalInterface
    private interface Attempt {
        void run();
    }

    /**
     * Kết quả của một đợt tranh nhau.
     *
     * <p>{@code unexpected} tồn tại để một lỗi không lường trước không lặng lẽ bị
     * đếm nhầm thành một lời từ chối hợp lệ: nếu một request hỏng vì bế tắc khóa
     * hay vì lỗi lập trình, số lượt thành công vẫn có thể đúng, và bài kiểm sẽ
     * xanh trong khi che mất chuyện đó.
     */
    private record Outcome(int successes, int alreadyRedeemed, int exhausted,
                           List<Throwable> unexpected) {
    }

    private GiftCode giftCode(String code, Integer maxUses) {
        return giftCodeRepository.saveAndFlush(GiftCode.builder()
                .code(code)
                .coinAmount(REWARD)
                .maxUses(maxUses)
                .enabled(true)
                .usedCount(0)
                .build());
    }

    private GiftCode reload(GiftCode code) {
        return giftCodeRepository.findById(code.getId()).orElseThrow();
    }
}
