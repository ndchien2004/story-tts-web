package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Story;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.service.tts.SynthesisResult;
import com.storytts.backend.service.tts.TranscriptCodec;
import com.storytts.backend.service.tts.TtsGenerationRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Bất biến trung tâm của tính năng, kiểm trên cơ sở dữ liệu thật:
 *
 * <pre>
 *   AUDIO_HIỆN_TẠI.contentVersion == CHƯƠNG_HIỆN_TẠI.contentVersion
 * </pre>
 *
 * <p>Không kiểm được bằng mock. Ba thứ giữ bất biến này đều do cơ sở dữ liệu
 * thực thi chứ không do mã nguồn: câu UPDATE hàng loạt đánh dấu bản cũ ngay
 * trong giao dịch sửa chương, mệnh đề lọc theo phiên bản trong câu truy vấn tìm
 * audio hiện tại, và cột {@code @Version} chặn hai lần ghi chồng nhau. Thay
 * repository bằng mock là thay đúng phần đang được kiểm.
 *
 * <p>Kịch bản ở {@link #kichBanBatBuoc()} là kịch bản bắt buộc của đề bài, viết
 * theo đúng thứ tự đề ra.
 */
@DataJpaTest
// ObjectMapper đi kèm vì TranscriptCodec cần nó, mà @DataJpaTest chỉ dựng phần
// bền vững của ứng dụng nên không có sẵn bean ấy.
@Import({ChapterService.class, TtsGenerationRecords.class, TranscriptCodec.class,
        com.fasterxml.jackson.databind.ObjectMapper.class})
class ContentAudioVersioningJpaTest {

    private static final String NOI_DUNG_GOC = "Chữ của phiên bản đầu tiên.";
    private static final String NOI_DUNG_MOI = "Chữ đã được sửa lại.";

    @Autowired
    private ChapterService chapterService;
    @Autowired
    private TtsGenerationRecords records;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private AudioFileRepository audioFileRepository;
    @Autowired
    private TestEntityManager entityManager;

    /** Quyền đọc không phải thứ đang kiểm ở đây; mọi chương đều mở. */
    @MockitoBean
    private ChapterAccessService chapterAccessService;
    @MockitoBean
    private CurrentUserService currentUserService;

    private Long chapterId;

    @BeforeEach
    void setUp() {
        Story story = storyRepository.save(Story.builder().title("Truyện thử").build());
        chapterId = chapterRepository.save(Chapter.builder()
                .story(story)
                .title("Chương 1")
                .content(NOI_DUNG_GOC)
                .chapterNumber(1)
                .accessLevel(AccessLevel.PUBLIC)
                .build()).getId();

        // Quyền đọc không phải thứ đang kiểm; mọi chương đều mở, để phần được
        // kiểm thật sự là phiên bản nội dung.
        when(chapterAccessService.decide(any(Chapter.class)))
                .thenReturn(ChapterAccessDecision.ALLOWED_FREE);
        when(chapterAccessService.decide(any(Chapter.class), anyBoolean()))
                .thenReturn(ChapterAccessDecision.ALLOWED_FREE);

        flushAndClear();
    }

    // ==================== Kịch bản bắt buộc ====================

    /**
     * Đúng hai mươi bước của đề bài, rút lại còn phần mà cơ sở dữ liệu quyết định.
     */
    @Test
    @DisplayName("Admin sửa chương giữa lúc TTS đang dựng → bản dựng xong KHÔNG thành audio hiện tại")
    void kichBanBatBuoc() {
        // 1–2. Chương ở phiên bản 1, chưa có audio nào.
        assertThat(phienBanChuong()).isEqualTo(1);
        assertThat(audioHienTai()).isEmpty();

        // 3–5. Người đọc yêu cầu TTS: một hàng PROCESSING đóng dấu phiên bản 1.
        Long audioId = xepHangDung(1);
        assertThat(audioHienTai()).hasSize(1);   // "đang dựng" vẫn hiện ra cho trang đọc

        // 6–7. Admin sửa nội dung. Chương lên phiên bản 2.
        chapterService.update(chapterId, suaNoiDung(NOI_DUNG_MOI));
        flushAndClear();
        assertThat(phienBanChuong()).isEqualTo(2);

        // Bản đang dựng đã thành lỗi thời ngay tại lúc Admin bấm lưu.
        assertThat(trangThai(audioId)).isEqualTo(AudioStatus.STALE);

        // 8–10. Lượt dựng phiên bản 1 về đích, và tự so phiên bản một lần nữa.
        TtsGenerationRecords.Outcome ketCuc =
                records.markReady(audioId, 1, ketQuaDung(), "ban-cua-phien-ban-1.mp3");

        // 11–12. Không có đường nào để nó thành audio hiện tại.
        assertThat(ketCuc).isEqualTo(TtsGenerationRecords.Outcome.STALE);
        flushAndClear();
        assertThat(trangThai(audioId)).isEqualTo(AudioStatus.STALE);

        // 13–14. Người đọc hỏi audio hiện tại: không được nhận bản phiên bản 1.
        assertThat(audioHienTai()).isEmpty();

        // 16–18. Dựng lại cho phiên bản 2, lần này không ai sửa gì.
        Long audioMoi = xepHangDung(2);
        assertThat(records.markReady(audioMoi, 2, ketQuaDung(), "ban-cua-phien-ban-2.mp3"))
                .isEqualTo(TtsGenerationRecords.Outcome.READY);
        flushAndClear();

        // 19–20. Chương v2 + audio v2 là tổ hợp hợp lệ duy nhất.
        List<AudioFile> hienTai = audioHienTai();
        assertThat(hienTai).hasSize(1);
        assertThat(hienTai.get(0).getId()).isEqualTo(audioMoi);
        assertThat(hienTai.get(0).getContentVersion()).isEqualTo(2);
        assertThat(hienTai.get(0).getStatus()).isEqualTo(AudioStatus.READY);
    }

    /**
     * Chương đi qua ba phiên bản trong lúc ba lượt dựng cùng chạy, và lượt về
     * đích <i>sau cùng</i> là lượt của phiên bản cũ nhất.
     *
     * <p>Đây là chỗ dễ sai nhất nếu ai đó cài đặt bằng "ai xong sau thì thắng":
     * cách ấy vượt qua mọi bài test có đúng một lượt dựng, rồi hỏng đúng vào lúc
     * Admin sửa liên tiếp. Thứ quyết định phải là phiên bản hiện tại của chương,
     * không phải thứ tự hoàn thành.
     */
    @Test
    @DisplayName("Sửa liên tiếp v1→v2→v3: mọi lượt dựng cũ đều thua, kể cả lượt về đích cuối cùng")
    void suaLienTiepThiChiPhienBanMoiNhatThang() {
        Long dungV1 = xepHangDung(1);

        chapterService.update(chapterId, suaNoiDung("Bản sửa lần một."));
        flushAndClear();
        Long dungV2 = xepHangDung(2);

        chapterService.update(chapterId, suaNoiDung("Bản sửa lần hai."));
        flushAndClear();
        Long dungV3 = xepHangDung(3);

        assertThat(phienBanChuong()).isEqualTo(3);

        // Về đích theo thứ tự ngược: v3 trước, v1 sau cùng.
        assertThat(records.markReady(dungV3, 3, ketQuaDung(), "v3.mp3"))
                .isEqualTo(TtsGenerationRecords.Outcome.READY);
        assertThat(records.markReady(dungV2, 2, ketQuaDung(), "v2.mp3"))
                .isEqualTo(TtsGenerationRecords.Outcome.STALE);
        assertThat(records.markReady(dungV1, 1, ketQuaDung(), "v1.mp3"))
                .isEqualTo(TtsGenerationRecords.Outcome.STALE);
        flushAndClear();

        // Lượt về sau cùng không giành được chỗ của bản hợp lệ.
        List<AudioFile> hienTai = audioHienTai();
        assertThat(hienTai).hasSize(1);
        assertThat(hienTai.get(0).getId()).isEqualTo(dungV3);
    }

    // ==================== Tăng phiên bản ====================

    @Test
    @DisplayName("Sửa nội dung thì phiên bản tăng")
    void suaNoiDungThiTangPhienBan() {
        chapterService.update(chapterId, suaNoiDung(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(phienBanChuong()).isEqualTo(2);
        assertThat(chapterRepository.findById(chapterId).orElseThrow().getContent())
                .isEqualTo(NOI_DUNG_MOI);
    }

    /**
     * Tiêu đề và mức khóa không nằm trong những chữ đem đi đọc, nên bản audio
     * đang có vẫn đọc đúng chương. Tăng phiên bản ở đây chỉ có tác dụng vứt đi
     * một bản còn tốt và bắt hệ thống trả tiền dựng lại nó.
     */
    @Test
    @DisplayName("Sửa mỗi tiêu đề hoặc mức khóa thì KHÔNG tăng phiên bản, và audio vẫn dùng được")
    void suaThuKhacThiGiuNguyenPhienBan() {
        Long audioId = xepHangDung(1);
        records.markReady(audioId, 1, ketQuaDung(), "ban-tot.mp3");
        flushAndClear();

        chapterService.update(chapterId, new ChapterRequest(
                "Tiêu đề hoàn toàn mới", NOI_DUNG_GOC, 1, AccessLevel.VIP));
        flushAndClear();

        assertThat(phienBanChuong()).isEqualTo(1);
        assertThat(trangThai(audioId)).isEqualTo(AudioStatus.READY);
        assertThat(audioHienTai()).hasSize(1);
    }

    // ==================== Đánh dấu lỗi thời ====================

    @Test
    @DisplayName("Sửa nội dung đánh dấu lỗi thời cả bản READY lẫn bản đang dựng, không đụng bản FAILED")
    void suaNoiDungDanhDauMoiBanCu() {
        Long banSan = xepHangDung(1);
        records.markReady(banSan, 1, ketQuaDung(), "san-sang.mp3");
        Long banDangDung = xepHangDung(1);
        Long banHong = xepHangDung(1);
        records.markFailed(banHong, "nhà cung cấp lỗi");
        flushAndClear();

        chapterService.update(chapterId, suaNoiDung(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(trangThai(banSan)).isEqualTo(AudioStatus.STALE);
        assertThat(trangThai(banDangDung)).isEqualTo(AudioStatus.STALE);
        // FAILED không có gì để lỗi thời — nó vốn đã không phục vụ ai.
        assertThat(trangThai(banHong)).isEqualTo(AudioStatus.FAILED);
    }

    /**
     * Bản thu của Admin cũng đọc chính những chữ ấy, nên sửa chữ thì nó cũng
     * lệch. Không có gì ở một giọng người làm nó miễn nhiễm với việc này.
     */
    @Test
    @DisplayName("Bản Admin tự thu cũng lỗi thời khi nội dung đổi")
    void banThuCungLoiThoi() {
        Long banThu = audioFileRepository.save(AudioFile.builder()
                .chapter(chapterRepository.findById(chapterId).orElseThrow())
                .source(AudioSource.UPLOAD)
                .status(AudioStatus.READY)
                .contentVersion(1)
                .filePath("admin-thu.mp3")
                .contentType("audio/mpeg")
                .build()).getId();
        flushAndClear();
        assertThat(audioHienTai()).hasSize(1);

        chapterService.update(chapterId, suaNoiDung(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(trangThai(banThu)).isEqualTo(AudioStatus.STALE);
        assertThat(audioHienTai()).isEmpty();
    }

    /**
     * Bản dựng từ trước khi có cột phiên bản: không biết nó đọc theo nội dung
     * nào, nên không bao giờ được coi là bản hiện tại.
     */
    @Test
    @DisplayName("Bản không rõ phiên bản không bao giờ là audio hiện tại")
    void banKhongRoPhienBanThiKhongDuocPhucVu() {
        audioFileRepository.save(AudioFile.builder()
                .chapter(chapterRepository.findById(chapterId).orElseThrow())
                .source(AudioSource.TTS)
                .status(AudioStatus.READY)
                .contentVersion(null)
                .filePath("di-san.mp3")
                .contentType("audio/mpeg")
                .build());
        flushAndClear();

        assertThat(audioHienTai()).isEmpty();
    }

    // ==================== Dọn theo hạn lưu giữ ====================

    @Test
    @DisplayName("Bản lỗi thời chỉ đến hạn dọn sau khi đã quá hạn lưu giữ")
    void banLoiThoiChoDenHanMoiDon() {
        Long audioId = xepHangDung(1);
        records.markReady(audioId, 1, ketQuaDung(), "sap-cu.mp3");
        chapterService.update(chapterId, suaNoiDung(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(trangThai(audioId)).isEqualTo(AudioStatus.STALE);

        // Vừa mới lỗi thời: chưa được đụng tới, vì rất có thể đang có người nghe dở.
        assertThat(audioFileRepository.findStaleUpdatedBefore(
                java.time.Instant.now().minus(java.time.Duration.ofHours(72)))).isEmpty();

        // Quá hạn thì mới vào tầm dọn.
        assertThat(audioFileRepository.findStaleUpdatedBefore(
                java.time.Instant.now().plus(java.time.Duration.ofHours(1))))
                .extracting(AudioFile::getId)
                .containsExactly(audioId);
    }

    // ==================== Trợ giúp ====================

    /** Một hàng PROCESSING đóng dấu phiên bản, như {@code TtsService} vẫn tạo. */
    private Long xepHangDung(int phienBan) {
        Long id = audioFileRepository.save(AudioFile.builder()
                .chapter(chapterRepository.findById(chapterId).orElseThrow())
                .source(AudioSource.TTS)
                .status(AudioStatus.PROCESSING)
                .contentVersion(phienBan)
                .voice("el:giong-mot")
                .speed(0)
                .contentType("audio/mpeg")
                .build()).getId();
        flushAndClear();
        return id;
    }

    private static SynthesisResult ketQuaDung() {
        return new SynthesisResult(new byte[] {1, 2, 3}, List.of(), "elevenlabs", "ElevenLabs");
    }

    private static ChapterRequest suaNoiDung(String noiDung) {
        return new ChapterRequest("Chương 1", noiDung, 1, AccessLevel.PUBLIC);
    }

    /** Đúng câu hỏi mà trang đọc hỏi: "audio hiện tại của chương này là gì". */
    private List<AudioFile> audioHienTai() {
        return audioFileRepository.findCurrentForChapter(chapterId, null);
    }

    private int phienBanChuong() {
        return chapterRepository.findById(chapterId).orElseThrow().getContentVersion();
    }

    private AudioStatus trangThai(Long audioId) {
        return Optional.of(audioFileRepository.findById(audioId).orElseThrow())
                .map(AudioFile::getStatus)
                .orElseThrow();
    }

    /** Buộc mọi thứ xuống cơ sở dữ liệu rồi đọc lại — nếu không sẽ chỉ kiểm bộ nhớ đệm. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
