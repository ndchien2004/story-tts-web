package com.storytts.backend.service.tts;

import com.storytts.backend.config.StorageProperties;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dọn file của những bản audio đã lỗi thời đủ lâu.
 *
 * <h3>Vì sao không xóa ngay lúc chúng thành lỗi thời</h3>
 * Lúc Admin bấm lưu một chương, rất có thể đang có người nghe dở đúng bản audio
 * vừa bị thay thế. Trang đọc cố ý không cắt ngang họ — đường phát vẫn phục vụ
 * bản lỗi thời cho tới khi họ tự chọn chuyển sang nội dung mới. Xóa file ngay
 * tại chỗ sẽ biến một thay đổi mà họ chưa kịp biết thành một lỗi phát giữa
 * chừng, tức là đúng thứ mà việc giữ bản cũ sinh ra để tránh.
 *
 * <p>Hạn lưu giữ là khoảng đệm cho chuyện đó, và tiện thể cũng là khoảng để một
 * lần sửa nhầm còn kịp hoàn tác trước khi file biến mất.
 *
 * <h3>Ba pha, và pha giữa không cầm kết nối nào</h3>
 * Xóa một file là một lời gọi mạng ra Cloudinary khi chạy thật. Gói cả vòng lặp
 * xóa vào một {@code @Transactional} là giữ một kết nối cơ sở dữ liệu suốt hàng
 * chục lời gọi mạng — chính cái bẫy mà {@link TtsGenerationWorker} đã phải tách
 * ra để thoát, và với pool mười kết nối thì một lượt dọn đủ để làm cả trang web
 * chậm lại.
 *
 * <p>Nên method này <b>không</b> có {@code @Transactional}. Mỗi lời gọi
 * repository tự mở giao dịch ngắn của riêng nó, còn quãng xóa file ở giữa không
 * cầm gì cả.
 *
 * <p>Thứ tự cũng có chủ ý, và giống {@link StaleGenerationReconciler}: xóa file
 * trước, xóa hàng sau. Xóa hụt file thì để lại một file thừa còn tìm lại được;
 * xóa hàng trước rồi xóa hụt thì mất luôn manh mối để tìm ra file ấy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AudioRetentionSweeper {

    /**
     * Trần cho một lượt dọn.
     *
     * <p>Phần dư ở lại cho lượt sau. Lượt chạy đầu tiên ngay sau khi triển khai
     * có thể gặp một tồn đọng lớn, và xóa hàng nghìn file trong một lượt là hàng
     * nghìn lời gọi mạng nối nhau — đủ lâu để đụng phải một lần khởi động lại
     * giữa chừng.
     */
    private static final int MAX_PER_SWEEP = 200;

    /**
     * Một giờ một lượt. File lỗi thời không gấp; hạn lưu giữ đo bằng ngày.
     *
     * <p>Viết ra bằng phép nhân số nguyên chứ không phải {@code Duration.ofHours(1)}:
     * giá trị trong một annotation phải là hằng lúc biên dịch, mà một lời gọi
     * method thì không.
     */
    private static final long SWEEP_INTERVAL_MS = 60L * 60 * 1000;

    /**
     * Chờ trước lượt đầu tiên.
     *
     * <p>Lúc mới khởi động còn có {@link StaleGenerationReconciler} và phần nạp
     * dữ liệu đang chạy, mà nền tảng này ngủ khi vắng khách nên "lúc mới khởi
     * động" xảy ra vài chục lần một ngày. Nhường chỗ cho chúng xong đã.
     */
    private static final long INITIAL_DELAY_MS = 5L * 60 * 1000;

    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void sweep() {
        int retentionHours = storageProperties.staleRetentionHours();
        if (retentionHours == 0) {
            // Tắt hẳn — bản lỗi thời ở lại vĩnh viễn. Có ích khi đang dò lỗi.
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));

        // ---- Giao dịch ngắn thứ nhất, đóng lại ngay khi đọc xong ----
        List<AudioFile> expired = audioFileRepository.findStaleUpdatedBefore(cutoff);
        if (expired.isEmpty()) {
            return;
        }

        List<AudioFile> batch = expired.size() <= MAX_PER_SWEEP
                ? expired
                : expired.subList(0, MAX_PER_SWEEP);

        // ---- Ngoài mọi giao dịch: xóa file ----
        batch.forEach(audio -> {
            if (audio.getFilePath() != null) {
                storageService.deleteAudio(audio.getFilePath());
            }
        });

        // ---- Giao dịch ngắn thứ hai ----
        //
        // Dòng mốc thời gian ở audio_transcripts đi theo nhờ khóa ngoại
        // ON DELETE CASCADE phía cơ sở dữ liệu, không cần dọn ở đây — xem V3.
        audioFileRepository.deleteAllById(batch.stream().map(AudioFile::getId).toList());

        log.info("Đã dọn {} bản audio lỗi thời quá {} giờ{}",
                batch.size(), retentionHours,
                expired.size() > batch.size()
                        ? " (còn %d chờ lượt sau)".formatted(expired.size() - batch.size())
                        : "");
    }
}
