package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.AccessControlService;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns chapter text into audio, reusing previous results where possible.
 *
 * Generated tracks are cached per (chapter, voice, speed). A repeat request for
 * the same combination returns the stored file instead of calling a provider
 * again, which keeps both latency and API usage down.
 *
 * Reached only from the admin console. Readers play what is already there; they
 * have no way in here, which is what stops one file per curious visitor from
 * piling up on disk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsService {

    private final ChapterService chapterService;
    private final AccessControlService accessControlService;
    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;
    private final TtsProperties properties;
    private final TtsEngine ttsEngine;
    private final ApplicationEventPublisher eventPublisher;

    public List<VoiceOptionDto> availableVoices() {
        return ttsEngine.availableVoices();
    }

    /**
     * Starts or resumes synthesis for a chapter.
     *
     * Returns immediately: a fresh request comes back as PROCESSING and the
     * caller polls the chapter's track list until it is READY.
     */
    @Transactional
    public AudioInfoDto requestForChapter(Long chapterId, TtsRequest request) {
        if (!properties.enabled()) {
            throw new TtsException("Chức năng tạo audio đang tạm tắt trên máy chủ.");
        }
        if (!ttsEngine.hasAnyProvider()) {
            throw new TtsException(
                    "Chưa cấu hình nhà cung cấp giọng đọc nào. "
                            + "Vui lòng đặt ELEVENLABS_API_KEY trong file .env.");
        }

        Chapter chapter = chapterService.findDetailEntity(chapterId);

        // The same gate the reader and the audio stream go through, so synthesis
        // can never be used to reach a chapter the caller cannot open.
        accessControlService.requireAccess(chapter);

        String voice = resolveVoice(request == null ? null : request.voice());
        int speed = resolveSpeed(request == null ? null : request.speed());

        String contentHash = hashContent(chapter.getContent());

        AudioFile cached = audioFileRepository.findTtsCache(chapterId, voice, speed).orElse(null);
        if (cached != null) {
            // Đang dựng dở: để nó chạy tiếp. Xóa hàng mà luồng nền đang ghi vào
            // chỉ tạo ra một lần gọi API nữa và một bản ghi mồ côi.
            if (cached.getStatus() == AudioStatus.PROCESSING) {
                return AudioInfoDto.from(cached, chapterId);
            }
            // Còn dùng được, và đọc đúng chữ đang có trong chương.
            if (cached.getStatus() == AudioStatus.READY
                    && contentHash.equals(cached.getContentHash())) {
                return AudioInfoDto.from(cached, chapterId);
            }

            // Hoặc lần trước hỏng, hoặc nội dung chương đã đổi kể từ lúc dựng bản
            // này — cả hai đều có nghĩa là bản cũ không dùng lại được.
            log.info("Bỏ bản audio cũ của chương {} ({})", chapterId,
                    cached.getStatus() == AudioStatus.FAILED ? "lần trước hỏng" : "nội dung đã đổi");
            storageService.deleteAudio(cached.getFilePath());
            audioFileRepository.delete(cached);
            audioFileRepository.flush();
        }

        AudioFile pending = audioFileRepository.save(AudioFile.builder()
                .chapter(chapter)
                .source(AudioSource.TTS)
                .status(AudioStatus.PROCESSING)
                .voice(voice)
                .speed(speed)
                .contentHash(contentHash)
                .contentType("audio/mpeg")
                .build());

        log.info("Queued synthesis for chapter {} (voice={}, speed={})", chapterId, voice, speed);
        eventPublisher.publishEvent(
                new TtsGenerationRequested(pending.getId(), chapter.getContent(), voice, speed));

        return AudioInfoDto.from(pending, chapterId);
    }

    /**
     * Falls back to the first voice on offer when the request names none, so a
     * caller that omits the field still gets a deterministic cache key.
     */
    private String resolveVoice(String requested) {
        List<VoiceOptionDto> voices = ttsEngine.availableVoices();

        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            boolean known = voices.stream().anyMatch(voice -> voice.code().equalsIgnoreCase(trimmed));
            if (known) {
                return trimmed;
            }
        }

        return voices.isEmpty() ? "default" : voices.getFirst().code();
    }

    /**
     * Tốc độ giờ chỉ còn là một phần khóa cache.
     *
     * <p>ElevenLabs không nhận tham số tốc độ, nên giá trị này không đổi được
     * giọng đọc nhanh hay chậm; nó ở lại vì khóa cache
     * {@code (chương, giọng, tốc độ)} vẫn dùng tới, và vì người nghe đã có nút
     * chỉnh tốc độ phát ngay trên trình phát — chỗ đó mới là nơi việc này thuộc về.
     */
    /**
     * Dấu vân tay của nội dung chương, dùng để biết bản audio cũ còn khớp không.
     *
     * <p>Băm chứ không so chuỗi: chương dài hàng chục nghìn ký tự, mà thứ cần
     * lưu lại chỉ là "có đổi hay không".
     */
    private String hashContent(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không có thuật toán SHA-256", ex);
        }
    }

    private int resolveSpeed(Integer requested) {
        int speed = requested != null ? requested : properties.defaultSpeed();
        return Math.max(-3, Math.min(3, speed));
    }
}
