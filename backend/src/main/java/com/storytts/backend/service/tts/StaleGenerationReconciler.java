package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fails any synthesis left mid-flight by a previous run.
 *
 * Without this, a row stuck at PROCESSING would be treated as "already in
 * progress" by the cache lookup forever, and the chapter could never be
 * synthesised again.
 */
@Component
@RequiredArgsConstructor
@Order(20)
@Slf4j
public class StaleGenerationReconciler implements ApplicationRunner {

    private final AudioFileRepository audioFileRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AudioFile> stale = audioFileRepository.findAll().stream()
                .filter(audio -> audio.getStatus() == AudioStatus.PROCESSING)
                .toList();

        if (stale.isEmpty()) {
            return;
        }

        stale.forEach(audio -> {
            audio.setStatus(AudioStatus.FAILED);
            audio.setErrorMessage(
                    "Quá trình tạo audio bị gián đoạn do máy chủ khởi động lại. Vui lòng thử lại.");
        });
        audioFileRepository.saveAll(stale);

        log.info("Marked {} interrupted synthesis job(s) as failed", stale.size());
    }
}
