package com.storytts.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một lượt dùng AI đã xảy ra — bảng {@code ai_usage}.
 *
 * <h3>Vì sao hạn mức không đếm trên chính thứ AI tạo ra</h3>
 * Trước đây hạn mức "Nghe bằng AI" là một phép đếm trên {@code audio_files},
 * còn bản audio người đọc tự dựng thì bị dọn mỗi khi họ mở phiên đăng nhập
 * mới. Hai việc ấy đứng cạnh nhau biến hạn mức thành thứ nạp lại được bằng
 * cách đăng xuất rồi đăng nhập.
 *
 * <p>Bảng này tồn tại để tách hai vòng đời đang bị buộc vào nhau: bản audio là
 * <b>tài sản</b>, dọn đi được; một lượt đã dùng là <b>sự kiện</b>, không xóa
 * được nữa. Không có nghiệp vụ nào xóa dòng ở đây — kể cả lúc hoàn lượt, thứ
 * được ghi thêm là {@link #refundedAt} chứ không phải một lệnh xóa.
 *
 * <p>{@link #audioFileId} là số thường chứ không phải quan hệ JPA, và khóa
 * ngoại của nó có {@code ON DELETE SET NULL}: dòng sổ phải sống sót qua chính
 * bản audio đã sinh ra nó, nên một quan hệ có ràng buộc chặt ở đây sẽ chống lại
 * mục đích của cả bảng.
 */
@Entity
@Table(name = "ai_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiUsageKind kind;

    /** Chương đã tiêu lượt này; chỉ để tra cứu, không tham gia phép đếm. */
    @Column(name = "chapter_id")
    private Long chapterId;

    /** Bản audio lượt này đã trả tiền để dựng, hoặc null với trợ lý. */
    @Column(name = "audio_file_id")
    private Long audioFileId;

    /** Đã hoàn thì không còn tính vào hạn mức nữa. */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason", length = 120)
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }
}
