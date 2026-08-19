package com.storytts.backend.service;

import com.storytts.backend.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Dọn file audio trên nơi lưu khi chương hoặc truyện chứa nó bị xóa.
 *
 * <h3>Vì sao phải có lớp này</h3>
 * Xóa một chương chỉ xóa <i>hàng</i> {@code audio_files} — Hibernate cascade lo
 * việc đó. File MP3 thật thì nằm ngoài cơ sở dữ liệu, và không ai bảo nó biến
 * mất. Với bản dựng bằng ElevenLabs thì mỗi file bỏ quên là một khoản tiền đã
 * trả nằm lại vĩnh viễn trên Cloudinary, không có gì trỏ tới và không có gì dọn.
 *
 * <p>Đường xóa một bản audio lẻ ({@code AudioService.delete}) vốn đã dọn file.
 * Hai đường còn lại — xóa chương, xóa truyện — thì không, và lớp này bù đúng
 * chỗ ấy.
 *
 * <h3>Vì sao xóa file SAU khi giao dịch commit</h3>
 * Thứ tự này là thứ duy nhất không để lại trạng thái hỏng nào:
 *
 * <pre>
 *   xóa file trước, rồi commit → giao dịch hỏng giữa chừng thì hàng còn
 *                                nguyên nhưng file mất: một bản audio trông
 *                                như phát được mà bấm vào không ra gì
 *   commit rồi mới xóa file    → tiến trình chết giữa chừng thì file ở lại
 *                                mà không hàng nào trỏ tới: rác, nhưng vô hại
 * </pre>
 *
 * <p>Chọn hướng để lại rác thay vì hướng để lại một bản ghi nói dối. Và rác thì
 * đã có đường dọn sẵn: {@code GET /api/admin/storage/audit}.
 *
 * <p>Đường dẫn phải được đọc <b>trong</b> giao dịch, trước khi hàng biến mất —
 * đó là lý do lớp này nhận id rồi tự tra, chứ không nhận sẵn danh sách file.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoredAudioCleanup {

    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;

    /** Gọi ngay trước khi xóa một chương, trong cùng giao dịch ấy. */
    public void purgeChapterAfterCommit(Long chapterId) {
        purgeAfterCommit(audioFileRepository.findFilePathsByChapter(chapterId),
                "chương " + chapterId);
    }

    /** Gọi ngay trước khi xóa một truyện, trong cùng giao dịch ấy. */
    public void purgeStoryAfterCommit(Long storyId) {
        purgeAfterCommit(audioFileRepository.findFilePathsByStory(storyId),
                "truyện " + storyId);
    }

    private void purgeAfterCommit(List<String> paths, String what) {
        if (paths.isEmpty()) {
            return;
        }

        // Không có giao dịch nào đang chạy thì cũng không có gì để chờ commit.
        // Xảy ra ở test và ở những lời gọi trực tiếp; xóa ngay là đúng.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAll(paths, what);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteAll(paths, what);
            }
        });
    }

    private void deleteAll(List<String> paths, String what) {
        // Một file hỏng không được kéo theo những file còn lại: tới đây thì hàng
        // trong cơ sở dữ liệu đã biến mất rồi, nên bỏ dở giữa chừng chỉ để lại
        // nhiều rác hơn.
        int removed = 0;
        for (String path : paths) {
            try {
                storageService.deleteAudio(path);
                removed++;
            } catch (RuntimeException ex) {
                log.warn("Không xóa được file audio {} của {}: {}", path, what, ex.getMessage());
            }
        }
        log.info("Đã dọn {}/{} file audio của {}", removed, paths.size(), what);
    }
}
