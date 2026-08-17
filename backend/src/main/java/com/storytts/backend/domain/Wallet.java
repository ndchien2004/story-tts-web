package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Ví Xu của một người dùng — một người một ví.
 *
 * <h3>Vì sao số dư nằm ở đây chứ không phải một cột trên {@code users}</h3>
 * Số dư bị ghi bởi những câu UPDATE có điều kiện chạy đồng thời với nhau, và
 * mỗi câu ấy khóa dòng cho tới hết giao dịch. Để nó ở {@code users} nghĩa là mỗi
 * lần ai đó mở một chương lại khóa đúng cái dòng mà việc đăng nhập, đọc hồ sơ và
 * kiểm tra VIP đều đang đọc.
 *
 * <h3>Số dư và sổ cái</h3>
 * Cột {@link #balance} là thứ quyết định có đủ tiền hay không;
 * {@link WalletTransaction} là thứ giải thích vì sao nó bằng bấy nhiêu. Hai thứ
 * luôn được ghi trong cùng một giao dịch, và bất biến giữa chúng là
 * {@code SUM(wallet_transactions.amount) = wallets.balance}.
 *
 * <p><b>Không có setter cho {@link #balance}.</b> Cộng trừ Xu đi qua những câu
 * UPDATE nguyên tử trong {@code WalletRepository}, không đi qua bộ nhớ — xem
 * {@code WalletService}. Một setter ở đây là lời mời viết
 * {@code wallet.setBalance(wallet.getBalance() - giá)}, và đó đúng là cách làm
 * mất Xu khi hai người bấm mua cùng lúc.
 */
@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallets_user", columnNames = "user_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wallets_user"))
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private long balance = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
