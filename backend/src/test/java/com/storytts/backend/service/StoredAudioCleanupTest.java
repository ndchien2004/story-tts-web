package com.storytts.backend.service;

import com.storytts.backend.repository.AudioFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Dọn file audio khi chương hoặc truyện bị xóa.
 *
 * <h3>Điều đáng kiểm nhất: thứ tự</h3>
 * File chỉ được xóa <b>sau khi giao dịch commit</b>. Ngược lại thì một giao dịch
 * hỏng giữa chừng sẽ để lại hàng trong cơ sở dữ liệu trỏ tới một file đã mất —
 * một bản audio trông như phát được mà bấm vào không ra gì. Đổi hướng thì cái
 * xấu nhất còn lại chỉ là rác trên đĩa, thứ đã có sẵn đường dọn ở
 * {@code /api/admin/storage/audit}.
 *
 * <p>Đường dẫn cũng phải được đọc <i>trước</i> lệnh xóa, vì sau đó không còn
 * hàng nào để tra ra chúng nữa.
 */
@ExtendWith(MockitoExtension.class)
class StoredAudioCleanupTest {

    @Mock
    private AudioFileRepository audioFileRepository;
    @Mock
    private StorageService storageService;

    private StoredAudioCleanup cleanup;

    @BeforeEach
    void setUp() {
        cleanup = new StoredAudioCleanup(audioFileRepository, storageService);
    }

    @AfterEach
    void tearDown() {
        // Bối cảnh giao dịch là biến toàn cục theo luồng: bỏ dọn thì bài kiểm sau
        // thừa hưởng phần đăng ký của bài trước.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("không có giao dịch nào đang chạy thì xóa ngay")
    void khongCoGiaoDichThiXoaNgay() {
        when(audioFileRepository.findFilePathsByChapter(7L))
                .thenReturn(List.of("mot.mp3", "hai.mp3"));

        cleanup.purgeChapterAfterCommit(7L);

        verify(storageService).deleteAudio("mot.mp3");
        verify(storageService).deleteAudio("hai.mp3");
    }

    @Test
    @DisplayName("đang trong giao dịch thì chờ commit rồi mới xóa")
    void trongGiaoDichThiChoCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(audioFileRepository.findFilePathsByChapter(7L)).thenReturn(List.of("mot.mp3"));

        cleanup.purgeChapterAfterCommit(7L);

        // Chưa commit: file vẫn còn nguyên. Đây là điểm của cả lớp — xóa sớm một
        // nhịp là để lại một bản ghi nói dối nếu giao dịch cuộn ngược.
        verifyNoInteractions(storageService);

        List<TransactionSynchronization> pending =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(pending).hasSize(1);

        pending.getFirst().afterCommit();
        verify(storageService).deleteAudio("mot.mp3");
    }

    @Test
    @DisplayName("giao dịch cuộn ngược thì không file nào bị xóa")
    void cuonNguocThiKhongXoaGi() {
        TransactionSynchronizationManager.initSynchronization();
        when(audioFileRepository.findFilePathsByChapter(7L)).thenReturn(List.of("mot.mp3"));

        cleanup.purgeChapterAfterCommit(7L);

        // Không gọi afterCommit: mô phỏng đúng một giao dịch hỏng. Hàng vẫn còn
        // trong cơ sở dữ liệu, nên file cũng phải còn.
        verify(storageService, never()).deleteAudio(anyString());
    }

    @Test
    @DisplayName("chương không có file nào thì không đăng ký gì cả")
    void khongCoFileThiKhongDangKyGi() {
        TransactionSynchronizationManager.initSynchronization();
        when(audioFileRepository.findFilePathsByChapter(7L)).thenReturn(List.of());

        cleanup.purgeChapterAfterCommit(7L);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("một file xóa hỏng không kéo theo những file còn lại")
    void motFileHongKhongKeoTheoCaiKhac() {
        when(audioFileRepository.findFilePathsByStory(3L))
                .thenReturn(List.of("mot.mp3", "hai.mp3", "ba.mp3"));
        doThrow(new RuntimeException("Cloudinary từ chối")).when(storageService).deleteAudio("hai.mp3");

        cleanup.purgeStoryAfterCommit(3L);

        // Tới đây thì hàng trong cơ sở dữ liệu đã biến mất rồi, nên bỏ dở giữa
        // chừng chỉ để lại nhiều rác hơn.
        verify(storageService).deleteAudio("mot.mp3");
        verify(storageService).deleteAudio("ba.mp3");
    }
}
