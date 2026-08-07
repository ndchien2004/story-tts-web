package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs synthesis off the request thread.
 *
 * Listening at {@link TransactionPhase#AFTER_COMMIT} matters: the caller creates
 * the PROCESSING row inside its own transaction, and a worker running on another
 * thread would not see that row until the commit lands. The row is flipped to
 * READY or FAILED here, and the client polls for that change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TtsGenerationWorker {

    private final FptAiTtsClient ttsClient;
    private final StorageService storageService;
    private final AudioFileRepository audioFileRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGenerationRequested(TtsGenerationRequested event) {
        AudioFile audio = audioFileRepository.findById(event.audioId()).orElse(null);
        if (audio == null) {
            log.warn("Audio row {} no longer exists; skipping synthesis", event.audioId());
            return;
        }

        try {
            byte[] mp3 = ttsClient.synthesize(event.text(), event.voice(), event.speed());
            String fileName = storageService.storeAudio(mp3, ".mp3");

            audio.setFilePath(fileName);
            audio.setFileSize((long) mp3.length);
            audio.setContentType("audio/mpeg");
            audio.setStatus(AudioStatus.READY);
            audio.setErrorMessage(null);
            audioFileRepository.save(audio);

            log.info("Synthesis finished for audio {} ({} bytes)", event.audioId(), mp3.length);
        } catch (TtsException ex) {
            log.warn("Synthesis failed for audio {}: {}", event.audioId(), ex.getMessage());
            markFailed(audio, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected synthesis failure for audio {}", event.audioId(), ex);
            markFailed(audio, "Không tạo được audio. Vui lòng thử lại sau.");
        }
    }

    private void markFailed(AudioFile audio, String message) {
        audio.setStatus(AudioStatus.FAILED);
        audio.setErrorMessage(truncate(message));
        audioFileRepository.save(audio);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
