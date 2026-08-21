package com.storytts.backend.service;

import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.exception.InsufficientCoinsException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.WalletRepository;
import com.storytts.backend.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ví Xu trên một cơ sở dữ liệu thật.
 *
 * <p>Chạy trên H2 chứ không trên mock, vì thứ đang được kiểm chính là hành vi của
 * cơ sở dữ liệu: câu {@code UPDATE ... WHERE balance >= :số} có thật sự trả về 0
 * dòng khi không đủ tiền hay không. Mock hóa chỗ ấy là mock hóa đúng cái cần kiểm.
 */
@DataJpaTest
@Import(WalletService.class)
class WalletJpaTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletTransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .displayName("Người Đọc")
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build()).getId();
    }

    @Test
    @DisplayName("người chưa từng nạp có số dư 0, không phải lỗi")
    void aFreshUserHasZero() {
        assertThat(walletService.balanceOf(userId)).isZero();
    }

    @Test
    @DisplayName("nạp lần đầu tự tạo ví")
    void theFirstCreditCreatesTheWallet() {
        assertThat(credit(100)).isEqualTo(100L);
        assertThat(walletRepository.findByUserId(userId)).isPresent();
    }

    @Test
    @DisplayName("số dư luôn khớp tổng sổ cái sau một chuỗi cộng trừ")
    void balanceAlwaysMatchesTheLedger() {
        credit(100);
        debit(10);
        debit(20);
        credit(50);

        // +100 -10 -20 +50 = 120
        assertThat(balanceFromDatabase()).isEqualTo(120L);
        assertThat(walletService.ledgerTotalOf(userId)).isEqualTo(120L);
        assertThat(transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("mỗi dòng sổ cái nối liền với dòng trước: after của dòng này là before của dòng sau")
    void theLedgerFormsAnUnbrokenChain() {
        credit(100);
        debit(30);
        credit(5);

        var rows = transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                        userId, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent();

        // Trang trả về mới nhất trước, nên duyệt ngược để đi theo dòng thời gian.
        long expected = 0L;
        for (int i = rows.size() - 1; i >= 0; i--) {
            var row = rows.get(i);
            assertThat(row.getBalanceBefore()).isEqualTo(expected);
            assertThat(row.getBalanceAfter()).isEqualTo(expected + row.getAmount());
            expected = row.getBalanceAfter();
        }
        assertThat(expected).isEqualTo(balanceFromDatabase());
    }

    @Test
    @DisplayName("không đủ Xu thì bị từ chối, và không ghi dòng sổ cái nào")
    void anInsufficientDebitWritesNothing() {
        credit(30);

        assertThatThrownBy(() -> debit(50))
                .isInstanceOfSatisfying(InsufficientCoinsException.class, ex -> {
                    assertThat(ex.getRequired()).isEqualTo(50L);
                    assertThat(ex.getBalance()).isEqualTo(30L);
                });

        assertThat(balanceFromDatabase()).isEqualTo(30L);
        // Sổ cái ghi những gì đã xảy ra, không ghi những gì đã bị từ chối.
        assertThat(walletService.ledgerTotalOf(userId)).isEqualTo(30L);
    }

    @Test
    @DisplayName("câu trừ có điều kiện: trừ đúng bằng số dư thì được, hơn một Xu là không")
    void theConditionalDebitIsExact() {
        credit(50);
        entityManager.flush();
        entityManager.clear();

        assertThat(walletRepository.debit(userId, 51, Instant.now())).isZero();
        assertThat(walletRepository.debit(userId, 50, Instant.now())).isEqualTo(1);

        entityManager.flush();
        entityManager.clear();
        assertThat(balanceFromDatabase()).isZero();
    }

    /**
     * Hai lệnh trừ liên tiếp với số dư chỉ đủ cho một — mô phỏng hai request song
     * song sau khi cả hai đã đọc được cùng một số dư.
     *
     * <p>Chỗ quan trọng: lệnh thứ hai không cần biết lệnh thứ nhất đã chạy. Điều
     * kiện nằm trong chính câu UPDATE, nên nó tự đọc lại số dư đã giảm.
     */
    @Test
    @DisplayName("hai lần mua với số Xu chỉ đủ một lần: đúng một lần thành công")
    void onlyOneOfTwoCompetingDebitsSucceeds() {
        credit(10);
        entityManager.flush();
        entityManager.clear();

        int first = walletRepository.debit(userId, 10, Instant.now());
        int second = walletRepository.debit(userId, 10, Instant.now());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        entityManager.flush();
        entityManager.clear();
        assertThat(balanceFromDatabase()).isZero();
    }

    /* ------------------------------------------------------------------ */

    private long credit(long amount) {
        return walletService.credit(userId, amount, WalletTransactionType.DEPOSIT,
                WalletReferenceType.PAYMENT_ORDER, 1L, "Nạp thử");
    }

    private long debit(long amount) {
        return walletService.debit(userId, amount, WalletTransactionType.PURCHASE_CHAPTER,
                WalletReferenceType.CHAPTER, 2L, "Mua thử");
    }

    private long balanceFromDatabase() {
        entityManager.flush();
        entityManager.clear();
        return walletService.balanceOf(userId);
    }
}
