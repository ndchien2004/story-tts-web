package com.storytts.backend.dto.audio;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;

/**
 * State of one audio track for a chapter.
 *
 * @param streamUrl relative path the player should hit; null until the track is ready
 * @param status    PROCESSING, READY or FAILED — drives the client-side polling loop
 * @param provider  which backend produced the audio, which may differ from the
 *                  one requested if a fallback took over
 * @param owner     LIBRARY for what the console put there, SESSION for what the
 *                  reader made for themselves. Two tracks can be alike in every
 *                  other field and still differ here: one outlives the visit and
 *                  is meant for everyone, the other belongs to one person and
 *                  goes when their session does. The reading page needs the
 *                  distinction to know which one to reach for first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AudioInfoDto(
        Long id,
        Long chapterId,
        String source,
        String owner,
        String status,
        String streamUrl,
        String voice,
        Integer speed,
        String provider,
        Integer durationSeconds,
        Long fileSize,
        String errorMessage
) {

    /** Bản của khu quản trị: dựng sẵn cho mọi người, sống lâu dài. */
    public static final String OWNER_LIBRARY = "LIBRARY";

    /** Bản người đọc tự dựng: của riêng họ, mất khi phiên đăng nhập kết thúc. */
    public static final String OWNER_SESSION = "SESSION";

    public static AudioInfoDto from(AudioFile audio, Long chapterId) {
        boolean ready = audio.getStatus() == AudioStatus.READY;
        return new AudioInfoDto(
                audio.getId(),
                chapterId,
                audio.getSource().name(),
                audio.getRequestedBy() == null ? OWNER_LIBRARY : OWNER_SESSION,
                audio.getStatus().name(),
                ready ? "/api/chapters/%d/audio/%d".formatted(chapterId, audio.getId()) : null,
                audio.getVoice(),
                audio.getSpeed(),
                audio.getProvider(),
                audio.getDurationSeconds(),
                audio.getFileSize(),
                audio.getErrorMessage());
    }
}
