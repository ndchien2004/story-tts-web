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

    private static final TtsGenerationRequested EVENT =
            new TtsGenerationRequested(AUDIO_ID, "Nội dung chương.", "el:giong-mot", 0);

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
        when(records.markReady(eq(AUDIO_ID), any(), eq(FILE_NAME))).thenReturn(true);

        worker.onGenerationRequested(EVENT);

        InOrder order = inOrder(records, ttsEngine, storageService);
        order.verify(records).stillQueued(AUDIO_ID);          // giao dịch 1, đã đóng
        order.verify(ttsEngine).synthesize(anyString(), anyString(), anyInt());
        order.verify(storageService).storeAudio(any(byte[].class), anyString());
        order.verify(records).markReady(eq(AUDIO_ID), any(), eq(FILE_NAME));  // giao dịch 2
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("bản ghi đã bị xóa thì không gọi nhà cung cấp — lần gọi ấy tính tiền")
    void skipsSynthesisWhenTheRowIsGone() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(false);

        worker.onGenerationRequested(EVENT);

        verifyNoInteractions(ttsEngine, storageService);
        verify(records, never()).markReady(any(), any(), anyString());
        verify(records, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("hàng biến mất trong lúc dựng thì file vừa ghi bị dọn, không để lại rác")
    void discardsTheFileWhenTheRowVanishesMidFlight() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), any(), eq(FILE_NAME))).thenReturn(false);

        worker.onGenerationRequested(EVENT);

        verify(storageService).deleteAudio(FILE_NAME);
    }

    @Test
    @DisplayName("nhà cung cấp hỏng thì bản ghi được đánh dấu FAILED kèm lý do")
    void marksFailedWhenTheProviderFails() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt()))
                .thenThrow(new TtsException("hết hạn mức"));

        worker.onGenerationRequested(EVENT);

        verify(records).markFailed(AUDIO_ID, "hết hạn mức");
        verify(records, never()).markReady(any(), any(), anyString());
        // Chưa ghi file nào nên cũng không có gì để dọn.
        verify(storageService, never()).deleteAudio(anyString());
    }

    @Test
    @DisplayName("ghi cơ sở dữ liệu hỏng sau khi đã ghi đĩa thì file ấy được dọn theo")
    void discardsTheFileWhenTheFinalWriteFails() {
        when(records.stillQueued(AUDIO_ID)).thenReturn(true);
        when(ttsEngine.synthesize(anyString(), anyString(), anyInt())).thenReturn(synthesised());
        when(storageService.storeAudio(any(byte[].class), anyString())).thenReturn(FILE_NAME);
        when(records.markReady(eq(AUDIO_ID), any(), eq(FILE_NAME)))
                .thenThrow(new IllegalStateException("mất kết nối lúc commit"));

        worker.onGenerationRequested(EVENT);

        verify(storageService).deleteAudio(FILE_NAME);
        verify(records).markFailed(eq(AUDIO_ID), contains("Không tạo được audio"));
    }
}
