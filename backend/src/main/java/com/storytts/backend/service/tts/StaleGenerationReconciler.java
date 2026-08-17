package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Dọn phần việc dở dang mà lần chạy trước để lại.
 *
 * <p>Không có bước này, một hàng kẹt ở PROCESSING sẽ bị bộ nhớ đệm coi là "đang
 * dựng" mãi mãi, và chương ấy không bao giờ dựng lại được nữa.
 *
 * <h3>Hai thứ được dọn</h3>
 * <b>Hàng kẹt ở PROCESSING</b> được đánh dấu FAILED. <b>File của chúng</b> cũng
 * được xóa theo, và đó là phần trước đây thiếu: một lượt dựng bị cắt ngang sau
 * khi đã ghi file nhưng trước khi kịp ghi cơ sở dữ liệu để lại một file không
 * hàng nào trỏ tới. Không xóa ở đây thì không còn đường nào tìm ra nó nữa — tên
 * file là một UUID chỉ tồn tại trong biến cục bộ của luồng đã chết.
 *
 * <p>Thứ tự có chủ ý: xóa file trước, ghi cơ sở dữ liệu sau. Xóa hụt thì để lại
 * một file thừa, còn ghi cơ sở dữ liệu trước rồi xóa hụt thì mất luôn manh mối
 * để tìm file ấy.
 */
@Component
@RequiredArgsConstructor
@Order(20)
@Slf4j
public class StaleGenerationReconciler implements ApplicationRunner {

    /**
     * File ghi dở cũ hơn quãng này thì chắc chắn không phải của lượt ghi nào đang chạy.
     *
     * <p>Rộng rãi có chủ ý: ứng dụng vừa mới khởi động nên chưa thể có lượt ghi
     * nào của chính nó, còn xóa nhầm thì không lấy lại được.
     */
    private static final Duration TEMP_FILE_TTL = Duration.ofHours(6);

    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        storageService.sweepTemporary(TEMP_FILE_TTL);

        List<AudioFile> stale = audioFileRepository.findByStatus(AudioStatus.PROCESSING);
        if (stale.isEmpty()) {
            return;
        }

        stale.forEach(audio -> {
            storageService.deleteAudio(audio.getFilePath());
            audio.setFilePath(null);
            audio.setStatus(AudioStatus.FAILED);
            audio.setErrorMessage(
                    "Quá trình tạo audio bị gián đoạn do máy chủ khởi động lại. Vui lòng thử lại.");
        });
        audioFileRepository.saveAll(stale);

        log.info("Đã đánh dấu {} lượt dựng audio bị gián đoạn là hỏng", stale.size());
    }
}
