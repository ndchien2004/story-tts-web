package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.EntitlementSource;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.dto.wallet.ChapterPurchaseDto;
import com.storytts.backend.exception.InsufficientCoinsException;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Mở khóa chương bằng Xu, trên cơ sở dữ liệu thật.
 *
 * <p>Ba điều được kiểm ở đây đều là điều mà mock không kiểm được, vì cả ba đều do
 * cơ sở dữ liệu bảo đảm chứ không do mã nguồn: ràng buộc duy nhất chặn mua trùng,
 * câu UPDATE có điều kiện chặn tiêu quá số dư, và giao dịch giữ cho việc trừ Xu
 * với việc cấp quyền cùng sống hoặc cùng chết.
 */
@DataJpaTest
@Import({ChapterEntitlementStore.class, ChapterAccessService.class, AccessControlService.class,
        WalletService.class, ChapterService.class, PublicationService.class})
class ChapterPurchaseJpaTest {

    private static final long PRICE = 50L;

    @Autowired
    private ChapterEntitlementStore store;
    @Autowired
    private ChapterAccessService accessService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private ChapterEntitlementRepository entitlementRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    /** Cửa vào SecurityContext; điều khiển "ai đang đăng nhập" từ đây. */
    @MockitoBean
    private CurrentUserService currentUserService;

    /** ChapterService kéo theo hai thứ này nhưng đường mua không đi qua chúng. */
    @MockitoBean
    private com.storytts.backend.repository.AudioFileRepository audioFileRepository;
    @MockitoBean
    private com.storytts.backend.repository.ReadingProgressRepository progressRepository;

    private Long chapterId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Story story = storyRepository.save(Story.builder().title("Truyện thử")
                .publishedAt(java.time.Instant.now().minusSeconds(60)).build());
        chapterId = chapterRepository.save(Chapter.builder()
                .story(story)
                .title("Chương có giá")
                .content("Nội dung")
                .chapterNumber(1)
                .accessLevel(AccessLevel.MEMBER)
                .coinPrice(PRICE)
                .publishedAt(java.time.Instant.now().minusSeconds(60))
                .build()).getId();

        userId = userRepository.save(User.builder()
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .displayName("Người Đọc")
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build()).getId();

        loginAs(Role.MEMBER, null);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("mua thành công: Xu bị trừ, quyền được cấp, sổ cái có dòng tương ứng")
    void aSuccessfulPurchaseMovesEverythingTogether() {
        topUp(200);

        ChapterPurchaseDto result = store.settle(chapterId, userId);

        assertThat(result.outcome()).isEqualTo(ChapterPurchaseDto.Outcome.PURCHASED);
        assertThat(result.coinsSpent()).isEqualTo(PRICE);
        assertThat(result.balance()).isEqualTo(150L);

        entityManager.flush();
        entityManager.clear();
        assertThat(walletService.balanceOf(userId)).isEqualTo(150L);
        assertThat(walletService.ledgerTotalOf(userId)).isEqualTo(150L);
        assertThat(entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)).isTrue();
    }

    @Test
    @DisplayName("mua rồi thì đọc được — quyết định đổi sang ALLOWED_PURCHASED")
    void buyingOpensTheChapter() {
        topUp(200);
        assertThat(accessService.decide(chapter()))
                .isEqualTo(ChapterAccessDecision.DENIED_COINS_REQUIRED);

        store.settle(chapterId, userId);
        entityManager.flush();
        entityManager.clear();

        assertThat(accessService.decide(chapter()))
                .isEqualTo(ChapterAccessDecision.ALLOWED_PURCHASED);
    }

    @Test
    @DisplayName("không đủ Xu: bị từ chối, và không có quyền nào được cấp")
    void anUnaffordablePurchaseGrantsNothing() {
        topUp(20);

        assertThatThrownBy(() -> store.settle(chapterId, userId))
                .isInstanceOf(InsufficientCoinsException.class);

        assertThat(walletService.balanceOf(userId)).isEqualTo(20L);
        assertThat(entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)).isFalse();
    }

    @Test
    @DisplayName("mua lần thứ hai bị ràng buộc duy nhất chặn — đây là thứ chặn bấm trùng")
    void theUniqueConstraintRejectsASecondPurchase() {
        topUp(200);
        store.settle(chapterId, userId);
        entityManager.flush();
        entityManager.clear();

        // Chèn thẳng dòng quyền thứ hai: đúng điều một request song song sẽ làm
        // sau khi nó cũng đọc được "chưa mua".
        assertThatThrownBy(() -> {
            entitlementRepository.save(ChapterEntitlement.builder()
                    .user(userRepository.getReferenceById(userId))
                    .chapter(chapterRepository.getReferenceById(chapterId))
                    .source(EntitlementSource.COIN_PURCHASE)
                    .coinsSpent(PRICE)
                    .build());
            entitlementRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("VIP bấm mua: không bị trừ Xu, vì họ vốn đã đọc được")
    void aVipIsNotCharged() {
        topUp(200);
        loginAs(Role.MEMBER, Instant.now().plus(30, ChronoUnit.DAYS));

        ChapterPurchaseDto result = store.settle(chapterId, userId);

        assertThat(result.outcome()).isEqualTo(ChapterPurchaseDto.Outcome.ALREADY_ACCESSIBLE);
        assertThat(result.coinsSpent()).isZero();
        assertThat(walletService.balanceOf(userId)).isEqualTo(200L);
        assertThat(entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)).isFalse();
    }

    @Test
    @DisplayName("chương không đặt giá thì không bán được")
    void aFreeChapterCannotBeBought() {
        Chapter free = chapterRepository.findById(chapterId).orElseThrow();
        free.setCoinPrice(0L);
        chapterRepository.saveAndFlush(free);
        entityManager.clear();

        topUp(200);
        assertThatThrownBy(() -> store.settle(chapterId, userId))
                .hasMessageContaining("không bán bằng Xu");
    }

    @Test
    @DisplayName("quản trị viên cấp quyền: có quyền đọc mà không sinh giao dịch Xu nào")
    void anAdminGrantTouchesNoMoney() {
        store.grant(userId, chapterId);
        entityManager.flush();
        entityManager.clear();

        assertThat(entitlementRepository.existsByUserIdAndChapterId(userId, chapterId)).isTrue();
        assertThat(walletService.ledgerTotalOf(userId)).isZero();
    }

    @Test
    @DisplayName("cấp quyền hai lần là lệnh rỗng, không phải lỗi")
    void grantingTwiceIsANoOp() {
        store.grant(userId, chapterId);
        store.grant(userId, chapterId);
        entityManager.flush();

        assertThat(entitlementRepository.countByChapterId(chapterId)).isEqualTo(1L);
    }

    /* ------------------------------------------------------------------ */

    private Chapter chapter() {
        return chapterRepository.findById(chapterId).orElseThrow();
    }

    private void topUp(long coins) {
        walletService.credit(userId, coins, WalletTransactionType.DEPOSIT,
                WalletReferenceType.PAYMENT_ORDER, 1L, "Nạp thử");
        entityManager.flush();
        entityManager.clear();
    }

    private void loginAs(Role role, Instant vipUntil) {
        User user = User.builder()
                .id(userId)
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .displayName("Người Đọc")
                .role(role)
                .vipGranted(false)
                .vipUntil(vipUntil)
                .enabled(true)
                .build();
        when(currentUserService.currentPrincipal())
                .thenReturn(Optional.of(new AppUserPrincipal(user)));
        when(currentUserService.currentUserId()).thenReturn(Optional.of(userId));
        when(currentUserService.isAuthenticated()).thenReturn(true);
    }
}
