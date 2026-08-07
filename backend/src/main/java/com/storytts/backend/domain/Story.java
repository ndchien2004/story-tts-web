package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bảng stories (mục 8 đề bài). */
@Entity
@Table(
        name = "stories",
        indexes = {
                @Index(name = "idx_stories_title", columnList = "title"),
                @Index(name = "idx_stories_genre", columnList = "genre_id"),
                @Index(name = "idx_stories_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", foreignKey = @ForeignKey(name = "fk_stories_author"))
    private Author author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", foreignKey = @ForeignKey(name = "fk_stories_genre"))
    private Genre genre;

    /** Đường dẫn ảnh bìa (URL ngoài hoặc file đã upload trong /uploads/covers). */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StoryStatus status = StoryStatus.ONGOING;

    /** Dùng cho sắp xếp "phổ biến nhất" (mục 4.3 đề bài). */
    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterNumber ASC")
    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

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
