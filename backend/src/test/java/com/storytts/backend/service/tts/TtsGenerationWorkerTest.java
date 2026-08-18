package com.storytts.backend.service.tts;

import com.storytts.backend.exception.TtsException;
import com.storytts.backend.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử ranh giới giao dịch của luồng dựng audio nền.
 *
 * <p>Điều đang được giữ ở đây không phải là "audio dựng đúng" — phần ấy thuộc về
 * {@link TtsServiceTest} và {@code WordAlignerTest}. Điều được giữ là <b>lệnh gọi
 * nhà cung cấp nằm ngoài mọi giao dịch</b>: nó phải rơi vào đúng khoảng giữa hai
 * lần chạm cơ sở dữ liệu, chứ không nằm gọn trong một. Đó là khác biệt giữa việc
 * giữ một kết nối trong pool vài mili giây và giữ nó vài phút.
 *
 * <p>{@link TtsGenerationRecords} được mock chính vì mỗi method của nó là một giao
 * dịch riêng; thứ tự các lần gọi nó chính là thứ tự các giao dịch đã mở ra.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TtsGenerationWorkerTest {

    private static final Long AUDIO_ID = 42L;
    private static final String FILE_NAME = "sinh-ra-tu-test.mp3";

    /** Phiên bản nội dung mà lượt dựng này đã chụp lúc bắt đầu. */
    private static final int CHAPTER_VERSION = 3;

    private static final TtsGenerationRequested EVENT =
            new TtsGenerationRequested(AUDIO_ID, "Nội dung chương.", CHAPTER_VERSION, "el:giong-mot", 0);

    @Mock
    private TtsEngine ttsEngine;
    @Mock
    private StorageService storageService;
    @Mock
    private TtsGenerationRecords records;

    @InjectMocks
    private TtsGenerationWorker worker;

    private SynthesisResult synthesised() {
        return new SynthesisResult(new byte[] {1, 2, 3}, List.of(), "elevenlabs", "ElevenLabs");
    }

    @Test
    @DisplayName("lệnh gọi nhà cung cấp nằm giữa hai lần chạm cơ sở dữ liệu, không nằm trong")
    void synthesisRunsBetweenTheTwoShortTransactions() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME))).thenReturn(TtsGenerationRecords.Outcome.READY);

        worker.onGenerationRequested(EVENT);

        InOrder order = inOrder(records, ttsEngine, storageService);
        order.verify(records).stillQueued(AUDIO_ID);          // giao dịch 1, đã đóng
        order.verify(ttsEngine).synthesize(anyString(), anyString(), anyInt());
        order.verify(storageService).storeAudio(any(byte[].class), anyString());
        order.verify(records).markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME));  // giao dịch 2
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("bản ghi đã bị xóa thì không gọi nhà cung cấp — lần gọi ấy tính tiền")
    void skipsSynthesisWhenTheRowIsGone() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(false);

        worker.onGenerationRequested(EVENT);

        verifyNoInteractions(ttsEngine, storageService);
        verify(records, never()).markReady(any(), anyInt(), any(), anyString());
        verify(records, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("hàng biến mất trong lúc dựng thì file vừa ghi bị dọn, không để lại rác")
    void discardsTheFileWhenTheRowVanishesMidFlight() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME))).thenReturn(TtsGenerationRecords.Outcome.GONE);

        worker.onGenerationRequested(EVENT);

        verify(storageService).deleteAudio(FILE_NAME);
    }

    /**
     * Chương bị sửa trong lúc dựng: bản vừa xong không được giữ file lại.
     *
     * <p>Khác với bản thành lỗi thời vì Admin sửa chương — bản ấy đã từng mang cờ
     * READY nên có thể đang phát dở trong tai một ai đó, và file của nó được giữ
     * tới hết hạn lưu giữ. Bản ở đây thì chưa bao giờ READY, nên không có người
     * nghe nào để giữ nó lại cho; và giữ cũng vô ích, vì phiên bản là một con số
     * tăng nên nội dung cũ sẽ không bao giờ mang lại số cũ.
     */
    @Test
    @DisplayName("chương đổi trong lúc dựng thì kết quả là lỗi thời, và file vừa ghi bị dọn ngay")
    void discardsTheFileWhenTheChapterMovedOnMidFlight() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME)))
                .thenReturn(TtsGenerationRecords.Outcome.STALE);

        worker.onGenerationRequested(EVENT);

        verify(storageService).deleteAudio(FILE_NAME);
        // Lỗi thời không phải là hỏng: không có gì để báo lỗi cho người dùng.
        verify(records, never()).markFailed(any(), anyString());
    }

    /** Phiên bản đã chụp phải đi nguyên vẹn tới lệnh ghi cuối cùng. */
    @Test
    @DisplayName("phiên bản chụp lúc bắt đầu là phiên bản đem đi so lúc kết thúc")
    void passesTheSnapshottedVersionThrough() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME)))
                .thenReturn(TtsGenerationRecords.Outcome.READY);

        worker.onGenerationRequested(EVENT);

        verify(records).markReady(eq(AUDIO_ID), eq(CHAPTER_VERSION), any(), eq(FILE_NAME));
    }

    @Test
    @DisplayName("nhà cung cấp hỏng thì bản ghi được đánh dấu FAILED kèm lý do")
    void marksFailedWhenTheProviderFails() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt()))
                .thenThrow(new TtsException("hết hạn mức"));

        worker.onGenerationRequested(EVENT);

        verify(records).markFailed(AUDIO_ID, "hết hạn mức");
        verify(records, never()).markReady(any(), anyInt(), any(), anyString());
        // Chưa ghi file nào nên cũng không có gì để dọn.
        verify(storageService, never()).deleteAudio(anyString());
    }

    @Test
    @DisplayName("ghi cơ sở dữ liệu hỏng sau khi đã ghi đĩa thì file ấy được dọn theo")
    void discardsTheFileWhenTheFinalWriteFails() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), anyInt(), any(), eq(FILE_NAME)))
                .thenThrow(new IllegalStateException("mất kết nối lúc commit"));

        worker.onGenerationRequested(EVENT);

        verify(storageService).deleteAudio(FILE_NAME);
        verify(records).markFailed(eq(AUDIO_ID), contains("Không tạo được audio"));
    }
}
