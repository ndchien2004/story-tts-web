package com.storytts.backend.repository;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    List<AudioFile> findByChapterId(Long chapterId);

    Optional<AudioFile> findFirstByChapterIdAndSourceAndStatus(Long chapterId, AudioSource source, AudioStatus status);

    /**
     * Tra cứu cache TTS: cùng chương + cùng giọng + cùng tốc độ thì tái sử dụng file cũ,
     * không gọi lại API.
     */
    @Query("""
            SELECT a FROM AudioFile a
            WHERE a.chapter.id = :chapterId
              AND a.source = com.storytts.backend.domain.AudioSource.TTS
              AND a.voice = :voice
              AND a.speed = :speed
            ORDER BY a.createdAt DESC
            LIMIT 1
            """)
    Optional<AudioFile> findTtsCache(@Param("chapterId") Long chapterId,
                                     @Param("voice") String voice,
                                     @Param("speed") Integer speed);

    boolean existsByChapterIdAndStatus(Long chapterId, AudioStatus status);

    /**
     * Các chương (trong danh sách truyền vào) đã có audio dùng được — một truy vấn duy nhất,
     * để danh sách chương không sinh N+1 query.
     */
    @Query("""
            SELECT DISTINCT a.chapter.id FROM AudioFile a
            WHERE a.chapter.id IN :chapterIds
              AND a.status = com.storytts.backend.domain.AudioStatus.READY
            """)
    List<Long> findChapterIdsWithReadyAudio(@Param("chapterIds") java.util.Collection<Long> chapterIds);

    long countBySource(AudioSource source);

    void deleteByChapterId(Long chapterId);
}
