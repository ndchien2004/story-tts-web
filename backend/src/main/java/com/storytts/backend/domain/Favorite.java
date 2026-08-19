package com.storytts.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import lombok.*;

import java.time.Instant;

/** Bảng favorites — đánh dấu truyện yêu thích. */
@Entity
@Table(
        name = "favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorites_user_story",
                columnNames = {"user_id", "story_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_favorites_user"))
    private User user;

    /**
     * Truyện được đánh dấu.
     *
     * <p>Xóa truyện thì lượt yêu thích nó cũng mất theo — xem ghi chú ở
     * { ReadingProgress#getChapter()} và migration V12. Thiếu quy tắc này
     * thì một lượt bấm yêu thích là đủ để truyện không xóa được nữa.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, foreignKey = @ForeignKey(name = "fk_favorites_story"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Story story;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
