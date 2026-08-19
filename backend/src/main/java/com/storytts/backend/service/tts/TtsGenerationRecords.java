package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.AudioTranscript;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AudioTranscriptRepository;
import com.storytts.backend.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mọi lần chạm cơ sở dữ liệu của một lượt dựng audio, gói thành từng giao dịch ngắn.
 *
 * <h3>Vì sao là một bean riêng chứ không phải mấy method của {@link TtsGenerationWorker}</h3>
 * Việc dựng audio gồm ba đoạn: đọc bản ghi, gọi nhà cung cấp (hàng phút), rồi ghi
 * kết quả. Chỉ đoạn đầu và đoạn cuối cần kết nối cơ sở dữ liệu. Trước đây cả ba
 * nằm trong một {@code @Transactional}, nên kết nối bị giữ suốt lúc chờ mạng —
 * bốn luồng nền là bốn trong mười kết nối của cả ứng dụng đứng im hàng phút.
 *
 * <p>Tách ra được là nhờ {@code @Transactional} của Spring chạy bằng proxy: một
 * method gọi method khác <i>trong cùng một bean</i> thì không đi qua proxy và
 * annotation lặng lẽ vô hiệu. Bean riêng là cách duy nhất để worker mở đúng hai
 * giao dịch ngắn ở hai đầu mà không phải tự viết mã quản lý giao dịch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TtsGenerationRecords {

    private final AudioFileRepository audioFileRepository;
    private final AudioTranscriptRepository audioTranscriptRepository;
    private final TranscriptCodec transcriptCodec;
    private final AiUsageService aiUsageService;

    /**
     * Hàng chờ dựng còn tồn tại không.
     *
     * <p>Hỏi trước khi gọi nhà cung cấp: bản ghi đã bị xóa (người đọc đăng xuất,
     * admin dọn chương) thì lần gọi API ấy vừa tốn tiền vừa không có chỗ để ghi.
     */
    @Transactional(readOnly = true)
    public boolean stillQueued(Long audioId) {
        return audioFileRepository.existsById(audioId);
    }

    /**
     * Kết cục của một lượt dựng, quyết định ở giao dịch ngắn cuối cùng.
     */
    public enum Outcome {

        /** Phiên bản còn khớp: bản audio này là bản hiện tại của chương. */
        READY,

        /**
         * Chương đã đi tiếp trong lúc dựng. File nghe được, nhưng nó đọc nội dung
         * cũ nên không bao giờ được phục vụ như audio hiện tại.
         */
        STALE,

        /** Hàng đã bị xóa trong lúc dựng; không còn chỗ nào để ghi kết quả. */
        GONE
    }

    /**
     * Ghi kết quả, sau khi hỏi lại cơ sở dữ liệu một câu duy nhất:
     * <b>chương này còn ở phiên bản mà lượt dựng đã chụp không?</b>
     *
     * <h3>Vì sao phải hỏi lần thứ hai</h3>
     * Lần hỏi thứ nhất nằm ở {@code TtsService}, lúc người dùng bấm nút. Giữa hai
     * lần ấy là lời gọi tới nhà cung cấp — hàng phút với một chương dài — và
     * không có gì ngăn Admin lưu chương ba lần trong quãng đó. Chỉ kiểm ở đầu
     * request là kiểm một sự thật đã hết hạn từ lâu trước khi nó được dùng tới.
     *
     * <p>Lần hỏi này rơi vào đúng chỗ duy nhất còn kịp: cùng giao dịch với lệnh
     * ghi, sau khi mọi thứ chậm chạp đã xong. Phiên bản đọc ra ở đây là phiên bản
     * tại thời điểm ghi, nên hai câu "audio này hợp lệ" và "audio này được ghi là
     * hợp lệ" không thể tách nhau ra được.
     *
     * <p><b>Không có đường nào để một lượt dựng cũ thắng.</b> Chương đi qua v7,
     * v8, v9 trong lúc ba lượt dựng cùng chạy thì cả ba đều so với v9 lúc về
     * đích, và cả ba đều thua nếu chúng không phải v9 — bất kể lượt nào về sau
     * cùng. Thứ quyết định là phiên bản hiện tại của chương, không phải thứ tự
     * hoàn thành.
     *
     * <p>Mốc thời gian chỉ được cất khi kết cục là READY, và được cất <b>trước</b>
     * cờ READY trong cùng giao dịch: trang đọc thấy READY là đi hỏi mốc thời gian
     * ngay, nên thứ tự này là thứ giữ cho nó không hỏi đúng vào lúc chưa có gì.
     *
     * @param generationVersion phiên bản nội dung đã chụp lúc bắt đầu, đi theo
     *                          {@link TtsGenerationRequested} cùng chính đoạn chữ
     *                          vừa được đọc thành tiếng
     * @return xem {@link Outcome}; bên gọi dọn file với {@code GONE} và {@code STALE}
     */
    @Transactional
    public Outcome markReady(Long audioId, int generationVersion,
                             SynthesisResult result, String fileName) {
        AudioFile audio = audioFileRepository.findById(audioId).orElse(null);
        if (audio == null) {
            return Outcome.GONE;
        }

        // Đọc trong giao dịch này, nên đây là phiên bản lúc ghi chứ không phải
        // một con số nhớ từ lúc bắt đầu.
        int currentVersion = audio.getChapter().getContentVersion();

        if (currentVersion != generationVersion) {
            log.info("Chương {} đã sang phiên bản {} trong lúc dựng bản {} (phiên bản {}); "
                            + "bản này là lỗi thời",
                    audio.getChapter().getId(), currentVersion, audioId, generationVersion);

            // Ghi lại đúng những gì đã xảy ra: một bản dựng thành công, cho một
            // phiên bản không còn là hiện tại. Cố ý không đặt filePath — bên gọi
            // xóa file ngay, vì bản này chưa từng đến tai ai nên không có ai để
            // giữ nó lại cho (khác với bản STALE do Admin sửa chương, thứ có thể
            // đang phát dở trong tai nghe của một người nào đó).
            audio.setStatus(AudioStatus.STALE);
            audio.setProvider(result.providerId());
            audio.setErrorMessage(null);
            audioFileRepository.save(audio);

            // Người bấm nút không nhận được gì cả: file bị xóa ngay sau đây vì
            // nó đọc chữ đã cũ. Họ trả một lượt cho một lần sửa chương của Admin
            // rơi đúng vào lúc họ đang chờ, nên lượt ấy được trả lại.
            aiUsageService.refundForAudio(audioId, "chuong doi noi dung trong luc dung");
            return Outcome.STALE;
        }

        audio.setFilePath(fileName);
        audio.setFileSize((long) result.audio().length);
        audio.setContentType("audio/mpeg");
        audio.setProvider(result.providerId());
        audio.setTranscriptWords(storeTranscript(audioId, result.words()));
        audio.setStatus(AudioStatus.READY);
        audio.setErrorMessage(null);
        audioFileRepository.save(audio);
        return Outcome.READY;
    }

    /**
     * Đánh dấu hỏng kèm lý do; bản ghi đã biến mất thì không còn gì để đánh dấu.
     *
     * <p>Lượt đã trừ được hoàn ở đây. Người đọc không mất lượt vì lỗi của nhà
     * cung cấp — lời hứa ấy trước đây là hệ quả của việc hạn mức đếm trên chính
     * bảng audio và bỏ qua hàng FAILED; giờ sổ đếm nằm ở bảng khác nên nó phải
     * được nói thành một câu lệnh. Đường thứ hai dẫn tới FAILED là
     * {@link StaleGenerationReconciler} lúc khởi động, và nó hoàn lượt bằng
     * cùng lời gọi này.
     */
    @Transactional
    public void markFailed(Long audioId, String message) {
        audioFileRepository.findById(audioId).ifPresent(audio -> {
            audio.setStatus(AudioStatus.FAILED);
            audio.setErrorMessage(truncate(message));
            audioFileRepository.save(audio);
        });
        aiUsageService.refundForAudio(audioId, "dung audio that bai");
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

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
