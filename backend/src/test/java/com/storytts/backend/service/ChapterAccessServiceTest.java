package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.exception.ChapterPurchaseRequiredException;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.repository.WalletRepository;
import com.storytts.backend.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Bảng quyết định quyền đọc chương, sau khi Xu bước vào.
 *
 * <p>Điều quan trọng nhất được giữ ở đây nằm ở nhóm đầu tiên: <b>chương giá 0 phải
 * cư xử y hệt như trước khi có tính năng Xu</b>. Mọi chương đang có trên máy chủ
 * đều mang giá 0, nên nếu nhóm ấy còn xanh thì tính năng này không thể làm đổi
 * hành vi của một chương nào đang chạy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterAccessServiceTest {

    private static final Long CHAPTER_ID = 77L;
    private static final Long USER_ID = 5L;

    @Mock
    private ChapterEntitlementRepository entitlementRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private CurrentUserService currentUserService;

    private ChapterAccessService service;

    @BeforeEach
    void setUp() {
        service = new ChapterAccessService(
                new AccessControlService(currentUserService),
                entitlementRepository, walletRepository, currentUserService);
        asGuest();
    }

    /* ------------------------------------------------------------------ */
    /* Nhóm 1 — chương giá 0: đúng hành vi cũ, không đổi một ly            */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("chương không đặt giá thì cư xử y như trước khi có Xu")
    class WithoutPricing {

        @Test
        @DisplayName("PUBLIC: khách đọc được")
        void publicIsFree() {
            assertThat(service.decide(chapter(AccessLevel.PUBLIC, 0)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_FREE);
        }

        @Test
        @DisplayName("MEMBER: khách bị mời đăng nhập, thành viên đọc được")
        void memberNeedsLogin() {
            assertThat(service.decide(chapter(AccessLevel.MEMBER, 0)))
                    .isEqualTo(ChapterAccessDecision.DENIED_LOGIN_REQUIRED);

            asMember();
            assertThat(service.decide(chapter(AccessLevel.MEMBER, 0)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_MEMBER);
        }

        @Test
        @DisplayName("VIP: thành viên thường bị chặn và KHÔNG mua được bằng Xu")
        void vipStaysVipOnly() {
            asMember();
            ChapterAccessDecision decision = service.decide(chapter(AccessLevel.VIP, 0));

            assertThat(decision).isEqualTo(ChapterAccessDecision.DENIED_VIP_REQUIRED);
            // Không có giá thì không có nút mua — đây là chỗ dễ vô tình mở ra nhất.
            assertThat(decision.purchasable()).isFalse();
        }

        @Test
        @DisplayName("VIP: người có VIP đọc được")
        void vipReadsVipChapters() {
            asVip();
            assertThat(service.decide(chapter(AccessLevel.VIP, 0)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_VIP);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Nhóm 2 — chương có giá                                              */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("chương có giá Xu")
    class WithPricing {

        @Test
        @DisplayName("khách chưa đăng nhập được mời đăng nhập, không phải mời trả tiền")
        void guestIsAskedToLogInFirst() {
            assertThat(service.decide(chapter(AccessLevel.MEMBER, 50)))
                    .isEqualTo(ChapterAccessDecision.DENIED_LOGIN_REQUIRED);
        }

        @Test
        @DisplayName("thành viên chưa mua: mở được bằng Xu")
        void memberIsOfferedThePurchase() {
            asMember();
            when(entitlementRepository.existsByUserIdAndChapterId(USER_ID, CHAPTER_ID)).thenReturn(false);

            ChapterAccessDecision decision = service.decide(chapter(AccessLevel.MEMBER, 50));

            assertThat(decision).isEqualTo(ChapterAccessDecision.DENIED_COINS_REQUIRED);
            assertThat(decision.purchasable()).isTrue();
        }

        @Test
        @DisplayName("đã mua rồi thì đọc được")
        void anOwnedChapterOpens() {
            asMember();
            when(entitlementRepository.existsByUserIdAndChapterId(USER_ID, CHAPTER_ID)).thenReturn(true);

            assertThat(service.decide(chapter(AccessLevel.MEMBER, 50)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_PURCHASED);
        }

        @Test
        @DisplayName("VIP đọc được chương có giá mà không tốn Xu nào")
        void vipReadsPaidChaptersFree() {
            asVip();

            assertThat(service.decide(chapter(AccessLevel.VIP, 50)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_VIP);
            assertThat(service.decide(chapter(AccessLevel.MEMBER, 50)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_VIP);
        }

        @Test
        @DisplayName("quản trị viên đọc được mọi thứ, không hỏi tới ví")
        void adminBypassesEverything() {
            asAdmin();
            assertThat(service.decide(chapter(AccessLevel.VIP, 500)))
                    .isEqualTo(ChapterAccessDecision.ALLOWED_ADMIN);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Nhóm 3 — chặn cứng ném đúng loại ngoại lệ                           */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("chương có giá ném 402 kèm giá và số dư; chương VIP ném 403")
    void requireAccessThrowsTheRightKind() {
        asMember();
        when(entitlementRepository.existsByUserIdAndChapterId(USER_ID, CHAPTER_ID)).thenReturn(false);
        when(walletRepository.findBalanceByUserId(USER_ID)).thenReturn(Optional.of(20L));

        assertThatThrownBy(() -> service.requireAccess(chapter(AccessLevel.MEMBER, 50)))
                .isInstanceOfSatisfying(ChapterPurchaseRequiredException.class, ex -> {
                    assertThat(ex.getCoinPrice()).isEqualTo(50L);
                    assertThat(ex.getBalance()).isEqualTo(20L);
                    assertThat(ex.affordable()).isFalse();
                });

        assertThatThrownBy(() -> service.requireAccess(chapter(AccessLevel.VIP, 0)))
                .isInstanceOf(ChapterLockedException.class);
    }

    @Test
    @DisplayName("đủ quyền thì requireAccess im lặng đi qua")
    void requireAccessPassesWhenAllowed() {
        assertThatCode(() -> service.requireAccess(chapter(AccessLevel.PUBLIC, 0)))
                .doesNotThrowAnyException();
    }

    /* ------------------------------------------------------------------ */

    private Chapter chapter(AccessLevel level, long price) {
        return Chapter.builder()
                .id(CHAPTER_ID)
                .accessLevel(level)
                .coinPrice(price)
                .title("Chương thử")
                .chapterNumber(1)
                .build();
    }

    private void asGuest() {
        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());
        when(currentUserService.isAuthenticated()).thenReturn(false);
    }

    private void asMember() {
        login(user(Role.MEMBER, false, null));
    }

    private void asVip() {
        login(user(Role.MEMBER, false, Instant.now().plus(30, ChronoUnit.DAYS)));
    }

    private void asAdmin() {
        login(user(Role.ADMIN, false, null));
    }

    private void login(User user) {
        AppUserPrincipal principal = new AppUserPrincipal(user);
        when(currentUserService.currentPrincipal()).thenReturn(Optional.of(principal));
        when(currentUserService.currentUserId()).thenReturn(Optional.of(USER_ID));
        when(currentUserService.isAuthenticated()).thenReturn(true);
    }

    private User user(Role role, boolean vipGranted, Instant vipUntil) {
        return User.builder()
                .id(USER_ID)
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .displayName("Người Đọc")
                .role(role)
                .vipGranted(vipGranted)
                .vipUntil(vipUntil)
                .enabled(true)
                .build();
    }
}
