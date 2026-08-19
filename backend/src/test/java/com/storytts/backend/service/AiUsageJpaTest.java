package com.storytts.backend.service;

import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.repository.AiUsageRepository;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sổ đếm lượt dùng AI trên một cơ sở dữ liệu thật.
 *
 * <h3>Bài quan trọng nhất ở đây</h3>
 * {@link #dongSoSongSotQuaViecDonBanAudio()}. Trước lần sửa này, hạn mức "Nghe
 * bằng AI" là một phép đếm trên chính bảng {@code audio_files} — và bản audio
 * người đọc tự dựng thì bị dọn sạch mỗi lần họ mở phiên đăng nhập mới. Nghĩa là
 * đăng xuất rồi đăng nhập lại là nạp đầy hạn mức, lặp bao nhiêu lần cũng được,
 * và toàn bộ hàng rào chi phí ElevenLabs coi như không có.
 *
 * <p>Bài ấy chạy đúng kịch bản đó: dùng hết lượt, xóa bản audio như lúc dọn
 * phiên, rồi hỏi lại xem còn mấy lượt. Câu trả lời phải là 0.
 *
 * <p>Chạy trên cơ sở dữ liệu thật chứ không trên mock, vì hai thứ đang được kiểm
 * đều thuộc về cơ sở dữ liệu: khóa ngoại {@code ON DELETE SET NULL} có thật sự
 * để dòng sổ ở lại không, và phép xếp chỗ theo khóa chính tự tăng có ra đúng thứ
 * tự không.
 */
@DataJpaTest
@Import(AiUsageService.class)
class AiUsageJpaTest {

    private static final int PERSONAL_LIMIT = 3;
    private static final int GLOBAL_LIMIT = 100;

    @Autowired
    private AiUsageService aiUsageService;
    @Autowired
    private AiUsageRepository aiUsageRepository;
    @Autowired
    private AudioFileRepository audioFileRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Long userId;
    private Long chapterId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .username("nguoidoc")
                .email("doc@test.local")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .enabled(true)
                .build()).getId();

        Story story = storyRepository.save(Story.builder().title("Truyện thử").build());
        chapterId = chapterRepository.save(Chapter.builder()
                .story(story)
                .title("Chương 1")
                .chapterNumber(1)
                .content("Nội dung.")
                .build()).getId();
    }

    /* ------------------------------------------------------------------ */
    /* Lỗi được sửa                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("dọn bản audio của một phiên không làm hạn mức mọc lại")
    void dongSoSongSotQuaViecDonBanAudio() {
        List<Long> audioIds = List.of(reserveWithAudio(), reserveWithAudio(), reserveWithAudio());
        assertThat(remaining()).isZero();

        // Đúng những gì ReaderNarrationCleanup làm khi người đọc mở phiên mới.
        audioFileRepository.deleteAllById(audioIds);
        entityManager.flush();
        entityManager.clear();

        // Trước lần sửa này, con số ở đây quay về 3 và người ta tạo được thêm ba
        // bản nữa — rồi lại đăng xuất, rồi lại ba bản nữa, không có điểm dừng.
        assertThat(remaining()).isZero();
        assertThatThrownBy(this::reserve).hasMessageContaining("USER");
    }

    @Test
    @DisplayName("dòng sổ vẫn còn sau khi bản audio nó trả tiền cho đã bị xóa")
    void dongSoKhongBiKeoDiTheoKhoaNgoai() {
        Long audioId = reserveWithAudio();

        audioFileRepository.deleteById(audioId);
        entityManager.flush();
        entityManager.clear();

        assertThat(aiUsageRepository.countForUser(
                userId, AiUsageKind.TTS, AiUsageService.startOfToday())).isEqualTo(1);
    }

    /* ------------------------------------------------------------------ */
    /* Chiếm chỗ và hoàn lượt                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("hết phần thì lượt vừa chiếm được hoàn ngay, không giữ chỗ của ai")
    void luotBiTuChoiKhongGiuCho() {
        reserve();
        reserve();
        reserve();

        assertThatThrownBy(this::reserve).hasMessageContaining("USER");

        // Bốn dòng đã ghi, nhưng chỉ ba dòng còn tính — nếu không thì mỗi lần
        // bấm hụt lại đẩy người ta lún thêm xuống dưới hạn mức thật.
        assertThat(aiUsageRepository.count()).isEqualTo(4);
        assertThat(aiUsageRepository.countForUser(
                userId, AiUsageKind.TTS, AiUsageService.startOfToday())).isEqualTo(3);
    }

    @Test
    @DisplayName("hoàn lượt cho một bản dựng hỏng thì lượt ấy dùng lại được")
    void hoanLuotChoBanHong() {
        Long audioId = reserveWithAudio();
        reserve();
        reserve();
        assertThat(remaining()).isZero();

        aiUsageService.refundForAudio(audioId, "dung audio that bai");

        // Người đọc không mất lượt vì lỗi của nhà cung cấp.
        assertThat(remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("hoàn hai lần không cộng thêm lượt nào")
    void hoanHaiLanKhongCongThem() {
        Long audioId = reserveWithAudio();

        aiUsageService.refundForAudio(audioId, "lần một");
        aiUsageService.refundForAudio(audioId, "lần hai");

        assertThat(remaining()).isEqualTo(PERSONAL_LIMIT);
    }

    @Test
    @DisplayName("hoàn lượt là ghi thêm một mốc, không phải xóa dòng")
    void hoanLuotKhongXoaDong() {
        Long audioId = reserveWithAudio();
        aiUsageService.refundForAudio(audioId, "dung audio that bai");

        // Sổ chi phí phải đọc được cả phần đã tiêu lẫn phần đã hỏng; xóa dòng đi
        // là mất luôn câu trả lời cho "tháng này hỏng bao nhiêu lượt".
        assertThat(aiUsageRepository.count()).isEqualTo(1);
        assertThat(aiUsageRepository.findAll().getFirst().getRefundedAt()).isNotNull();
    }

    /* ------------------------------------------------------------------ */
    /* Hai hàng rào, một bảng                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("lượt hỏi trợ lý không ăn vào hạn mức tạo audio")
    void haiLoaiKhongLanVaoNhau() {
        aiUsageService.reserve(userId, AiUsageKind.ASSISTANT, null, 10, GLOBAL_LIMIT, denial());
        aiUsageService.reserve(userId, AiUsageKind.ASSISTANT, null, 10, GLOBAL_LIMIT, denial());

        assertThat(remaining()).isEqualTo(PERSONAL_LIMIT);
    }

    @Test
    @DisplayName("trần chung xét trước hạn mức cá nhân")
    void tranChungXetTruoc() {
        Long other = userRepository.save(User.builder()
                .username("nguoikhac").email("khac@test.local").passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build()).getId();

        aiUsageService.reserve(other, AiUsageKind.TTS, chapterId, 10, 1, denial());

        // Người này chưa dùng lượt nào, nên "bạn hết lượt" sẽ là câu trả lời sai.
        assertThatThrownBy(() ->
                aiUsageService.reserve(userId, AiUsageKind.TTS, chapterId,
                        PERSONAL_LIMIT, 1, denial()))
                .hasMessageContaining("GLOBAL");
    }

    /* ------------------------------------------------------------------ */
    /* Dữ liệu dựng sẵn                                                    */
    /* ------------------------------------------------------------------ */

    private Long reserve() {
        return aiUsageService.reserve(userId, AiUsageKind.TTS, chapterId,
                PERSONAL_LIMIT, GLOBAL_LIMIT, denial());
    }

    /** Một lượt đã chiếm, kèm bản audio mà nó trả tiền để dựng. */
    private Long reserveWithAudio() {
        Long usageId = reserve();
        Long audioId = audioFileRepository.save(AudioFile.builder()
                .chapter(entityManager.getEntityManager().getReference(Chapter.class, chapterId))
                .source(AudioSource.TTS)
                .status(AudioStatus.READY)
                .voice("el:mot")
                .speed(0)
                .contentVersion(1)
                .requestedBy(entityManager.getEntityManager().getReference(User.class, userId))
                .build()).getId();
        aiUsageService.linkToAudio(usageId, audioId);
        return audioId;
    }

    private Integer remaining() {
        return aiUsageService.remaining(userId, AiUsageKind.TTS, PERSONAL_LIMIT, GLOBAL_LIMIT);
    }

    /** Ngoại lệ trần trụi: bài kiểm này quan tâm tới phía nào chạm trần, không tới lời văn. */
    private static AiUsageService.Denial denial() {
        return (scope, limit) -> new IllegalStateException(scope + " limit=" + limit);
    }
}
