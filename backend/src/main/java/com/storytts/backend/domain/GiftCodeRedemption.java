package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * "Người này đã đổi mã kia" — một dòng, vĩnh viễn.
 *
 * <h3>Ràng buộc quan trọng nhất của cả tính năng</h3>
 * {@code UNIQUE(gift_code_id, user_id)}. Nó là thứ khiến bấm "Đổi mã" ba lần
 * liên tiếp — hoặc mở hai tab cùng gửi một lúc — không thể cộng Xu ba lần.
 *
 * <p>Không phải nhờ mã nguồn kiểm tra trước. Mã nguồn <i>có</i> kiểm, nhưng phép
 * kiểm ấy chỉ để trả lời nhanh và trả lời đẹp cho trường hợp thường gặp: hai
 * request chạy song song thì cả hai đều thấy "chưa đổi" trước khi bên nào kịp
 * ghi. Thứ chặn được là cơ sở dữ liệu từ chối dòng thứ hai, và request thua cuộc
 * bị cuộn ngược nguyên vẹn — kể cả số Xu nó vừa cộng và lượt nó vừa chiếm.
 *
 * <h3>Vì sao chép lại {@link #coinAmount}</h3>
 * Nó đọc được từ {@code gift_codes.coin_amount} qua khóa ngoại, nên thoạt nhìn
 * là thừa. Nhưng quản trị viên sửa được mệnh giá của một mã đang chạy, và lúc ấy
 * cột bên kia không còn nói đúng về những lượt đã đổi trước đó nữa. Số Xu đã vào
 * ví một người là một sự việc đã xảy ra; nó phải được lưu ở chỗ mà một lần sửa
 * cấu hình về sau không với tới được. Cùng lý do với
 * {@code ChapterEntitlement.coinsSpent} và với việc đơn hàng chép lại giá gói.
 *
 * <h3>Vì sao chỉ có {@code created_at}</h3>
 * Một cột {@code redeemed_at} riêng sẽ luôn bằng nó: dòng này chỉ được tạo ra ở
 * đúng một chỗ, và đúng vào lúc việc đổi mã xảy ra. Hai cột luôn bằng nhau là
 * hai cột có thể lệch nhau. DTO gọi nó là {@code redeemedAt} cho đúng nghĩa với
 * bên đọc.
 */
@Entity
@Table(
        name = "gift_code_redemptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gift_redemption_code_user",
                columnNames = {"gift_code_id", "user_id"}
        ),
        indexes = @Index(name = "idx_gift_redemption_user", columnList = "user_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftCodeRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_code_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gift_redemption_code"))
    private GiftCode giftCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gift_redemption_user"))
    private User user;

    /** Số Xu lượt đổi này thật sự đã phát. Xem ghi chú ở đầu lớp. */
    @Column(name = "coin_amount", nullable = false)
    private long coinAmount;

    /** Lúc đổi. Xem ghi chú ở đầu lớp về việc không có {@code redeemed_at}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
