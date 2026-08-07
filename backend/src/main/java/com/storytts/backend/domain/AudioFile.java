package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng audio_files.
 * Một chương có thể có nhiều bản audio: 1 bản UPLOAD do admin thu âm sẵn,
 * và các bản TTS sinh ra theo từng giọng đọc / tốc độ (dùng làm cache — mục 4.5).
 */
@Entity
@Table(
        name = "audio_files",
        indexes = @Index(name = "idx_audio_chapter", columnList = "chapter_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_audio_chapter"))
    private Chapter chapter;

    /** Tên file tương đối trong thư mục uploads (không lộ đường dẫn tuyệt đối ra client). */
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AudioStatus status = AudioStatus.READY;

    /** Độ dài audio tính bằng giây (nếu xác định được). */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    @Builder.Default
    private String contentType = "audio/mpeg";

    /** Voice code passed to the provider; null for an uploaded recording. */
    @Column(length = 80)
    private String voice;

    /** Reading speed, -3 to 3; null for an uploaded recording. */
    private Integer speed;

    /**
     * Which backend produced the audio.
     *
     * Recorded because a fallback means the result may not come from the
     * provider that was asked for.
     */
    @Column(length = 40)
    private String provider;

    /** Thông báo lỗi khi status = FAILED, để frontend hiển thị rõ ràng. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

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
