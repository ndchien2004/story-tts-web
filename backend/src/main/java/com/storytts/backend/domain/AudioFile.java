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
        indexes = {
                @Index(name = "idx_audio_chapter", columnList = "chapter_id"),
                // Hạn mức mỗi ngày đếm theo (ai, lúc nào) nên hai cột đó đi cùng nhau.
                @Index(name = "idx_audio_requested_by", columnList = "requested_by, created_at")
        }
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
     * Băm SHA-256 của nội dung chương tại thời điểm dựng bản audio này.
     *
     * <p>Khóa cache (chương, giọng, tốc độ) không nhìn thấy nội dung, nên sửa
     * chương rồi bấm tạo lại sẽ nhận đúng bản audio đọc theo bản cũ. Cột này là
     * thứ phát hiện ra điều đó. Null với bản tải lên, và với những bản đã tạo
     * trước khi có cột này — chúng bị coi là cũ và được dựng lại đúng một lần.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

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

    /**
     * Số chữ có mốc thời gian, hay null khi bản này không có mốc nào.
     *
     * <p>Bản thân mốc thời gian nằm ở bảng {@link AudioTranscript} — ba trăm KB
     * JSON để chung trên hàng này thì mỗi lần liệt kê bản audio của một chương
     * là một lần kéo nó về rồi vứt đi. Còn danh sách track thì vẫn cần trả lời
     * "bản này tô sáng được không", và câu ấy chỉ tốn một số nguyên.
     *
     * <p>Null với bản admin tải lên (không ai biết giọng ấy đọc tới đâu) và với
     * những bản dựng trước khi có cột này — cả hai đều nghe bình thường.
     */
    @Column(name = "transcript_words")
    private Integer transcriptWords;

    /**
     * Người đọc đã bấm "Nghe bằng AI" để sinh ra bản này.
     *
     * <p>Null với bản tải lên, với các bản do khu quản trị dựng, và với những bản
     * có trước khi có cột này. Hạn mức mỗi ngày đếm đúng những dòng có tên người
     * — nhờ đó hai điều sau là hệ quả của cấu trúc chứ không phải một quy tắc
     * phải nhớ: nghe lại bản đã có không tốn lượt nào (vì không sinh dòng nào),
     * và một lô hàng trăm chương do Admin dựng không ăn vào ngân sách của người đọc.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", foreignKey = @ForeignKey(name = "fk_audio_requested_by"))
    private User requestedBy;

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
