package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.audio.ChapterTranscriptDto;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AudioTranscriptRepository;
import com.storytts.backend.service.tts.TranscriptCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Bản audio người đọc tự dựng là của riêng người ấy.
 *
 * <p>Biết được số thứ tự của một bản audio không phải là được phép nghe nó. Ẩn
 * trên giao diện chỉ là phép lịch sự; thứ thật sự chặn nằm ở đây, vì đường phát
 * audio nhận số thứ tự từ địa chỉ URL và ai cũng gõ tay được.
 *
 * <p>Bản của khu quản trị thì ngược lại: nó được dựng sẵn cho mọi người, kể cả
 * Khách chưa đăng nhập.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioOwnershipTest {

    private static final Long CHAPTER_ID = 7L;
    private static final Long AUDIO_ID = 99L;

    private static final User NGUOI_DUNG_MOT = User.builder().id(1L).username("mot").build();
    private static final User NGUOI_DUNG_HAI = User.builder().id(2L).username("hai").build();

    @Mock
    private AudioFileRepository audioFileRepository;
    @Mock
    private AudioTranscriptRepository audioTranscriptRepository;
    @Mock
    private TranscriptCodec transcriptCodec;
    @Mock
    private ChapterService chapterService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private StorageService storageService;
    @Mock
    private ViewEventService viewEventService;
    @Mock
    private CurrentUserService currentUserService;

    private AudioService audioService;

    @BeforeEach
    void setUp() {
        audioService = new AudioService(audioFileRepository, audioTranscriptRepository,
                transcriptCodec, chapterService, accessControlService, storageService,
                viewEventService, currentUserService);

        when(chapterService.findDetailEntity(CHAPTER_ID)).thenReturn(chuong());
    }

    @Test
    @DisplayName("Người khác không xem được trạng thái bản audio của tôi")
    void nguoiKhacKhongXemDuocBanCuaToi() {
        dangCo(banCua(NGUOI_DUNG_MOT));
        dangDangNhapLa(NGUOI_DUNG_HAI);

        assertThatThrownBy(() -> audioService.trackStatus(CHAPTER_ID, AUDIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Người khác không phát được bản audio của tôi dù biết số thứ tự")
    void nguoiKhacKhongPhatDuocBanCuaToi() {
        dangCo(banCua(NGUOI_DUNG_MOT));
        dangDangNhapLa(NGUOI_DUNG_HAI);

        assertThatThrownBy(() -> audioService.openForStreaming(CHAPTER_ID, AUDIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Khách chưa đăng nhập cũng không nghe được bản của người khác")
    void khachKhongNgheDuocBanCuaNguoiKhac() {
        dangCo(banCua(NGUOI_DUNG_MOT));
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> audioService.openForStreaming(CHAPTER_ID, AUDIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Chính chủ thì xem được bản mình đã dựng")
    void chinhChuThiXemDuoc() {
        dangCo(banCua(NGUOI_DUNG_MOT));
        dangDangNhapLa(NGUOI_DUNG_MOT);

        assertThatCode(() -> audioService.trackStatus(CHAPTER_ID, AUDIO_ID))
                .doesNotThrowAnyException();
    }

    /**
     * Mốc thời gian gần như là nội dung chương chép lại thành mảng, nên nó phải
     * đi qua đúng cánh cửa mà đường phát audio đi qua — nếu không thì có một
     * đường vòng đọc được chữ của một chương trả phí mà không cần phát nó.
     */
    @Test
    @DisplayName("Người khác không lấy được mốc thời gian của bản audio của tôi")
    void nguoiKhacKhongLayDuocMocThoiGianCuaToi() {
        dangCo(banCua(NGUOI_DUNG_MOT));
        dangDangNhapLa(NGUOI_DUNG_HAI);

        assertThatThrownBy(() -> audioService.transcript(CHAPTER_ID, AUDIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Bản không có mốc thời gian trả về mảng rỗng, không phải lỗi")
    void banKhongCoMocThiTraVeMangRong() {
        dangCo(banCua(null));
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());
        when(audioTranscriptRepository.findById(AUDIO_ID)).thenReturn(Optional.empty());

        ChapterTranscriptDto transcript = audioService.transcript(CHAPTER_ID, AUDIO_ID);

        assertThat(transcript.timestamps()).isEmpty();
        assertThat(transcript.wordCount()).isZero();
        assertThat(transcript.audioUrl()).isEqualTo("/api/chapters/7/audio/99");
    }

    @Test
    @DisplayName("Bản của khu quản trị thì ai cũng xem được, kể cả Khách")
    void banCuaQuanTriThiAiCungXemDuoc() {
        dangCo(banCua(null));
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        assertThatCode(() -> audioService.trackStatus(CHAPTER_ID, AUDIO_ID))
                .doesNotThrowAnyException();
    }

    private void dangCo(AudioFile audio) {
        when(audioFileRepository.findById(anyLong())).thenReturn(Optional.of(audio));
    }

    private void dangDangNhapLa(User user) {
        when(currentUserService.currentUserId()).thenReturn(Optional.of(user.getId()));
    }

    /** {@code chuNhan} rỗng nghĩa là bản của khu quản trị. */
    private AudioFile banCua(User chuNhan) {
        return AudioFile.builder()
                .id(AUDIO_ID)
                .chapter(chuong())
                .source(AudioSource.TTS)
                .status(AudioStatus.READY)
                .filePath("mot-ban-nao-do.mp3")
                .requestedBy(chuNhan)
                .build();
    }

    private Chapter chuong() {
        return Chapter.builder()
                .id(CHAPTER_ID)
                .title("Chương 1")
                .chapterNumber(1)
                .accessLevel(AccessLevel.PUBLIC)
                .content("Nội dung thử.")
                .story(Story.builder().id(1L).title("Truyện thử").build())
                .build();
    }
}
