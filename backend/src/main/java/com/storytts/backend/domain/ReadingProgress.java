package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng reading_progress (mục 8 đề bài).
 * Dùng cho cả tiến độ đọc (mục 4.3 [NC]) lẫn vị trí đang nghe (mục 4.4 [NC]),
 * đồng thời là dữ liệu đầu vào cho gợi ý truyện tương tự (mục 4.3 [NC]).
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_progress_chapter"))
    private Chapter chapter;

    /** Vị trí cuộn khi đọc (phần trăm 0-100). */
    @Column(name = "last_position", nullable = false)
    @Builder.Default
    private Integer lastPosition = 0;

    /** Giây thứ bao nhiêu của audio khi nghe dở (mục 4.4 [NC] đề bài). */
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
