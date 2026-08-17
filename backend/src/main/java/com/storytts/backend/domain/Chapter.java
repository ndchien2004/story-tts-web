package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bảng chapters.
 * Cột {@link #accessLevel} là cơ chế khóa chương do Admin đặt.
 */
@Entity
@Table(
        name = "chapters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chapters_story_number",
                columnNames = {"story_id", "chapter_number"}
        ),
        indexes = @Index(name = "idx_chapters_story", columnList = "story_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chapters_story"))
    private Story story;

    @Column(nullable = false, length = 255)
    private String title;

    /** Nội dung văn bản của chương — cũng là đầu vào cho TTS. */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    /** Mức khóa chương: PUBLIC / MEMBER / VIP — Admin toàn quyền quyết định. */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    @Builder.Default
    private AccessLevel accessLevel = AccessLevel.PUBLIC;

    /**
     * Giá mở chương bằng Xu. 0 nghĩa là không bán lẻ — đây là mặc định.
     *
     * <p>Cột này <b>không</b> thay thế {@link #accessLevel}; nó vuông góc với
     * accessLevel, và đó là chủ ý. Hai cột trả lời hai câu khác nhau:
     * accessLevel nói ai được phép nhìn tới chương, coinPrice nói mở nó tốn bao
     * nhiêu. Trộn chúng vào một enum thì mỗi cách bán mới lại nhân đôi số giá trị
     * (VIP, COIN, VIP_HOẶC_COIN, MEMBER_VÀ_COIN…).
     *
     * <p>Để rời nhau thì bốn trạng thái cần đến rơi ra tự nhiên:
     * {@code PUBLIC+0} là đọc tự do, {@code MEMBER+0} cần đăng nhập,
     * {@code VIP+0} là chỉ VIP (đúng hành vi cũ), và {@code VIP+50} là VIP đọc
     * miễn phí còn người thường trả 50 Xu.
     *
     * <p>Mọi chương đã có đều mang giá 0, nên không chương nào đổi hành vi vì
     * tính năng này. Xem {@code ChapterAccessService}.
     */
    @Column(name = "coin_price", nullable = false)
    @Builder.Default
    private long coinPrice = 0L;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AudioFile> audioFiles = new ArrayList<>();

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
