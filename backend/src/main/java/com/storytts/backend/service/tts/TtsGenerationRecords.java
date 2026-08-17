package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.AudioTranscript;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AudioTranscriptRepository;
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
     * Ghi kết quả và bật cờ READY.
     *
     * <p>Mốc thời gian được cất <b>trước</b> cờ READY và trong cùng giao dịch với
     * nó: trang đọc thấy READY là đi hỏi mốc thời gian ngay, nên thứ tự này là thứ
     * giữ cho nó không hỏi đúng vào lúc chưa có gì.
     *
     * @return false nếu bản ghi đã biến mất trong lúc dựng — bên gọi cần dọn file
     *         vừa ghi, vì không còn hàng nào trỏ tới nó nữa
     */
    @Transactional
    public boolean markReady(Long audioId, SynthesisResult result, String fileName) {
        AudioFile audio = audioFileRepository.findById(audioId).orElse(null);
        if (audio == null) {
            return false;
        }

        audio.setFilePath(fileName);
        audio.setFileSize((long) result.audio().length);
        audio.setContentType("audio/mpeg");
        audio.setProvider(result.providerId());
        audio.setTranscriptWords(storeTranscript(audioId, result.words()));
        audio.setStatus(AudioStatus.READY);
        audio.setErrorMessage(null);
        audioFileRepository.save(audio);
        return true;
    }

    /** Đánh dấu hỏng kèm lý do; bản ghi đã biến mất thì không còn gì để đánh dấu. */
    @Transactional
    public void markFailed(Long audioId, String message) {
        audioFileRepository.findById(audioId).ifPresent(audio -> {
            audio.setStatus(AudioStatus.FAILED);
            audio.setErrorMessage(truncate(message));
            audioFileRepository.save(audio);
        });
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
