package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.GiftCodeRedemption;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.gift.GiftCodeDto;
import com.storytts.backend.dto.gift.GiftCodeRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Khu quản trị của gift code, trên một cơ sở dữ liệu thật.
 *
 * <p>Trọng tâm là ba quy tắc sửa đổi mà backend phải tự giữ — không hạ trần lượt
 * xuống dưới số đã phát, không đổi mã của một đợt đang chạy, không xóa cứng một
 * mã đã có người đổi. Cả ba đều là những thứ giao diện cũng kiểm, và đó chính là
 * lý do phải kiểm ở đây: một request gửi thẳng bằng curl không đi qua giao diện.
 */
@DataJpaTest
@Import(GiftCodeAdminService.class)
class GiftCodeAdminJpaTest {

    @Autowired
    private GiftCodeAdminService adminService;
    @Autowired
    private GiftCodeRepository giftCodeRepository;
    @Autowired
    private GiftCodeRedemptionRepository redemptionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Long adminId;
    private Long readerId;

    @BeforeEach
    void setUp() {
        adminId = newUser("quantri", "qt@test.local", Role.ADMIN);
        readerId = newUser("nguoidoc", "doc@test.local", Role.MEMBER);

        when(currentUserService.currentUserId()).thenReturn(Optional.of(adminId));
        when(currentUserService.currentUserReference())
                .thenAnswer(call -> Optional.of(userRepository.getReferenceById(adminId)));

        entityManager.flush();
        entityManager.clear();
    }

    /* ------------------------------------------------------------------ */
    /* Tạo                                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("tạo mã hợp lệ: mã được chuẩn hóa thành chữ hoa và ghi nhận người tạo")
    void aValidCodeIsStoredNormalised() {
        GiftCodeDto created = adminService.create(request("summer2026", 500L)
                .withMaxUses(1_000)
                .withDescription("Quà hè")
                .build());

        assertThat(created.code()).isEqualTo("SUMMER2026");
        assertThat(created.coinAmount()).isEqualTo(500L);
        assertThat(created.maxUses()).isEqualTo(1_000);
        assertThat(created.usedCount()).isZero();
        assertThat(created.remainingUses()).isEqualTo(1_000);
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.createdBy()).isEqualTo("quantri");

        assertThat(giftCodeRepository.findByCode("SUMMER2026")).isPresent();
    }

    @Test
    @DisplayName("mã trùng bị từ chối, kể cả khi gõ khác kiểu chữ")
    void aDuplicateCodeIsRejectedRegardlessOfCase() {
        adminService.create(request("SUMMER2026", 500L).build());
        entityManager.flush();

        assertThatThrownBy(() -> adminService.create(request("summer2026", 100L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("khoảng thời gian đi ngược chiều bị từ chối")
    void anInvertedWindowIsRejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> adminService.create(request("NGUOC", 100L)
                .withStart(now.plus(2, ChronoUnit.DAYS))
                .withEnd(now.plus(1, ChronoUnit.DAYS))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("trước thời gian kết thúc");
    }

    @Test
    @DisplayName("bỏ trống mốc thời gian và trần lượt là hợp lệ, và có nghĩa")
    void anUnboundedCodeIsValid() {
        GiftCodeDto created = adminService.create(request("VO-HAN", 50L).build());

        assertThat(created.startAt()).isNull();
        assertThat(created.endAt()).isNull();
        assertThat(created.maxUses()).isNull();
        assertThat(created.remainingUses()).isNull();
        assertThat(created.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("mã hẹn giờ tạo hôm nay vẫn nằm trong cơ sở dữ liệu, chỉ chưa đổi được")
    void aScheduledCodeExistsBeforeItStarts() {
        GiftCodeDto created = adminService.create(request("HEN-GIO", 200L)
                .withStart(Instant.now().plus(3, ChronoUnit.DAYS))
                .withEnd(Instant.now().plus(10, ChronoUnit.DAYS))
                .build());

        assertThat(created.status()).isEqualTo("SCHEDULED");
        assertThat(giftCodeRepository.existsByCode("HEN-GIO")).isTrue();
    }

    /* ------------------------------------------------------------------ */
    /* Sửa                                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("mã chưa ai đổi thì sửa được tất cả, kể cả chính cái mã")
    void anUntouchedCodeIsFullyEditable() {
        GiftCodeDto created = adminService.create(request("CU", 100L).build());

        GiftCodeDto updated = adminService.update(created.id(),
                request("moi", 250L).withMaxUses(5).build());

        assertThat(updated.code()).isEqualTo("MOI");
        assertThat(updated.coinAmount()).isEqualTo(250L);
        assertThat(updated.maxUses()).isEqualTo(5);
    }

    /**
     * Đây là quy tắc §17 của đề bài, và là quy tắc dễ bỏ sót nhất: hạ trần lượt
     * xuống dưới số đã phát tạo ra một dòng dữ liệu không mô tả trạng thái nào có
     * thật.
     */
    @Test
    @DisplayName("không hạ được số lượt tối đa xuống dưới số lượt đã phát")
    void maxUsesCannotDropBelowWhatHasAlreadyBeenGivenOut() {
        GiftCode code = redeemedCode("DA-PHAT", 500);

        assertThatThrownBy(() -> adminService.update(code.getId(),
                request("DA-PHAT", 100L).withMaxUses(100).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể nhỏ hơn 500");
    }

    @Test
    @DisplayName("đặt số lượt tối đa đúng bằng số đã phát thì được — nó chỉ có nghĩa là dừng lại")
    void maxUsesMayEqualTheUsedCount() {
        GiftCode code = redeemedCode("VUA-BANG", 5);

        GiftCodeDto updated = adminService.update(code.getId(),
                request("VUA-BANG", 100L).withMaxUses(5).build());

        assertThat(updated.maxUses()).isEqualTo(5);
        assertThat(updated.remainingUses()).isZero();
        assertThat(updated.status()).isEqualTo("EXHAUSTED");
    }

    @Test
    @DisplayName("mã đã có người đổi thì không đổi tên được — lịch sử Xu của họ trỏ về tên cũ")
    void aRedeemedCodeCannotBeRenamed() {
        GiftCode code = redeemedCode("DA-DOI", 1);

        assertThatThrownBy(() -> adminService.update(code.getId(),
                request("TEN-KHAC", 100L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đổi được mã");
    }

    @Test
    @DisplayName("mã đã có người đổi vẫn sửa được mệnh giá và hạn dùng")
    void aRedeemedCodeStillAcceptsOtherEdits() {
        GiftCode code = redeemedCode("DA-DOI", 1);
        Instant end = Instant.now().plus(30, ChronoUnit.DAYS);

        GiftCodeDto updated = adminService.update(code.getId(),
                request("DA-DOI", 999L).withEnd(end).build());

        assertThat(updated.coinAmount()).isEqualTo(999L);
        assertThat(updated.endAt()).isEqualTo(end);
        assertThat(updated.usedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("đổi sang một mã mà mã khác đã dùng thì bị từ chối")
    void renamingOntoAnExistingCodeIsRejected() {
        adminService.create(request("DA-CO", 100L).build());
        GiftCodeDto other = adminService.create(request("KHAC", 100L).build());
        entityManager.flush();

        assertThatThrownBy(() -> adminService.update(other.id(),
                request("da-co", 100L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại");
    }

    /* ------------------------------------------------------------------ */
    /* Bật/tắt và xóa                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("tắt rồi bật lại một mã")
    void aCodeCanBeSwitchedOffAndBackOn() {
        GiftCodeDto created = adminService.create(request("CONG-TAC", 100L).build());

        assertThat(adminService.setEnabled(created.id(), false).status()).isEqualTo("DISABLED");
        assertThat(adminService.setEnabled(created.id(), true).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("mã chưa ai đổi thì xóa được")
    void anUntouchedCodeCanBeDeleted() {
        GiftCodeDto created = adminService.create(request("XOA-DUOC", 100L).build());

        adminService.delete(created.id());
        entityManager.flush();

        assertThat(giftCodeRepository.existsByCode("XOA-DUOC")).isFalse();
    }

    @Test
    @DisplayName("mã đã có người đổi thì không xóa được, và câu trả lời chỉ ra việc cần làm")
    void aRedeemedCodeCannotBeDeleted() {
        GiftCode code = redeemedCode("GIU-LAI", 1);

        assertThatThrownBy(() -> adminService.delete(code.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tắt mã");

        assertThat(giftCodeRepository.existsById(code.getId())).isTrue();
    }

    @Test
    @DisplayName("thao tác trên một id không tồn tại là 404, không phải 500")
    void anUnknownIdIsANotFound() {
        assertThatThrownBy(() -> adminService.detail(404_404L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adminService.redemptions(404_404L, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /* ------------------------------------------------------------------ */
    /* Danh sách, lọc, thống kê                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("lọc theo tình trạng: mỗi mã rơi vào đúng một nhóm")
    void filteringByStatusPartitionsTheTable() {
        Instant now = Instant.now();
        adminService.create(request("DANG-CHAY", 100L).build());
        adminService.create(request("CHO-GIO", 100L)
                .withStart(now.plus(1, ChronoUnit.DAYS)).build());
        GiftCodeDto off = adminService.create(request("DA-TAT", 100L).build());
        adminService.setEnabled(off.id(), false);
        persist("HET-HAN", c -> c.startAt(now.minus(2, ChronoUnit.DAYS))
                .endAt(now.minus(1, ChronoUnit.DAYS)));
        persist("HET-LUOT", c -> c.maxUses(2).usedCount(2));
        entityManager.flush();
        entityManager.clear();

        assertThat(codesWithStatus("ACTIVE")).containsExactly("DANG-CHAY");
        assertThat(codesWithStatus("SCHEDULED")).containsExactly("CHO-GIO");
        assertThat(codesWithStatus("DISABLED")).containsExactly("DA-TAT");
        assertThat(codesWithStatus("EXPIRED")).containsExactly("HET-HAN");
        assertThat(codesWithStatus("EXHAUSTED")).containsExactly("HET-LUOT");

        // Không lọc thì thấy đủ cả năm — tổng của năm nhóm rời nhau.
        assertThat(codesWithStatus(null)).hasSize(5);
    }

    @Test
    @DisplayName("tìm theo mã và theo ghi chú")
    void keywordSearchesCodeAndDescription() {
        adminService.create(request("TET2026", 100L).withDescription("Quà Tết").build());
        adminService.create(request("HE2026", 100L).withDescription("Quà hè").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(codesMatching("tet")).containsExactly("TET2026");
        assertThat(codesMatching("Quà hè")).containsExactly("HE2026");
        assertThat(codesMatching("2026")).hasSize(2);
    }

    @Test
    @DisplayName("thống kê đếm đúng, và tổng Xu cộng từ sổ đổi mã")
    void statsComeFromTheDatabase() {
        redeemedCode("MOT", 1);
        redeemedCode("HAI", 1);
        GiftCodeDto off = adminService.create(request("BA", 100L).build());
        adminService.setEnabled(off.id(), false);
        entityManager.flush();
        entityManager.clear();

        var stats = adminService.stats();
        assertThat(stats.totalCodes()).isEqualTo(3L);
        // "MOT" và "HAI" đang chạy; "BA" đã tắt.
        assertThat(stats.activeCodes()).isEqualTo(2L);
        assertThat(stats.totalRedemptions()).isEqualTo(2L);
        assertThat(stats.totalCoins()).isEqualTo(2 * 100L);
    }

    /**
     * Mệnh giá sửa được giữa chừng, và tổng Xu đã phát vẫn phải nói đúng.
     *
     * <p>Đây là lý do {@code gift_code_redemptions.coin_amount} tồn tại: nhân mệnh
     * giá hiện tại với số lượt sẽ cho 1 × 900 = 900, trong khi con số thật là 100.
     */
    @Test
    @DisplayName("sửa mệnh giá không làm sai lệch tổng Xu đã phát trước đó")
    void editingTheAmountDoesNotRewriteHistory() {
        GiftCode code = redeemedCode("DOI-GIA", 1);

        adminService.update(code.getId(), request("DOI-GIA", 900L).build());
        entityManager.flush();
        entityManager.clear();

        var detail = adminService.detail(code.getId());
        assertThat(detail.giftCode().coinAmount()).isEqualTo(900L);
        assertThat(detail.totalCoins()).isEqualTo(100L);
        assertThat(detail.redemptionCount()).isEqualTo(1L);
        assertThat(detail.consistent()).isTrue();
    }

    /* ------------------------------------------------------------------ */
    /* Sinh mã                                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("mã sinh ra đã chuẩn hóa, có tiền tố, và không trùng nhau")
    void generatedCodesAreNormalisedAndDistinct() {
        var generated = new java.util.HashSet<String>();
        for (int i = 0; i < 50; i++) {
            String code = adminService.generateCode("summer");
            assertThat(code).startsWith("SUMMER-").hasSizeLessThanOrEqualTo(GiftCodes.MAX_LENGTH);
            assertThat(code).isEqualTo(GiftCodes.normalize(code));
            generated.add(code);
        }
        assertThat(generated).hasSize(50);
    }

    @Test
    @DisplayName("sinh mã không tiền tố cũng hợp lệ")
    void generatingWithoutAPrefixWorks() {
        String code = adminService.generateCode(null);
        assertThat(code).isNotBlank().doesNotContain(" ");
        assertThat(giftCodeRepository.existsByCode(code)).isFalse();
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    private List<String> codesWithStatus(String status) {
        return adminService.list(null, status, null, null, page()).content()
                .stream().map(GiftCodeDto::code).toList();
    }

    private List<String> codesMatching(String keyword) {
        return adminService.list(keyword, null, null, null, page()).content()
                .stream().map(GiftCodeDto::code).toList();
    }

    private static PageRequest page() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Một mã đã có {@code used} lượt đổi, kèm đúng bấy nhiêu dòng trong sổ. */
    private GiftCode redeemedCode(String code, int used) {
        GiftCode saved = persist(code, c -> c.usedCount(used));
        redemptionRepository.save(GiftCodeRedemption.builder()
                .giftCode(saved)
                .user(userRepository.getReferenceById(readerId))
                .coinAmount(saved.getCoinAmount())
                .build());
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private GiftCode persist(String code,
                             java.util.function.UnaryOperator<GiftCode.GiftCodeBuilder> tweak) {
        GiftCode saved = giftCodeRepository.saveAndFlush(
                tweak.apply(GiftCode.builder()
                        .code(code)
                        .coinAmount(100L)
                        .enabled(true)
                        .usedCount(0)).build());
        entityManager.clear();
        return saved;
    }

    private Long newUser(String username, String email, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash("hash")
                .displayName(username)
                .role(role)
                .vipGranted(false)
                .enabled(true)
                .build()).getId();
    }

    /** Builder nhỏ cho {@link GiftCodeRequest}, để mỗi bài kiểm chỉ nêu thứ nó quan tâm. */
    private static Req request(String code, long amount) {
        return new Req(code, amount);
    }

    private static final class Req {
        private final String code;
        private final long amount;
        private Instant startAt;
        private Instant endAt;
        private Integer maxUses;
        private String description;

        private Req(String code, long amount) {
            this.code = code;
            this.amount = amount;
        }

        Req withStart(Instant value) {
            this.startAt = value;
            return this;
        }

        Req withEnd(Instant value) {
            this.endAt = value;
            return this;
        }

        Req withMaxUses(Integer value) {
            this.maxUses = value;
            return this;
        }

        Req withDescription(String value) {
            this.description = value;
            return this;
        }

        GiftCodeRequest build() {
            return new GiftCodeRequest(code, amount, startAt, endAt, maxUses, null, description);
        }
    }
}
