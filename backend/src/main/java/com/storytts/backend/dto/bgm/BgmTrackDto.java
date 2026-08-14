package com.storytts.backend.dto.bgm;

import com.storytts.backend.domain.BgmTrack;

/**
 * Một bản nhạc nền như trình duyệt nhìn thấy.
 *
 * <p>{@code streamUrl} là đường tương đối tính từ gốc API, không phải tên file:
 * người nghe không cần biết file tên gì, và tên file là thứ có thể đổi.
 */
public record BgmTrackDto(
        Long id,
        String title,
        String credit,
        String streamUrl,
        Integer durationSeconds,
        Long fileSize,
        boolean active
) {

    public static BgmTrackDto from(BgmTrack track) {
        return new BgmTrackDto(
                track.getId(),
                track.getTitle(),
                track.getCredit(),
                "/api/bgm/" + track.getId() + "/stream",
                track.getDurationSeconds(),
                track.getFileSize(),
                Boolean.TRUE.equals(track.getActive()));
    }
}
