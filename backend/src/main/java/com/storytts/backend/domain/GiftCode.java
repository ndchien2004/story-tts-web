package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Một mã đổi Xu do quản trị viên phát ra.
 *
 * <h3>Ba thứ giữ cho nó không phát quá tay</h3>
 * <ol>
 *   <li>{@code UNIQUE(code)} trên <b>giá trị đã chuẩn hóa</b> — xem
 *       {@link #code}. Không có nó thì "SUMMER2026" và "summer2026" là hai mã
 *       khác nhau, và một người đổi được cả hai.</li>
 *   <li>{@code UNIQUE(gift_code_id, user_id)} ở {@link GiftCodeRedemption} —
 *       một tài khoản một lần, do cơ sở dữ liệu quyết định chứ không do một
 *       phép kiểm tra trong Java.</li>
 *   <li>Câu {@code UPDATE ... WHERE used_count < max_uses} ở
 *       {@code GiftCodeRepository.claimUse} — trần lượt đổi, cũng do cơ sở dữ
 *       liệu quyết định.</li>
 * </ol>
 *
 * <p>Cả ba đều là cơ chế của cơ sở dữ liệu, và đó là điểm chính. Kiểm tra trước
 * bằng Java không giải quyết được gì khi hai request chạy song song: cả hai đều
 * thấy "chưa đổi" và "còn lượt" trước khi bên nào kịp ghi. Cùng một lập luận
 * với {@code ChapterEntitlement}, và cùng hình dạng lời giải.
 *
 * <h3>{@link #usedCount} là con số đếm sẵn, không phải một câu COUNT</h3>
 * Nó dư thừa so với việc đếm dòng ở {@code gift_code_redemptions}, và cố ý dư
 * thừa. Một mã phát trong sự kiện nhận hàng nghìn request gần như cùng lúc; đếm
 * lại toàn bộ bảng đổi mã ở mỗi lần là quét một tập đang lớn dần ngay trong
 * đường nóng. Hai con số được ghi trong cùng một giao dịch nên chúng không lệch
 * nhau được — bất biến là
 * {@code used_count = COUNT(gift_code_redemptions WHERE gift_code_id = id)}.
 *
 * <h3>Ba trường cho phép null, và null có nghĩa</h3>
 * {@link #startAt} null là "có hiệu lực ngay", {@link #endAt} null là "không hết
 * hạn", {@link #maxUses} null là "không giới hạn lượt". Dùng null chứ không dùng
 * giá trị đánh dấu như {@code max_uses = -1}: một con số âm nằm trong cột đếm
 * lượt sẽ lọt vào mọi phép so sánh và mọi báo cáo, còn null thì bị SQL loại ra
 * một cách rõ ràng ở đúng chỗ cần loại.
 */
@Entity
@Table(
        name = "gift_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_gift_codes_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_gift_codes_enabled_window", columnList = "enabled, start_at, end_at"),
                @Index(name = "idx_gift_codes_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã đã chuẩn hóa: viết hoa, bỏ khoảng trắng hai đầu.
     *
     * <p>Chỉ có một dạng được lưu và cũng chỉ có một dạng được tra, nên
     * {@code UNIQUE} ở đây thật sự chặn được mã trùng — chứ không phải chặn được
     * mỗi trường hợp người tạo gõ y hệt nhau. Việc chuẩn hóa nằm ở
     * {@code GiftCodes.normalize} và không được viết lại ở chỗ nào khác: hai
     * cách chuẩn hóa hơi khác nhau ở đường tạo và đường đổi là cách tạo ra một
     * mã không bao giờ đổi được.
     */
    @Column(nullable = false, length = 64)
    private String code;

    /** Số Xu người đổi nhận được. Luôn dương — xem ràng buộc CHECK trong V13. */
    @Column(name = "coin_amount", nullable = false)
    private long coinAmount;

    /** Bắt đầu đổi được; null là có hiệu lực ngay. */
    @Column(name = "start_at")
    private Instant startAt;

    /** Hết hạn; null là không hết hạn. */
    @Column(name = "end_at")
    private Instant endAt;

    /** Trần số lượt đổi; null là không giới hạn. */
    @Column(name = "max_uses")
    private Integer maxUses;

    /** Số lượt đã đổi. Chỉ tăng, và chỉ tăng qua {@code claimUse}. */
    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    /**
     * Công tắc của quản trị viên.
     *
     * <p>Tắt một mã đã có người đổi là cách đúng để ngừng phát: xóa nó đi sẽ kéo
     * theo cả lịch sử đổi mã, mà lịch sử ấy giải thích những dòng Xu đã vào ví
     * người ta — xem {@code GiftCodeAdminService.delete}.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Ghi chú nội bộ; người đọc không bao giờ thấy. */
    @Column(length = 300)
    private String description;

    /**
     * Ai đã tạo mã này.
     *
     * <p>Cho null vì tài khoản quản trị có thể bị xóa về sau, và mã thì ở lại —
     * khóa ngoại của nó là {@code ON DELETE SET NULL}. Mất tên người tạo còn hơn
     * mất cả cái mã đang giải thích một loạt dòng sổ cái.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by",
            foreignKey = @ForeignKey(name = "fk_gift_codes_creator"))
    private User createdBy;

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

    /* ------------------------------------------------------------------ */
    /* Suy ra                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Tình trạng tại một thời điểm cho trước.
     *
     * <p>Nhận {@code now} vào chứ không tự gọi {@code Instant.now()}: một bảng
     * quản trị hiện 50 dòng phải xét cả 50 dòng theo cùng một mốc, nếu không thì
     * hai dòng cạnh nhau có thể được xét ở hai thời điểm khác nhau. Và kiểm thử
     * cần dời được cái mốc ấy mà không phải dời đồng hồ của máy.
     *
     * <p>Thứ tự xét trùng đúng với thứ tự kiểm tra lúc đổi mã ở
     * {@code GiftCodeError} — xem ghi chú ở {@link GiftCodeStatus}.
     */
    public GiftCodeStatus status(Instant now) {
        if (!enabled) {
            return GiftCodeStatus.DISABLED;
        }
        if (startAt != null && now.isBefore(startAt)) {
            return GiftCodeStatus.SCHEDULED;
        }
        if (endAt != null && now.isAfter(endAt)) {
            return GiftCodeStatus.EXPIRED;
        }
        if (isExhausted()) {
            return GiftCodeStatus.EXHAUSTED;
        }
        return GiftCodeStatus.ACTIVE;
    }

    /** Tình trạng ngay bây giờ. */
    public GiftCodeStatus status() {
        return status(Instant.now());
    }

    /** Đã dùng hết lượt chưa. Mã không giới hạn thì không bao giờ hết. */
    public boolean isExhausted() {
        return maxUses != null && usedCount >= maxUses;
    }

    /**
     * Số lượt còn lại, hoặc null nếu không giới hạn.
     *
     * <p>Cố ý <b>không</b> có phương thức "tổng Xu đã phát" ở đây. Nó tính được
     * bằng {@code coinAmount * usedCount}, và phép tính ấy sai ngay khi quản trị
     * viên sửa mệnh giá giữa chừng: những lượt đổi trước đó đã nhận số Xu cũ.
     * Con số đúng là {@code SUM(gift_code_redemptions.coin_amount)}, vì mỗi dòng
     * đổi mã chép lại số Xu nó thật sự đã phát — xem
     * {@link GiftCodeRedemption#coinAmount}.
     */
    public Integer remainingUses() {
        return maxUses == null ? null : Math.max(0, maxUses - usedCount);
    }
}
