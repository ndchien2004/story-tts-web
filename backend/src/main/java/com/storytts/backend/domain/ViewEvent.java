package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng view_events — mỗi lần mở một chương để đọc hoặc để nghe là một dòng.
 *
 * <p>Cần bảng riêng vì {@code stories.view_count} và {@code chapters.view_count} chỉ là
 * số cộng dồn: nhìn vào đó biết được tổng, nhưng không tách được ra "ngày nào bao nhiêu
 * lượt" — mà biểu đồ theo ngày ở mục 4.7 của đề bài lại hỏi đúng câu đó.
 *
 * <p>Không có khóa ngoại tới {@code users} và cũng cố ý cho phép {@code userId} null:
 * Khách chưa đăng nhập vẫn đọc được chương công khai và lượt đọc đó vẫn phải được đếm.
 * Lưu id trần thay vì quan hệ để việc ghi log không kéo theo một lượt truy vấn người dùng.
 */
@Entity
@Table(
        name = "view_events",
        indexes = {
                @Index(name = "idx_view_events_created", columnList = "created_at"),
                @Index(name = "idx_view_events_story", columnList = "story_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "chapter_id", nullable = false)
    private Long chapterId;

    /** Null nghĩa là Khách chưa đăng nhập. */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ViewType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
