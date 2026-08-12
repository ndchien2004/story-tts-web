package com.storytts.backend.service.tts;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bản audio người đọc tự dựng chỉ sống trong phiên đăng nhập của người ấy.
 *
 * <p>Nó không phải kho của trang web, cũng không phải kho của người dùng: nó là
 * thứ dựng tạm cho một buổi đọc. Hết buổi thì bỏ đi — đĩa không phình lên theo
 * số lần bấm nút, và bảng quản trị không phải mang những dòng mà admin không
 * đặt ở đó.
 *
 * <p>Dọn ở cả hai đầu của phiên. Đăng xuất là đường thường gặp, nhưng đóng thẳng
 * trình duyệt thì không ai báo cho máy chủ biết cả — nên lúc đăng nhập cũng dọn
 * một lượt, và đó mới là chỗ bảo đảm chắc chắn rằng phiên trước không sót lại gì.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReaderNarrationCleanup {

    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;

    /**
     * Bỏ mọi bản người này đã tự dựng.
     *
     * @return số bản đã bỏ, để chỗ gọi ghi nhật ký
     */
    @Transactional
    public int discardFor(Long userId) {
        if (userId == null) {
            return 0;
        }

        // Bản đang dựng dở thì để yên: luồng nền còn đang ghi vào hàng ấy, xóa
        // bây giờ chỉ để lại một file mồ côi trên đĩa và một lỗi trong nhật ký.
        // Lần đăng nhập sau nó đã xong hoặc đã hỏng, và bị dọn ở lượt đó.
        List<AudioFile> finished = audioFileRepository.findByRequestedById(userId).stream()
                .filter(audio -> audio.getStatus() != AudioStatus.PROCESSING)
                .toList();

        if (finished.isEmpty()) {
            return 0;
        }

        finished.forEach(audio -> storageService.deleteAudio(audio.getFilePath()));
        audioFileRepository.deleteAll(finished);

        log.info("Đã bỏ {} bản audio tạm của người dùng {}", finished.size(), userId);
        return finished.size();
    }
}
