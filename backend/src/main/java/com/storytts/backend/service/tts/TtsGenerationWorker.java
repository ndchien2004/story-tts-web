package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.AudioTranscript;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AudioTranscriptRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

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

    private final TtsEngine ttsEngine;
    private final StorageService storageService;
    private final AudioFileRepository audioFileRepository;
    private final AudioTranscriptRepository audioTranscriptRepository;
    private final TranscriptCodec transcriptCodec;

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
            SynthesisResult result = ttsEngine.synthesize(event.text(), event.voice(), event.speed());
            String fileName = storageService.storeAudio(result.audio(), ".mp3");

            audio.setFilePath(fileName);
            audio.setFileSize((long) result.audio().length);
            audio.setContentType("audio/mpeg");
            audio.setProvider(result.providerId());
            // Trước khi cờ READY được bật, và trong cùng một giao dịch với nó:
            // trang đọc thấy READY là đi hỏi mốc thời gian ngay, nên thứ tự này
            // là thứ giữ cho nó không hỏi đúng vào lúc chưa có gì.
            audio.setTranscriptWords(storeTranscript(audio.getId(), result.words()));
            audio.setStatus(AudioStatus.READY);
            audio.setErrorMessage(null);
            audioFileRepository.save(audio);

            log.info("Synthesis finished for audio {} via {} ({} bytes, {} chữ có mốc thời gian)",
                    event.audioId(), result.providerId(), result.audio().length, result.words().size());
        } catch (TtsException ex) {
            log.warn("Synthesis failed for audio {}: {}", event.audioId(), ex.getMessage());
            markFailed(audio, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected synthesis failure for audio {}", event.audioId(), ex);
            markFailed(audio, "Không tạo được audio. Vui lòng thử lại sau.");
        }
    }

    /**
     * Cất mốc thời gian sang bảng riêng của nó.
     *
     * @return số chữ đã cất, hay null khi không có gì để cất — chính là giá trị
     *         cột {@code transcript_words} muốn nhận
     */
    private Integer storeTranscript(Long audioId, List<WordTimestamp> words) {
        String json = transcriptCodec.encode(words);
        if (json == null) {
            return null;
        }

        // save chứ không insert thẳng: dựng lại một bản audio cũ dùng lại đúng
        // id ấy trong vài đường, và hai dòng cùng khóa chính là một lỗi ràng buộc
        // thay vì một bản đọc được cập nhật.
        audioTranscriptRepository.save(AudioTranscript.builder()
                .audioId(audioId)
                .wordsJson(json)
                .build());

        return words.size();
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
