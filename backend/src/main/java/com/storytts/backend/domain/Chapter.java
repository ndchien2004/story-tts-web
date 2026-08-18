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

    /**
     * Phiên bản nghiệp vụ của {@link #content}, tăng đúng khi nội dung đổi.
     *
     * <p>Đây là <b>source of truth</b> để trả lời "bản audio này còn là bản hiện
     * tại của chương không". Một bản audio ghi lại phiên bản nó được dựng từ đó;
     * hai con số bằng nhau thì bản ấy hợp lệ, khác nhau thì không — không có
     * đường thứ ba, và không có chuyện lấy tạm bản cũ cho người nghe.
     *
     * <p>Chỉ nội dung mới làm nó tăng. Sửa tiêu đề, đổi mức khóa, đặt giá Xu hay
     * cộng lượt xem đều không: không thứ nào trong số đó đổi những chữ đem đi
     * đọc, nên tăng phiên bản vì chúng chỉ có tác dụng vứt bỏ một bản audio còn
     * dùng tốt. Xem {@code ChapterService.update}.
     *
     * <p>Không dùng {@link #updatedAt} cho việc này: cột ấy nhúc nhích theo mọi
     * lần ghi, kể cả lần không đụng tới nội dung, và hai lần ghi trong cùng một
     * mili giây thì không phân biệt được.
     */
    @Column(name = "content_version", nullable = false)
    @Builder.Default
    private int contentVersion = 1;

    /**
     * Cột optimistic locking của Hibernate — không phải phiên bản nội dung.
     *
     * <p>Hai Admin cùng bấm lưu một chương: cả hai đọc {@code contentVersion = 7},
     * cả hai ghi 8, và kết quả là hai nội dung khác nhau cùng mang nhãn v8. Từ
     * lúc ấy con số phiên bản không còn xác định được nội dung nữa, và mọi thứ
     * dựng trên nó — bản audio nào còn hợp lệ, trình duyệt nào đang xem bản cũ —
     * đều sai theo mà không có gì báo.
     *
     * <p>Cột này chặn đúng chỗ đó: lần ghi thứ hai thấy số phiên bản đã đổi và
     * hỏng ngay tại cơ sở dữ liệu, thành một câu trả lời 409 rõ ràng thay vì một
     * lần mất dữ liệu im lặng. Bên gọi không phải gửi gì thêm — xem
     * {@code GlobalExceptionHandler}.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private long version = 0L;

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
