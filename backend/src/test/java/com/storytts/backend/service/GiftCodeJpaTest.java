package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.GiftCodeRedemption;
import com.storytts.backend.domain.GiftCodeStatus;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.dto.gift.RedeemResultDto;
import com.storytts.backend.exception.GiftCodeException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Đổi gift code, trên một cơ sở dữ liệu thật.
 *
 * <p>Chạy trên H2 chứ không trên mock, vì phần lớn những gì được kiểm ở đây là
 * hành vi của cơ sở dữ liệu chứ không của mã nguồn: câu
 * {@code UPDATE ... WHERE used_count < max_uses} có thật sự trả về 0 dòng khi
 * hết lượt hay không, và {@code UNIQUE(gift_code_id, user_id)} có thật sự từ chối
 * dòng thứ hai hay không. Mock hóa chỗ ấy là mock hóa đúng cái cần kiểm.
 *
 * <p>Phần chạy song song thật — nhiều luồng, nhiều giao dịch — nằm ở
 * {@code GiftCodeConcurrencyTest}: {@code @DataJpaTest} gói mỗi bài kiểm trong
 * một giao dịch duy nhất, nên nó không dựng được cảnh hai giao dịch nhìn thấy
 * nhau.
 */
@DataJpaTest
@Import({GiftCodeService.class, GiftCodeRedemptionStore.class, WalletService.class})
class GiftCodeJpaTest {

    private static final long REWARD = 500L;

    @Autowired
    private GiftCodeService giftCodeService;
    @Autowired
    private GiftCodeRedemptionStore store;
    @Autowired
    private WalletService walletService;
    @Autowired
    private GiftCodeRepository giftCodeRepository;
    @Autowired
    private GiftCodeRedemptionRepository redemptionRepository;
    @Autowired
    private WalletTransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    /** Cửa vào SecurityContext; điều khiển "ai đang đăng nhập" từ đây. */
    @MockitoBean
    private CurrentUserService currentUserService;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        userId = newUser("nguoidoc", "doc@test.local");
        otherUserId = newUser("nguoikhac", "khac@test.local");
        loginAs(userId);
        entityManager.flush();
        entityManager.clear();
    }

    /* ------------------------------------------------------------------ */
    /* Tình trạng suy ra                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("tình trạng suy ra từ các cột, không lưu: năm trường hợp")
    void statusIsDerivedFromTheColumns() {
        Instant now = Instant.now();

        assertThat(code().status(now)).isEqualTo(GiftCodeStatus.ACTIVE);

        assertThat(code(c -> c.enabled(false)).status(now)).isEqualTo(GiftCodeStatus.DISABLED);

        assertThat(code(c -> c.startAt(now.plus(1, ChronoUnit.HOURS))).status(now))
                .isEqualTo(GiftCodeStatus.SCHEDULED);

        assertThat(code(c -> c.endAt(now.minus(1, ChronoUnit.HOURS))).status(now))
                .isEqualTo(GiftCodeStatus.EXPIRED);

        assertThat(code(c -> c.maxUses(3).usedCount(3)).status(now))
                .isEqualTo(GiftCodeStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("mã tắt hiện DISABLED kể cả khi cũng đã hết hạn — quyết định của người đứng trước")
    void disabledOutranksEverythingElse() {
        Instant now = Instant.now();
        GiftCode code = code(c -> c.enabled(false)
                .endAt(now.minus(1, ChronoUnit.HOURS))
                .maxUses(1).usedCount(1));

        assertThat(code.status(now)).isEqualTo(GiftCodeStatus.DISABLED);
    }

    @Test
    @DisplayName("mã không đặt hạn nào thì luôn ACTIVE và không bao giờ hết lượt")
    void anUnboundedCodeStaysActive() {
        GiftCode code = code(c -> c.startAt(null).endAt(null).maxUses(null).usedCount(9_999));

        assertThat(code.status()).isEqualTo(GiftCodeStatus.ACTIVE);
        assertThat(code.isExhausted()).isFalse();
        assertThat(code.remainingUses()).isNull();
    }

    /* ------------------------------------------------------------------ */
    /* Đổi mã thành công                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("đổi thành công: Xu vào ví, sổ cái có dòng, sổ đổi mã có dòng, cột đếm tăng")
    void aSuccessfulRedemptionMovesAllFourTogether() {
        GiftCode code = persisted(c -> c.maxUses(10));

        RedeemResultDto result = giftCodeService.redeem("summer2026");

        assertThat(result.coinAmount()).isEqualTo(REWARD);
        assertThat(result.balance()).isEqualTo(REWARD);
        assertThat(result.code()).isEqualTo("SUMMER2026");

        flushAndClear();

        // (1) số dư ví
        assertThat(walletService.balanceOf(userId)).isEqualTo(REWARD);
        // (2) sổ cái Xu — và nó khớp số dư
        assertThat(walletService.ledgerTotalOf(userId)).isEqualTo(REWARD);
        // (3) sổ đổi mã
        assertThat(redemptionRepository.existsByGiftCodeIdAndUserId(code.getId(), userId)).isTrue();
        // (4) cột đếm lượt
        assertThat(reload(code).getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("dòng sổ cái mang loại GIFT_CODE và trỏ ngược về đúng cái mã")
    void theLedgerRowPointsBackAtTheCode() {
        GiftCode code = persisted(c -> c);
        giftCodeService.redeem("SUMMER2026");
        flushAndClear();

        var row = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                .getContent().getFirst();

        assertThat(row.getType()).isEqualTo(WalletTransactionType.GIFT_CODE);
        assertThat(row.getAmount()).isEqualTo(REWARD);
        assertThat(row.getBalanceBefore()).isZero();
        assertThat(row.getBalanceAfter()).isEqualTo(REWARD);
        assertThat(row.getReferenceId()).isEqualTo(code.getId());
        assertThat(row.getDescription()).contains("SUMMER2026");
    }

    @Test
    @DisplayName("mã không phân biệt hoa thường: bốn cách gõ cùng ra một mã")
    void redemptionIsCaseInsensitive() {
        persisted(c -> c);

        // Ba cách gõ đầu đều tra ra cùng một dòng; chỉ cách đầu thành công vì
        // sau đó tài khoản này đã đổi rồi — đúng điều cần chứng minh.
        assertThat(giftCodeService.redeem("  summer2026  ").coinAmount()).isEqualTo(REWARD);
        flushAndClear();

        for (String typed : new String[]{"SUMMER2026", "Summer2026", "sUmMeR2026"}) {
            assertThatThrownBy(() -> giftCodeService.redeem(typed))
                    .isInstanceOfSatisfying(GiftCodeException.class, ex -> assertThat(ex.getReason())
                            .isEqualTo(GiftCodeException.Reason.GIFT_CODE_ALREADY_REDEEMED));
        }
    }

    @Test
    @DisplayName("hai người khác nhau đổi cùng một mã: cả hai đều nhận Xu")
    void twoDifferentAccountsBothSucceed() {
        GiftCode code = persisted(c -> c.maxUses(10));

        giftCodeService.redeem("SUMMER2026");
        loginAs(otherUserId);
        giftCodeService.redeem("SUMMER2026");
        flushAndClear();

        assertThat(walletService.balanceOf(userId)).isEqualTo(REWARD);
        assertThat(walletService.balanceOf(otherUserId)).isEqualTo(REWARD);
        assertThat(reload(code).getUsedCount()).isEqualTo(2);
        assertThat(redemptionRepository.countByGiftCodeId(code.getId())).isEqualTo(2L);
    }

    /* ------------------------------------------------------------------ */
    /* Sáu lời từ chối                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("mã không tồn tại")
    void anUnknownCodeIsRejected() {
        assertRejected("KHONG-CO-MA-NAY", GiftCodeException.Reason.INVALID_GIFT_CODE);
    }

    @Test
    @DisplayName("mã rỗng cũng chỉ là mã không tồn tại, không phải lỗi máy chủ")
    void aBlankCodeIsJustUnknown() {
        assertRejected("   ", GiftCodeException.Reason.INVALID_GIFT_CODE);
        assertRejected(null, GiftCodeException.Reason.INVALID_GIFT_CODE);
    }

    @Test
    @DisplayName("mã đã tắt")
    void aDisabledCodeIsRejected() {
        persisted(c -> c.enabled(false));
        assertRejected("SUMMER2026", GiftCodeException.Reason.GIFT_CODE_DISABLED);
        assertNothingHappened();
    }

    @Test
    @DisplayName("mã hẹn giờ, chưa tới lúc bắt đầu")
    void aScheduledCodeIsRejectedBeforeItsStart() {
        persisted(c -> c.startAt(Instant.now().plus(2, ChronoUnit.HOURS)));
        assertRejected("SUMMER2026", GiftCodeException.Reason.GIFT_CODE_NOT_STARTED);
        assertNothingHappened();
    }

    @Test
    @DisplayName("mã hẹn giờ đổi được ngay khi mốc bắt đầu đã qua")
    void aScheduledCodeWorksOnceItsStartHasPassed() {
        persisted(c -> c.startAt(Instant.now().minusSeconds(1)));
        assertThat(giftCodeService.redeem("SUMMER2026").coinAmount()).isEqualTo(REWARD);
    }

    @Test
    @DisplayName("mã đã hết hạn")
    void anExpiredCodeIsRejected() {
        persisted(c -> c.startAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .endAt(Instant.now().minusSeconds(1)));
        assertRejected("SUMMER2026", GiftCodeException.Reason.GIFT_CODE_EXPIRED);
        assertNothingHappened();
    }

    @Test
    @DisplayName("mã đã hết lượt")
    void anExhaustedCodeIsRejected() {
        persisted(c -> c.maxUses(1).usedCount(1));
        assertRejected("SUMMER2026", GiftCodeException.Reason.GIFT_CODE_EXHAUSTED);
        assertNothingHappened();
    }

    @Test
    @DisplayName("tài khoản đã đổi rồi thì không đổi lại được")
    void aSecondRedemptionByTheSameAccountIsRejected() {
        GiftCode code = persisted(c -> c.maxUses(10));
        giftCodeService.redeem("SUMMER2026");
        flushAndClear();

        assertRejected("SUMMER2026", GiftCodeException.Reason.GIFT_CODE_ALREADY_REDEEMED);

        // Và lần thứ hai không tiêu mất một lượt của mã: người khác vẫn đổi được.
        assertThat(reload(code).getUsedCount()).isEqualTo(1);
        assertThat(walletService.balanceOf(userId)).isEqualTo(REWARD);
    }

    @Test
    @DisplayName("chưa đăng nhập thì không đổi được")
    void anAnonymousCallerIsRefused() {
        persisted(c -> c);
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> giftCodeService.redeem("SUMMER2026"))
                .hasMessageContaining("đăng nhập");
    }

    /* ------------------------------------------------------------------ */
    /* Ràng buộc của cơ sở dữ liệu                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("ràng buộc duy nhất chặn dòng đổi mã thứ hai — đây là thứ chặn bấm trùng")
    void theUniqueConstraintRejectsASecondRedemptionRow() {
        GiftCode code = persisted(c -> c.maxUses(10));
        giftCodeService.redeem("SUMMER2026");
        flushAndClear();

        // Chèn thẳng dòng thứ hai: đúng điều một request song song sẽ làm sau khi
        // nó cũng đọc được "chưa đổi".
        assertThatThrownBy(() -> {
            redemptionRepository.save(GiftCodeRedemption.builder()
                    .giftCode(giftCodeRepository.getReferenceById(code.getId()))
                    .user(userRepository.getReferenceById(userId))
                    .coinAmount(REWARD)
                    .build());
            redemptionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Câu lệnh chiếm lượt, gọi thẳng, không qua service.
     *
     * <p>Mô phỏng nhiều request song song sau khi tất cả đã đọc được cùng một
     * {@code used_count}. Chỗ quan trọng: lệnh thứ ba không cần biết hai lệnh
     * trước đã chạy — điều kiện nằm trong chính câu UPDATE, nên nó tự đọc lại con
     * số đã tăng. Cùng hình dạng với bài kiểm tương ứng ở {@code WalletJpaTest}.
     */
    @Test
    @DisplayName("chiếm lượt: hai lượt cuối cùng thì lượt thứ ba nhận về 0 dòng")
    void claimingUsesStopsExactlyAtTheCeiling() {
        GiftCode code = persisted(c -> c.maxUses(2));
        flushAndClear();
        Instant now = Instant.now();

        assertThat(giftCodeRepository.claimUse(code.getId(), now)).isEqualTo(1);
        assertThat(giftCodeRepository.claimUse(code.getId(), now)).isEqualTo(1);
        assertThat(giftCodeRepository.claimUse(code.getId(), now)).isZero();

        flushAndClear();
        assertThat(reload(code).getUsedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("chiếm lượt mang theo cả điều kiện thời gian và cờ bật/tắt")
    void claimingHonoursTheWindowAndTheSwitch() {
        Instant now = Instant.now();
        GiftCode disabled = persisted("MA-TAT", c -> c.enabled(false));
        GiftCode early = persisted("MA-SOM", c -> c.startAt(now.plus(1, ChronoUnit.HOURS)));
        GiftCode late = persisted("MA-MUON", c -> c.endAt(now.minus(1, ChronoUnit.HOURS)));
        flushAndClear();

        assertThat(giftCodeRepository.claimUse(disabled.getId(), now)).isZero();
        assertThat(giftCodeRepository.claimUse(early.getId(), now)).isZero();
        assertThat(giftCodeRepository.claimUse(late.getId(), now)).isZero();
    }

    /*
     * Hai điều cố ý *không* được kiểm ở lớp này, vì {@code @DataJpaTest} không
     * kiểm được chúng: việc cuộn ngược một giao dịch hỏng, và việc một lỗi toàn
     * vẹn không phải do trùng thì được ném tiếp thay vì hóa thành
     * ALREADY_REDEEMED.
     *
     * Lý do là cùng một lý do. Ở đây cả bài kiểm nằm trong một giao dịch, nên
     * giao dịch của {@link GiftCodeRedemptionStore} nhập vào nó thay vì tự mở và
     * tự cuộn — không có gì cuộn ngược để mà quan sát, và phiên Hibernate đã hỏng
     * thì câu truy vấn tiếp theo ném ra một lỗi nội bộ chứ không phải lỗi thật.
     *
     * Cả hai nằm ở {@code GiftCodeConcurrencyTest}, nơi mỗi lượt đổi là một giao
     * dịch riêng thật sự commit.
     */

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    /** Một mã dựng sẵn trong bộ nhớ, chưa lưu — để kiểm phần suy ra tình trạng. */
    private GiftCode code() {
        return code(c -> c);
    }

    private GiftCode code(java.util.function.UnaryOperator<GiftCode.GiftCodeBuilder> tweak) {
        return tweak.apply(GiftCode.builder()
                .code("SUMMER2026")
                .coinAmount(REWARD)
                .enabled(true)
                .usedCount(0)).build();
    }

    private GiftCode persisted(java.util.function.UnaryOperator<GiftCode.GiftCodeBuilder> tweak) {
        return persisted("SUMMER2026", tweak);
    }

    private GiftCode persisted(String code,
                               java.util.function.UnaryOperator<GiftCode.GiftCodeBuilder> tweak) {
        GiftCode saved = giftCodeRepository.saveAndFlush(
                tweak.apply(GiftCode.builder()
                        .code(code)
                        .coinAmount(REWARD)
                        .enabled(true)
                        .usedCount(0)).build());
        entityManager.clear();
        return saved;
    }

    private GiftCode reload(GiftCode code) {
        return giftCodeRepository.findById(code.getId()).orElseThrow();
    }

    private void assertRejected(String typed, GiftCodeException.Reason expected) {
        assertThatThrownBy(() -> giftCodeService.redeem(typed))
                .isInstanceOfSatisfying(GiftCodeException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(expected));
    }

    /** Một lời từ chối không được để lại dấu vết nào. */
    private void assertNothingHappened() {
        flushAndClear();
        assertThat(walletService.balanceOf(userId)).isZero();
        assertThat(walletService.ledgerTotalOf(userId)).isZero();
        assertThat(redemptionRepository.count()).isZero();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Long newUser(String username, String email) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash("hash")
                .displayName(username)
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build()).getId();
    }

    private void loginAs(Long id) {
        when(currentUserService.currentUserId()).thenReturn(Optional.of(id));
    }
}
