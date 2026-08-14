package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng audio_transcripts — mốc thời gian từng chữ của một bản audio.
 *
 * <p>Một bản audio có nhiều nhất một dòng ở đây, khóa chính chính là id của bản
 * audio ấy. Không khai báo quan hệ ngược về {@link AudioFile}: mục đích của cả
 * bảng này là để cột JSON kia <em>không</em> bị nạp theo mỗi lần đụng tới hàng
 * audio, mà một liên kết một-một trong JPA thì gần như luôn nạp sớm dù có ghi
 * LAZY hay không.
 *
 * <p>Dòng ở đây bị xóa theo bản audio, do ràng buộc khóa ngoại phía cơ sở dữ
 * liệu chứ không do mã Java — xem V3.
 */
@Entity
@Table(name = "audio_transcripts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioTranscript {

    /** Chính là {@code audio_files.id}; không sinh tự động. */
    @Id
    @Column(name = "audio_id")
    private Long audioId;

    /** Mảng JSON các chữ: {@code [{"word","start","end","charStart","charEnd"}, …]}. */
    @Lob
    @Column(name = "words_json", nullable = false, columnDefinition = "LONGTEXT")
    private String wordsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
