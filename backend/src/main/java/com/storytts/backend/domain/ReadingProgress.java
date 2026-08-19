package com.storytts.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import lombok.*;

import java.time.Instant;

/**
 * Bảng reading_progress.
 * Dùng cho cả tiến độ đọc lẫn vị trí đang nghe,
 * đồng thời là dữ liệu đầu vào cho gợi ý truyện tương tự.
 */
@Entity
@Table(
        name = "reading_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_progress_user_chapter",
                columnNames = {"user_id", "chapter_id"}
        ),
        indexes = @Index(name = "idx_progress_user_updated", columnList = "user_id, updated_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_progress_user"))
    private User user;

    /**
     * Chương đang đọc dở.
     *
     * <p>{@code @OnDelete} nói cho cơ sở dữ liệu biết phải làm gì khi chương bị
     * xóa, thay vì để mặc định RESTRICT chặn lệnh xóa lại. Trước đây thiếu nó,
     * và hậu quả là <b>một người đã đọc chương thì Admin không xóa chương ấy
     * được nữa</b> — lệnh xóa hỏng với lỗi ràng buộc, còn trên màn hình chỉ hiện
     * "Đã có lỗi xảy ra ở máy chủ".
     *
     * <p>Cùng quy tắc ấy nằm trong migration V12 cho cơ sở dữ liệu đang chạy.
     * Khai báo cả ở đây vì lược đồ của test do chính entity sinh ra, nên nếu chỉ
     * sửa migration thì test sẽ chạy trên một lược đồ khác với lược đồ thật —
     * đúng loại khác biệt khiến lỗi này lọt qua ngay từ đầu.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_progress_chapter"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Chapter chapter;

    /** Vị trí cuộn khi đọc (phần trăm 0-100). */
    @Column(name = "last_position", nullable = false)
    @Builder.Default
    private Integer lastPosition = 0;

    /** Giây thứ bao nhiêu của audio khi nghe dở. */
    @Column(name = "audio_position_seconds", nullable = false)
    @Builder.Default
    private Integer audioPositionSeconds = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
