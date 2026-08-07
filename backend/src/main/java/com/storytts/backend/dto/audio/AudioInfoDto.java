package com.storytts.backend.dto.audio;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;

/**
 * State of one audio track for a chapter.
 *
 * @param streamUrl relative path the player should hit; null until the track is ready
 * @param status    PROCESSING, READY or FAILED — drives the client-side polling loop
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AudioInfoDto(
        Long id,
        Long chapterId,
        String source,
        String status,
        String streamUrl,
        String voice,
        Integer speed,
        Integer durationSeconds,
        Long fileSize,
        String errorMessage
) {

    public static AudioInfoDto from(AudioFile audio, Long chapterId) {
        boolean ready = audio.getStatus() == AudioStatus.READY;
        return new AudioInfoDto(
                audio.getId(),
                chapterId,
                audio.getSource().name(),
                audio.getStatus().name(),
                ready ? "/api/chapters/%d/audio/%d".formatted(chapterId, audio.getId()) : null,
                audio.getVoice(),
                audio.getSpeed(),
                audio.getDurationSeconds(),
                audio.getFileSize(),
                audio.getErrorMessage());
    }
}
