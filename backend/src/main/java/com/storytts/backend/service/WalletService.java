package com.storytts.backend.service;

import com.storytts.backend.domain.Wallet;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransaction;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.InsufficientCoinsException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.WalletRepository;
import com.storytts.backend.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Cộng và trừ Xu — và giữ cho số dư khớp với sổ cái.
 *
 * <h3>Đâu là nguồn sự thật</h3>
 * {@code wallets.balance} là thứ quyết định có đủ tiền hay không: nó có chỉ mục,
 * đọc một lần là xong, và là thứ câu UPDATE có điều kiện xét tới. Sổ cái là thứ
 * <i>giải thích</i> nó — không phải thứ được cộng lại mỗi lần cần biết số dư, vì
 * làm thế là quét toàn bộ lịch sử của một người ở mỗi lần mở chương.
 *
 * <p>Hai thứ ấy không lệch nhau được, vì mỗi phép cộng trừ ở đây ghi cả hai bên
 * trong <b>cùng một giao dịch</b>: hoặc cả số dư lẫn dòng sổ cái cùng vào, hoặc
 * không gì vào cả. Bất biến là {@code SUM(wallet_transactions.amount) =
 * wallets.balance}, và nó kiểm được bằng một câu truy vấn.
 *
 * <p>Nếu chúng vẫn lệch — vì một lần can thiệp tay vào cơ sở dữ liệu chẳng hạn —
 * thì {@code balanceBefore}/{@code balanceAfter} trên từng dòng chỉ ra chỗ lệch
 * bắt đầu từ đâu, thay vì chỉ nói rằng có lệch.
 *
 * <h3>Các phương thức ở đây là viên gạch, không phải thao tác nghiệp vụ</h3>
 * Chúng dùng propagation mặc định ({@code REQUIRED}), nên khi được gọi từ trong
 * một giao dịch đang mở — như lúc mua chương — chúng nhập vào giao dịch ấy. Đó là
 * điều cần: trừ Xu và cấp quyền đọc phải cùng sống hoặc cùng chết.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /* ------------------------------------------------------------------ */
    /* Đọc                                                                 */
    /* ------------------------------------------------------------------ */

    /** Số dư hiện tại. Người chưa từng có ví thì số dư là 0, không phải lỗi. */
    @Transactional(readOnly = true)
    public long balanceOf(Long userId) {
        return walletRepository.findBalanceByUserId(userId).orElse(0L);
    }

    /**
     * Tổng sổ cái của một người — vế đối chiếu của {@link #balanceOf}.
     *
     * <p>Hai con số này phải luôn bằng nhau. Dùng cho kiểm thử và cho việc dò
     * lệch, không dùng trong đường quyết định chi tiêu.
     */
    @Transactional(readOnly = true)
    public long ledgerTotalOf(Long userId) {
        return transactionRepository.sumAmountByUserId(userId);
    }

    /* ------------------------------------------------------------------ */
    /* Ghi                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Cộng Xu vào ví và ghi một dòng sổ cái.
     *
     * @param amount phải dương
     * @return số dư sau khi cộng
     */
    @Transactional
    public long credit(Long userId, long amount, WalletTransactionType type,
                       WalletReferenceType referenceType, Long referenceId, String description) {
        requirePositive(amount);
        ensureWallet(userId);

        if (walletRepository.credit(userId, amount, Instant.now()) == 0) {
            // ensureWallet vừa bảo đảm ví tồn tại, nên tới đây mà vẫn 0 dòng thì
            // có gì đó sai ở tầng dưới chứ không phải một trạng thái nghiệp vụ.
            throw new IllegalStateException("Không cộng được Xu cho người dùng " + userId);
        }

        // Đọc *sau* câu UPDATE chứ không phải trước: câu ấy đã khóa dòng ví cho
        // tới hết giao dịch này, nên con số đọc về là con số cuối cùng, và số dư
        // trước đó suy ngược ra được chính xác. Đọc trước thì một giao dịch khác
        // xen vào giữa là hai cột balance_before/after ghi sai.
        long after = balanceOf(userId);
        record(userId, type, amount, after - amount, after, referenceType, referenceId, description);
        return after;
    }

    /**
     * Trừ Xu khỏi ví và ghi một dòng sổ cái.
     *
     * <p>Không đủ số dư thì ném {@link InsufficientCoinsException} và <b>không ghi
     * gì cả</b> — kể cả một dòng sổ cái "đã thử". Sổ cái ghi những gì đã xảy ra,
     * không ghi những gì đã bị từ chối.
     *
     * @param amount phải dương; dấu âm được đặt vào sổ cái ở đây
     * @return số dư sau khi trừ
     */
    @Transactional
    public long debit(Long userId, long amount, WalletTransactionType type,
                      WalletReferenceType referenceType, Long referenceId, String description) {
        requirePositive(amount);

        // Câu UPDATE có điều kiện vừa kiểm tra vừa trừ. Nhận về 0 dòng nghĩa là
        // không đủ — hoặc vì người này thật sự hết Xu, hoặc vì một request song
        // song vừa tiêu mất phần còn lại. Hai trường hợp ấy là cùng một câu trả
        // lời cho người dùng, và cả hai đều không làm số dư âm được.
        if (walletRepository.debit(userId, amount, Instant.now()) == 0) {
            throw new InsufficientCoinsException(amount, balanceOf(userId));
        }

        long after = balanceOf(userId);
        record(userId, type, -amount, after + amount, after, referenceType, referenceId, description);
        return after;
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Bảo đảm người này có ví.
     *
     * <p>Tạo lúc cần chứ không lúc đăng ký: những tài khoản đã tồn tại từ trước
     * tính năng này cũng phải dùng được ví, và một lần chạy vá dữ liệu chỉ để
     * chèn sẵn những dòng số dư 0 là việc không cần thiết.
     *
     * <p>Hai request đầu tiên của cùng một người có thể cùng thấy "chưa có ví" và
     * cùng chèn. Ràng buộc duy nhất trên {@code user_id} cho đúng một bên thắng;
     * bên thua đọc lại và dùng ví vừa được tạo.
     */
    private void ensureWallet(Long userId) {
        if (walletRepository.findBalanceByUserId(userId).isPresent()) {
            return;
        }
        try {
            walletRepository.saveAndFlush(Wallet.builder()
                    .user(userRepository.getReferenceById(userId))
                    .balance(0L)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Ví của người dùng {} vừa được một request khác tạo", userId);
        }
    }

    private void record(Long userId, WalletTransactionType type, long signedAmount,
                        long before, long after, WalletReferenceType referenceType,
                        Long referenceId, String description) {
        transactionRepository.save(WalletTransaction.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .amount(signedAmount)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .build());
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BadRequestException("Số Xu phải là số dương.");
        }
    }
}
