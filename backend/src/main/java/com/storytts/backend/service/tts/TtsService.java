package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.ResourceNotFoundException;
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

import java.util.Arrays;
import java.util.List;

/**
 * Turns chapter text into audio, reusing previous results where possible.
 *
 * Generated tracks are cached per (chapter, voice, speed). A repeat request for
 * the same combination returns the stored file instead of calling the provider
 * again, which keeps both latency and API usage down.
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
    private final ApplicationEventPublisher eventPublisher;

    public List<VoiceOptionDto> availableVoices() {
        return Arrays.stream(TtsVoice.values()).map(VoiceOptionDto::from).toList();
    }

    /**
     * Starts or resumes synthesis for a chapter.
     *
     * Returns immediately: a fresh request comes back as PROCESSING and the
     * client polls {@link #getStatus} until the track is READY.
     */
    @Transactional
    public AudioInfoDto requestForChapter(Long chapterId, TtsRequest request) {
        if (!properties.enabled()) {
            throw new TtsException("Chức năng đọc bằng AI đang tạm tắt trên máy chủ.");
        }

        Chapter chapter = chapterService.findDetailEntity(chapterId);

        // The same gate the reader and the audio stream go through, so synthesis
        // can never be used to reach a chapter the caller cannot open.
        accessControlService.requireAccess(chapter);

        String voice = resolveVoice(request == null ? null : request.voice());
        int speed = resolveSpeed(request == null ? null : request.speed());

        AudioFile cached = audioFileRepository.findTtsCache(chapterId, voice, speed).orElse(null);
        if (cached != null) {
            switch (cached.getStatus()) {
                case READY, PROCESSING -> {
                    return AudioInfoDto.from(cached, chapterId);
                }
                // A previous attempt failed; clear it and try once more.
                case FAILED -> {
                    storageService.deleteAudio(cached.getFilePath());
                    audioFileRepository.delete(cached);
                    audioFileRepository.flush();
                }
            }
        }

        AudioFile pending = audioFileRepository.save(AudioFile.builder()
                .chapter(chapter)
                .source(AudioSource.TTS)
                .status(AudioStatus.PROCESSING)
                .voice(voice)
                .speed(speed)
                .contentType("audio/mpeg")
                .build());

        log.info("Queued synthesis for chapter {} (voice={}, speed={})", chapterId, voice, speed);
        eventPublisher.publishEvent(
                new TtsGenerationRequested(pending.getId(), chapter.getContent(), voice, speed));

        return AudioInfoDto.from(pending, chapterId);
    }

    /** Current state of a generated track, used by the client polling loop. */
    @Transactional(readOnly = true)
    public AudioInfoDto getStatus(Long chapterId, Long audioId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));

        if (!audio.getChapter().getId().equals(chapterId)) {
            throw new ResourceNotFoundException("File audio không thuộc chương này.");
        }
        return AudioInfoDto.from(audio, chapterId);
    }

    private String resolveVoice(String requested) {
        return TtsVoice.fromCode(requested)
                .or(() -> TtsVoice.fromCode(properties.fptai().defaultVoice()))
                .orElse(TtsVoice.BANMAI)
                .getCode();
    }

    private int resolveSpeed(Integer requested) {
        int speed = requested != null ? requested : properties.fptai().defaultSpeed();
        return Math.max(-3, Math.min(3, speed));
    }
}
