package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.*;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.AccessControlService;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử luồng Text-to-Speech — chức năng trọng tâm thứ hai của đề bài.
 *
 * <p>Mọi thứ bên ngoài đều được thay bằng mock, nên bài test <b>không gọi API thật</b>,
 * không tốn hạn mức và không cần mạng. Cái được kiểm ở đây là các quyết định của
 * service: chặn quyền, dùng lại cache, dọn bản hỏng, và đẩy việc nặng sang luồng nền.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TtsServiceTest {

    private static final Long CHAPTER_ID = 7L;
    private static final String VOICE = "banmai";

    @Mock
    private ChapterService chapterService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AudioFileRepository audioFileRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private TtsEngine ttsEngine;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TtsProperties properties;
    private TtsService ttsService;

    @BeforeEach
    void setUp() {
        properties = enabledProperties();
        ttsService = new TtsService(chapterService, accessControlService, audioFileRepository,
                storageService, properties, ttsEngine, eventPublisher);

        when(ttsEngine.hasAnyProvider()).thenReturn(true);
        when(ttsEngine.availableVoices()).thenReturn(List.of(
                new VoiceOptionDto(VOICE, "Ban Mai", "Nữ", "Miền Bắc", "fptai", "FPT.AI")));
        when(chapterService.findDetailEntity(CHAPTER_ID)).thenReturn(chapter(AccessLevel.PUBLIC));
        when(audioFileRepository.findTtsCache(anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(audioFileRepository.save(any(AudioFile.class))).thenAnswer(invocation -> {
            AudioFile saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
    }

    // ==================== Điều kiện tiên quyết ====================

    @Test
    @DisplayName("Máy chủ tắt TTS → báo lỗi rõ ràng, không đụng tới cơ sở dữ liệu")
    void tatTtsThiBaoLoi() {
        ttsService = new TtsService(chapterService, accessControlService, audioFileRepository,
                storageService, disabledProperties(), ttsEngine, eventPublisher);

        assertThatThrownBy(() -> ttsService.requestForChapter(CHAPTER_ID, null))
                .isInstanceOf(TtsException.class)
                .hasMessageContaining("tạm tắt");

        verifyNoInteractions(audioFileRepository);
    }

    @Test
    @DisplayName("Chưa cấu hình nhà cung cấp nào → thông báo chỉ rõ biến môi trường cần đặt")
    void chuaCoNhaCungCap() {
        when(ttsEngine.hasAnyProvider()).thenReturn(false);

        assertThatThrownBy(() -> ttsService.requestForChapter(CHAPTER_ID, null))
                .isInstanceOf(TtsException.class)
                .hasMessageContaining("FPT_TTS_API_KEY");

        verifyNoInteractions(audioFileRepository);
    }

    // ==================== Khóa chương ====================

    @Test
    @DisplayName("Chương bị khóa: không tạo audio, không phát sự kiện — TTS không lách được khóa")
    void chuongBiKhoaThiKhongTaoAudio() {
        when(chapterService.findDetailEntity(CHAPTER_ID)).thenReturn(chapter(AccessLevel.VIP));
        doThrow(new ChapterLockedException(AccessLevel.VIP, true))
                .when(accessControlService).requireAccess(any(Chapter.class));

        assertThatThrownBy(() -> ttsService.requestForChapter(CHAPTER_ID, null))
                .isInstanceOf(ChapterLockedException.class);

        // Đây là điểm mấu chốt của mục 4.5: nếu bỏ sót, người dùng có thể dùng TTS
        // để lấy nội dung chương mà họ không được phép đọc.
        verify(audioFileRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(TtsGenerationRequested.class));
    }

    @Test
    @DisplayName("Quyền được kiểm TRƯỚC khi tra cache")
    void kiemQuyenTruocKhiTraCache() {
        doThrow(new ChapterLockedException(AccessLevel.MEMBER, false))
                .when(accessControlService).requireAccess(any(Chapter.class));

        assertThatThrownBy(() -> ttsService.requestForChapter(CHAPTER_ID, null))
                .isInstanceOf(ChapterLockedException.class);

        // Cache có sẵn cũng không được phép trở thành cửa sau: chương đã bị khóa
        // thì ngay cả bản audio tạo sẵn từ trước cũng không được trả về.
        verify(audioFileRepository, never()).findTtsCache(anyLong(), anyString(), any());
    }

    // ==================== Cache ====================

    @Test
    @DisplayName("Đã có bản READY → trả lại luôn, không gọi API lần nữa")
    void cacheReadyThiDungLai() {
        AudioFile cached = audio(AudioStatus.READY, "da-co-san.mp3");
        when(audioFileRepository.findTtsCache(CHAPTER_ID, VOICE, 0)).thenReturn(Optional.of(cached));

        AudioInfoDto result = ttsService.requestForChapter(CHAPTER_ID, new TtsRequest(VOICE, 0));

        assertThat(result.status()).isEqualTo(AudioStatus.READY.name());
        verify(audioFileRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(TtsGenerationRequested.class));
    }

    @Test
    @DisplayName("Đang tạo dở → trả trạng thái PROCESSING, không xếp hàng lần hai")
    void dangTaoThiKhongXepHangLai() {
        when(audioFileRepository.findTtsCache(CHAPTER_ID, VOICE, 0))
                .thenReturn(Optional.of(audio(AudioStatus.PROCESSING, null)));

        AudioInfoDto result = ttsService.requestForChapter(CHAPTER_ID, new TtsRequest(VOICE, 0));

        assertThat(result.status()).isEqualTo(AudioStatus.PROCESSING.name());
        verify(eventPublisher, never()).publishEvent(any(TtsGenerationRequested.class));
    }

    @Test
    @DisplayName("Bản hỏng lần trước bị dọn đi rồi mới tạo lại")
    void banHongThiDonRoiTaoLai() {
        AudioFile failed = audio(AudioStatus.FAILED, "hong.mp3");
        when(audioFileRepository.findTtsCache(CHAPTER_ID, VOICE, 0)).thenReturn(Optional.of(failed));

        ttsService.requestForChapter(CHAPTER_ID, new TtsRequest(VOICE, 0));

        // Dọn cả file trên đĩa lẫn dòng trong bảng, nếu không lần sau lại gặp
        // đúng bản hỏng đó và không bao giờ tạo lại được.
        verify(storageService).deleteAudio("hong.mp3");
        verify(audioFileRepository).delete(failed);
        verify(eventPublisher).publishEvent(any(TtsGenerationRequested.class));
    }

    @Test
    @DisplayName("Đổi giọng là một khóa cache khác")
    void doiGiongLaCacheKhac() {
        when(ttsEngine.availableVoices()).thenReturn(List.of(
                new VoiceOptionDto(VOICE, "Ban Mai", "Nữ", "Miền Bắc", "fptai", "FPT.AI"),
                new VoiceOptionDto("leminh", "Lê Minh", "Nam", "Miền Bắc", "fptai", "FPT.AI")));

        ttsService.requestForChapter(CHAPTER_ID, new TtsRequest("leminh", 0));

        verify(audioFileRepository).findTtsCache(CHAPTER_ID, "leminh", 0);
    }

    // ==================== Xếp hàng bất đồng bộ ====================

    @Test
    @DisplayName("Yêu cầu mới: ghi PROCESSING rồi phát sự kiện, trả về ngay")
    void yeuCauMoiThiXepHang() {
        AudioInfoDto result = ttsService.requestForChapter(CHAPTER_ID, new TtsRequest(VOICE, 0));

        ArgumentCaptor<AudioFile> saved = ArgumentCaptor.forClass(AudioFile.class);
        verify(audioFileRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AudioStatus.PROCESSING);
        assertThat(saved.getValue().getSource()).isEqualTo(AudioSource.TTS);
        assertThat(saved.getValue().getVoice()).isEqualTo(VOICE);

        ArgumentCaptor<TtsGenerationRequested> event =
                ArgumentCaptor.forClass(TtsGenerationRequested.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().audioId()).isEqualTo(99L);
        assertThat(event.getValue().text()).isEqualTo("Nội dung chương để đọc.");

        // Việc gọi nhà cung cấp nằm ở luồng nền, nên hàm này phải trả về ngay
        // với trạng thái đang xử lý — chương dài mà làm đồng bộ sẽ timeout.
        assertThat(result.status()).isEqualTo(AudioStatus.PROCESSING.name());
        verify(ttsEngine, never()).synthesize(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Tốc độ ngoài khoảng cho phép bị kẹp về biên")
    void tocDoBiKep() {
        ttsService.requestForChapter(CHAPTER_ID, new TtsRequest(VOICE, 99));

        verify(audioFileRepository).findTtsCache(CHAPTER_ID, VOICE, 3);
    }

    @Test
    @DisplayName("Giọng lạ bị bỏ qua, quay về giọng đầu tiên có thật")
    void giongLaThiQuayVeMacDinh() {
        ttsService.requestForChapter(CHAPTER_ID, new TtsRequest("giong-khong-ton-tai", 0));

        verify(audioFileRepository).findTtsCache(CHAPTER_ID, VOICE, 0);
    }

    // ==================== Dữ liệu dựng sẵn ====================

    private static Chapter chapter(AccessLevel level) {
        return Chapter.builder()
                .id(CHAPTER_ID)
                .title("Chương 1")
                .chapterNumber(1)
                .accessLevel(level)
                .content("Nội dung chương để đọc.")
                .story(Story.builder().id(1L).title("Truyện thử").build())
                .build();
    }

    private static AudioFile audio(AudioStatus status, String filePath) {
        return AudioFile.builder()
                .id(55L)
                .chapter(chapter(AccessLevel.PUBLIC))
                .source(AudioSource.TTS)
                .status(status)
                .voice(VOICE)
                .speed(0)
                .filePath(filePath)
                .contentType("audio/mpeg")
                .build();
    }

    private static TtsProperties enabledProperties() {
        return new TtsProperties(true, List.of("fptai"),
                new TtsProperties.Fptai("https://api.fpt.ai/hmi/tts/v5", "khoa-gia", VOICE, 0),
                new TtsProperties.ElevenLabs("https://api.elevenlabs.io/v1", "", "", "eleven_multilingual_v2"));
    }

    private static TtsProperties disabledProperties() {
        return new TtsProperties(false, List.of("fptai"),
                new TtsProperties.Fptai("https://api.fpt.ai/hmi/tts/v5", "khoa-gia", VOICE, 0),
                new TtsProperties.ElevenLabs("https://api.elevenlabs.io/v1", "", "", "eleven_multilingual_v2"));
    }
}
