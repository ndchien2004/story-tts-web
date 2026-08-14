package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsReaderStatusDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.LoginRequiredException;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.exception.TtsQuotaExceededException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử ngân sách của nút "Nghe bằng AI" (mục 4.5).
 *
 * <p>Đề bài đòi người đọc tự bấm tạo audio, mà mỗi bản mới là một lần gọi API tính
 * tiền. Những gì kiểm ở đây là phần giữ cho việc đó không thành một lỗ hổng chi phí:
 * bắt đăng nhập, hạn mức theo ngày, trần chung, trần độ dài chương — và quan trọng
 * nhất, <b>bản đã có thì không tốn lượt nào</b>.
 *
 * <p>{@link TtsService} được mock, và mock đó gọi ngược lại
 * {@link TtsService.ReaderBudget} đúng như bản thật: chỉ gọi khi phải dựng bản mới.
 * Nhờ vậy test được cả nội dung các mức chặn lẫn chỗ chúng được hỏi tới.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaderTtsServiceTest {

    private static final Long CHAPTER_ID = 7L;
    private static final Long USER_ID = 42L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private TtsService ttsService;
    @Mock
    private TtsEngine ttsEngine;
    @Mock
    private AudioFileRepository audioFileRepository;
    @Mock
    private CurrentUserService currentUserService;

    private ReaderTtsService readerTtsService;

    @BeforeEach
    void setUp() {
        configure(TtsProperties.Reader.defaults());

        when(ttsEngine.hasAnyProvider()).thenReturn(true);
        signedInAs(member());
        when(audioFileRepository.countReaderRequestsBy(anyLong(), any())).thenReturn(0L);
        when(audioFileRepository.countReaderRequests(any())).thenReturn(0L);

        // Bản thật chỉ hỏi ngân sách khi không còn bản nào dùng lại được.
        stubDelegateAsNewGeneration();
    }

    // ==================== Điều kiện tiên quyết ====================

    @Test
    @DisplayName("Tắt đường người đọc → báo lỗi, không đụng tới TtsService")
    void tatDuongNguoiDoc() {
        configure(new TtsProperties.Reader(false, 3, 10, 100, 20_000));

        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(TtsException.class)
                .hasMessageContaining("tạm tắt");

        verifyNoInteractions(ttsService, audioFileRepository);
    }

    @Test
    @DisplayName("Chưa đăng nhập → 401, và không hỏi gì về chương (không lộ chương có tồn tại hay không)")
    void chuaDangNhapThiKhongVao() {
        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(LoginRequiredException.class)
                .hasMessageContaining("đăng nhập");

        verifyNoInteractions(ttsService);
    }

    // ==================== Khóa cache do máy chủ quyết định ====================

    @Test
    @DisplayName("Người đọc không chọn được giọng và tốc độ — hai thứ đó là khóa cache")
    void giongVaTocDoDoMayChuQuyet() {
        readerTtsService.request(CHAPTER_ID);

        ArgumentCaptor<TtsRequest> sent = ArgumentCaptor.forClass(TtsRequest.class);
        verify(ttsService).requestForChapter(eq(CHAPTER_ID), sent.capture(), any());

        // Giọng để null cho TtsService tự chọn giọng mặc định; tốc độ luôn là mặc
        // định của máy chủ. Nếu để người đọc chọn thì mỗi lựa chọn lại sinh thêm
        // một file cho cùng một chương.
        assertThat(sent.getValue().voice()).isNull();
        assertThat(sent.getValue().speed()).isZero();
    }

    // ==================== Bản đã có thì miễn phí ====================

    @Test
    @DisplayName("Trúng bản đã có → không đếm hạn mức của ai cả")
    void banDaCoThiKhongTonLuot() {
        // Không gọi ngân sách: đúng như TtsService làm khi cache dùng lại được.
        when(ttsService.requestForChapter(eq(CHAPTER_ID), any(), any()))
                .thenReturn(readyDto());

        readerTtsService.request(CHAPTER_ID);

        // Đây là điểm khiến chức năng này chấp nhận được: nghe lại một chương đã
        // có audio không phải là một lượt, vì nó không tốn thêm đồng nào.
        verify(audioFileRepository, never()).countReaderRequestsBy(anyLong(), any());
        verify(audioFileRepository, never()).countReaderRequests(any());
    }

    // ==================== Hạn mức ====================

    @Test
    @DisplayName("Thành viên hết lượt trong ngày → 429 kèm đúng hạn mức của bậc mình")
    void thanhVienHetLuot() {
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(3L);

        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(TtsQuotaExceededException.class)
                .hasFieldOrPropertyWithValue("scope", TtsQuotaExceededException.Scope.USER)
                .hasFieldOrPropertyWithValue("limit", 3);
    }

    @Test
    @DisplayName("VIP vượt mức của Thành viên nhưng còn trong mức VIP → vẫn được tạo")
    void vipCoHanMucRieng() {
        signedInAs(vip());
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(5L);

        assertThat(readerTtsService.request(CHAPTER_ID)).isNotNull();
    }

    @Test
    @DisplayName("Admin không bị hạn mức nào")
    void adminKhongBiHanMuc() {
        signedInAs(admin());
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(999L);

        assertThat(readerTtsService.request(CHAPTER_ID)).isNotNull();
    }

    @Test
    @DisplayName("Hạn mức 0 chặn hẳn, -1 là không giới hạn")
    void hanMucBienDuoi() {
        configure(new TtsProperties.Reader(true, 0, 10, 100, 20_000));
        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(TtsQuotaExceededException.class);

        configure(new TtsProperties.Reader(true, -1, 10, 100, 20_000));
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(500L);
        assertThat(readerTtsService.request(CHAPTER_ID)).isNotNull();
    }

    @Test
    @DisplayName("Cả hệ thống hết lượt → trả lời là GLOBAL, không phải 'bạn hết lượt'")
    void tranChungXetTruoc() {
        when(audioFileRepository.countReaderRequests(any())).thenReturn(100L);
        // Người này vẫn còn nguyên phần của mình.
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(0L);

        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(TtsQuotaExceededException.class)
                .hasFieldOrPropertyWithValue("scope", TtsQuotaExceededException.Scope.GLOBAL);
    }

    @Test
    @DisplayName("Hạn mức đếm theo ngày ở Việt Nam, không theo UTC")
    void hanMucTinhTheoNgayVietNam() {
        readerTtsService.request(CHAPTER_ID);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(audioFileRepository).countReaderRequestsBy(eq(USER_ID), since.capture());

        assertThat(since.getValue())
                .isEqualTo(LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant());
    }

    // ==================== Trần độ dài chương ====================

    @Test
    @DisplayName("Chương dài quá mức cho phép → từ chối, thông báo nêu rõ hai con số")
    void chuongDaiQuaThiTuChoi() {
        configure(new TtsProperties.Reader(true, 3, 10, 100, 50));

        assertThatThrownBy(() -> readerTtsService.request(CHAPTER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("Chương dài quá mức nhưng đã có audio → vẫn nghe được")
    void chuongDaiMaDaCoAudioThiVanNghe() {
        configure(new TtsProperties.Reader(true, 3, 10, 100, 50));
        when(ttsService.requestForChapter(eq(CHAPTER_ID), any(), any())).thenReturn(readyDto());

        // Trần độ dài là để chặn một khoản chi, không phải để chặn việc nghe.
        assertThat(readerTtsService.request(CHAPTER_ID).status()).isEqualTo("READY");
    }

    // ==================== Trạng thái cho trang đọc ====================

    @Test
    @DisplayName("Chưa cấu hình nhà cung cấp → trạng thái báo tắt, để trang đọc không hiện nút vô nghĩa")
    void trangThaiTatKhiChuaCoNhaCungCap() {
        when(ttsEngine.hasAnyProvider()).thenReturn(false);

        assertThat(readerTtsService.status().enabled()).isFalse();
    }

    @Test
    @DisplayName("Khách: không có số lượt nào để hiện, nhưng tính năng vẫn là đang bật")
    void trangThaiCuaKhach() {
        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());

        TtsReaderStatusDto status = readerTtsService.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.dailyQuota()).isNull();
        assertThat(status.remainingToday()).isNull();
    }

    @Test
    @DisplayName("Số lượt còn lại bị kẹp bởi trần chung — không hứa nhiều hơn hệ thống còn")
    void soLuotConLaiKepBoiTranChung() {
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(0L);
        when(audioFileRepository.countReaderRequests(any())).thenReturn(99L);

        TtsReaderStatusDto status = readerTtsService.status();

        assertThat(status.dailyQuota()).isEqualTo(3);
        assertThat(status.remainingToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dùng quá hạn mức rồi hạ hạn mức xuống → số lượt còn lại là 0, không phải số âm")
    void soLuotConLaiKhongAm() {
        when(audioFileRepository.countReaderRequestsBy(eq(USER_ID), any())).thenReturn(10L);

        assertThat(readerTtsService.status().remainingToday()).isZero();
    }

    // ==================== Dữ liệu dựng sẵn ====================

    private void configure(TtsProperties.Reader reader) {
        TtsProperties properties = new TtsProperties(true, List.of("elevenlabs"), 0,
                new TtsProperties.ElevenLabs("https://api.elevenlabs.io/v1", "khoa-gia", "el:mot",
                        "eleven_multilingual_v2"),
                reader);
        readerTtsService = new ReaderTtsService(ttsService, ttsEngine, properties,
                audioFileRepository, currentUserService);
    }

    /**
     * Mock {@link TtsService} theo đúng hành vi khi phải dựng bản mới: gọi ngân
     * sách trước, rồi trả về PROCESSING.
     */
    private void stubDelegateAsNewGeneration() {
        when(ttsService.requestForChapter(eq(CHAPTER_ID), any(), any())).thenAnswer(invocation -> {
            invocation.getArgument(2, TtsService.ReaderBudget.class)
                    .beforeNewGeneration(chapter());
            return processingDto();
        });
    }

    private void signedInAs(User user) {
        when(currentUserService.currentPrincipal())
                .thenReturn(Optional.of(new AppUserPrincipal(user)));
        when(currentUserService.currentUserReference()).thenReturn(Optional.of(user));
    }

    private static User member() {
        return User.builder().id(USER_ID).username("thanh-vien").passwordHash("x")
                .role(com.storytts.backend.domain.Role.MEMBER).enabled(true).build();
    }

    private static User vip() {
        return User.builder().id(USER_ID).username("vip").passwordHash("x")
                .role(com.storytts.backend.domain.Role.MEMBER).vipGranted(true).enabled(true).build();
    }

    private static User admin() {
        return User.builder().id(USER_ID).username("admin").passwordHash("x")
                .role(com.storytts.backend.domain.Role.ADMIN).enabled(true).build();
    }

    private static Chapter chapter() {
        return Chapter.builder()
                .id(CHAPTER_ID)
                .title("Chương 1")
                .chapterNumber(1)
                .accessLevel(AccessLevel.PUBLIC)
                .content("Nội dung chương dài hơn năm mươi ký tự để thử trần độ dài chương.")
                .story(Story.builder().id(1L).title("Truyện thử").build())
                .build();
    }

    private static AudioInfoDto processingDto() {
        return new AudioInfoDto(99L, CHAPTER_ID, "TTS", AudioInfoDto.OWNER_SESSION, "PROCESSING", null,
                "el:mot", 0, null, null, null, null, false);
    }

    private static AudioInfoDto readyDto() {
        return new AudioInfoDto(99L, CHAPTER_ID, "TTS", AudioInfoDto.OWNER_SESSION, "READY",
                "/api/chapters/7/audio/99", "el:mot", 0, "elevenlabs", null, 1024L, null, true);
    }
}
