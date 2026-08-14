package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng bgm_tracks — một bản nhạc nền quản trị viên tải lên cho người nghe chọn.
 *
 * <p>Cố tình không dính dáng gì tới {@link Chapter} hay {@link Story}: nhạc nền
 * là thứ người nghe chọn cho buổi nghe của họ, không phải thuộc tính của một
 * chương. Cùng một bản nhạc chạy được với mọi chương, và lựa chọn ấy nằm trong
 * trình duyệt của người nghe chứ không nằm ở máy chủ.
 *
 * <p>Chỉ tên file được lưu, y như {@link AudioFile}; việc đổi tên ấy ra đường
 * dẫn tuyệt đối luôn đi qua {@code StorageService}.
 */
@Entity
@Table(
        name = "bgm_tracks",
        indexes = @Index(name = "idx_bgm_active_order", columnList = "active, sort_order")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BgmTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    /** Ghi công tác giả, hiện dưới ô chọn nhạc. Null khi bản nhạc không đòi hỏi. */
    @Column(length = 255)
    private String credit;

    /** Tên file trong thư mục nhạc nền (không lộ đường dẫn tuyệt đối ra client). */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", length = 100)
    @Builder.Default
    private String contentType = "audio/mpeg";

    @Column(name = "file_size")
    private Long fileSize;

    /** Độ dài tính bằng giây, nếu xác định được — chỉ để hiện trong bảng quản trị. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Còn nằm trong danh sách người nghe chọn hay không.
     *
     * <p>Tắt chứ không xóa là thao tác lùi được: bản nhạc rút khỏi kho vì bản
     * quyền thường được đưa lại sau, và file vẫn còn nguyên trên đĩa.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Thứ tự trong ô chọn; nhỏ hơn thì lên trước. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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
}
