package com.storytts.backend.service.tts;

import com.storytts.backend.exception.TtsException;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs synthesis off the request thread.
 *
 * Listening at {@link TransactionPhase#AFTER_COMMIT} matters: the caller creates
 * the PROCESSING row inside its own transaction, and a worker running on another
 * thread would not see that row until the commit lands. The row is flipped to
 * READY or FAILED here, and the client polls for that change.
 *
 * <h3>Vì sao method này không có {@code @Transactional}</h3>
 * Một lượt dựng là ba đoạn nối nhau: đọc bản ghi, gọi ElevenLabs, ghi kết quả.
 * Đoạn giữa mất hàng phút — một chương 20.000 ký tự bị cắt thành năm lần gọi, mỗi
 * lần chờ tối đa hai phút — và trong suốt quãng ấy không có câu lệnh SQL nào.
 *
 * <p>Trước đây cả ba nằm trong một {@code @Transactional(REQUIRES_NEW)}, nên
 * kết nối lấy ra ở đoạn một bị giữ tới hết đoạn ba. Bốn luồng nền dựng cùng lúc
 * là bốn trong mười kết nối của cả ứng dụng nằm im chờ mạng, và những request
 * bình thường còn lại xếp hàng cho tới khi Hikari hết kiên nhẫn:
 * {@code Connection is not available, request timed out}.
 *
 * <p>Giờ mỗi đầu là một giao dịch ngắn riêng của {@link TtsGenerationRecords},
 * còn quãng chờ mạng ở giữa không cầm kết nối nào. Đổi lại là một khoảng hở có
 * thật: máy chủ tắt giữa chừng thì file đã ghi mà hàng vẫn ở PROCESSING. Khoảng
 * hở ấy vốn đã tồn tại — file luôn được ghi trước lúc commit — và
 * {@link StaleGenerationReconciler} dọn nó ở lần khởi động sau.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TtsGenerationWorker {

    private final TtsEngine ttsEngine;
    private final StorageService storageService;
    private final TtsGenerationRecords records;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(TtsGenerationRequested event) {
        Long audioId = event.audioId();

        // Giao dịch ngắn thứ nhất, đóng lại ngay khi trả lời xong.
        if (!records.stillQueued(audioId)) {
            log.warn("Audio row {} no longer exists; skipping synthesis", audioId);
            return;
        }

        String fileName = null;
        try {
            // ---- Ngoài mọi giao dịch: chờ mạng và ghi đĩa ----
            SynthesisResult result = ttsEngine.synthesize(event.text(), event.voice(), event.speed());
            fileName = storageService.storeAudio(result.audio(), ".mp3");

            // ---- Giao dịch ngắn thứ hai ----
            if (!records.markReady(audioId, result, fileName)) {
                // Hàng biến mất trong lúc dựng: file vừa ghi không còn ai trỏ tới.
                log.warn("Audio row {} disappeared during synthesis; discarding the file", audioId);
                storageService.deleteAudio(fileName);
                return;
            }

            log.info("Synthesis finished for audio {} via {} ({} bytes, {} chữ có mốc thời gian)",
                    audioId, result.providerId(), result.audio().length, result.words().size());
        } catch (TtsException ex) {
            log.warn("Synthesis failed for audio {}: {}", audioId, ex.getMessage());
            discard(fileName);
            records.markFailed(audioId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected synthesis failure for audio {}", audioId, ex);
            discard(fileName);
            records.markFailed(audioId, "Không tạo được audio. Vui lòng thử lại sau.");
        }
    }

    /**
     * Bỏ file đã ghi khi lượt dựng này rốt cuộc không thành.
     *
     * <p>Chỉ có việc với lỗi xảy ra <i>sau</i> lúc ghi đĩa — tức lúc ghi cơ sở dữ
     * liệu hỏng. Không có bước này thì mỗi lần ấy để lại một file không hàng nào
     * trỏ tới, và không còn đường nào tìm ra nó nữa.
     */
    private void discard(String fileName) {
        if (fileName != null) {
            storageService.deleteAudio(fileName);
        }
    }
}
