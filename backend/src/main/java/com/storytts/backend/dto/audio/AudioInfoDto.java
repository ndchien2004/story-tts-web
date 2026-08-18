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
 * @param hasTranscript có mốc thời gian từng chữ hay không. Trang đọc hỏi điều
 *                  này trước khi đi lấy: bản admin tải lên và những bản dựng từ
 *                  trước đều không có, và một lời mời "đọc theo giọng đọc" mà
 *                  bấm vào thì không có gì sáng lên còn tệ hơn là không mời.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AudioInfoDto(
        Long id,
        Long chapterId,

        /**
         * Phiên bản nội dung chương mà bản này đọc theo.
         *
         * <p>Trang đọc so nó với phiên bản của chương nó đang hiển thị. Bằng nhau
         * là khớp; khác nhau nghĩa là bản này thuộc về một nội dung khác, và trang
         * đọc biết điều đó mà không phải hỏi thêm máy chủ câu nào.
         *
         * <p>Máy chủ đã lọc theo phiên bản trước khi trả về, nên trong đường bình
         * thường hai con số luôn bằng nhau. Trường này vẫn có mặt vì nó biến một
         * bất biến ngầm thành thứ kiểm chứng được ở đầu bên kia — và vì một
         * response về muộn của lần tải trước thì tự nó khai ra là đã cũ.
         *
         * <p>Null với những bản dựng từ trước khi có cột này.
         */
        Integer contentVersion,

        String source,
        String owner,
        String status,
        String streamUrl,
        String voice,
        Integer speed,
        String provider,
        Integer durationSeconds,
        Long fileSize,
        String errorMessage,
        boolean hasTranscript
) {

    /** Bản của khu quản trị: dựng sẵn cho mọi người, sống lâu dài. */
    public static final String OWNER_LIBRARY = "LIBRARY";

    /** Bản người đọc tự dựng: của riêng họ, mất khi phiên đăng nhập kết thúc. */
    public static final String OWNER_SESSION = "SESSION";

    /** Đường dẫn phát của một bản audio — một chỗ định nghĩa, nhiều chỗ dùng. */
    public static String streamPath(Long chapterId, Long audioId) {
        return "/api/chapters/%d/audio/%d".formatted(chapterId, audioId);
    }

    public static AudioInfoDto from(AudioFile audio, Long chapterId) {
        boolean ready = audio.getStatus() == AudioStatus.READY;
        return new AudioInfoDto(
                audio.getId(),
                chapterId,
                audio.getContentVersion(),
                audio.getSource().name(),
                audio.getRequestedBy() == null ? OWNER_LIBRARY : OWNER_SESSION,
                audio.getStatus().name(),
                ready ? streamPath(chapterId, audio.getId()) : null,
                audio.getVoice(),
                audio.getSpeed(),
                audio.getProvider(),
                audio.getDurationSeconds(),
                audio.getFileSize(),
                audio.getErrorMessage(),
                audio.getTranscriptWords() != null && audio.getTranscriptWords() > 0);
    }
}
