package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Bản nháp và hẹn giờ đăng, trên một cơ sở dữ liệu thật.
 *
 * <h3>Vì sao chạy trên cơ sở dữ liệu chứ không trên mock</h3>
 * Thứ đang được kiểm là một mệnh đề {@code WHERE} — "chỉ những chương có
 * {@code published_at <= now()}". Đó chính là chỗ mà "đến giờ thì tự đăng" được
 * cài đặt: không có tác vụ nền nào đổi trạng thái, chỉ có một phép so sánh chạy
 * lúc có người đọc. Mock hóa chỗ ấy là mock hóa đúng cái cần kiểm.
 */
@DataJpaTest
@Import({ChapterService.class, PublicationService.class, ChapterAccessService.class,
        AccessControlService.class})
class PublicationJpaTest {

    @Autowired
    private ChapterService chapterService;
    @Autowired
    private PublicationService publicationService;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;

    /** Không dùng ở đây; đường xóa được kiểm riêng ở ContentDeletionJpaTest. */
    @MockitoBean
    private StoredAudioCleanup storedAudioCleanup;
    @MockitoBean
    private ChapterRefundService chapterRefundService;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Story story;
    private Long publishedChapterId;
    private Long draftChapterId;
    private Long scheduledChapterId;

    @BeforeEach
    void setUp() {
        asReader();

        story = storyRepository.save(Story.builder()
                .title("Truyện thử")
                .publishedAt(past())
                .build());

        publishedChapterId = saveChapter(1, "Chương đã đăng", past());
        draftChapterId = saveChapter(2, "Chương nháp", null);
        scheduledChapterId = saveChapter(3, "Chương hẹn giờ", future());
    }

    /* ------------------------------------------------------------------ */
    /* Người đọc thường                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("người đọc chỉ thấy chương đã tới giờ")
    void nguoiDocChiThayChuongDaDang() {
        PageResponse<ChapterSummaryDto> page = list();

        assertThat(page.content()).extracting(ChapterSummaryDto::id)
                .containsExactly(publishedChapterId);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("mở thẳng một chương nháp thì nhận 404, không phải 403")
    void chuongNhapTraVe404() {
        // 403 là một lời xác nhận rằng chương ấy có tồn tại — mà với bản nháp
        // thì chính sự tồn tại của nó mới là thứ chưa được công bố.
        assertThatThrownBy(() -> chapterService.findDetailEntity(draftChapterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("chương hẹn giờ cũng chưa mở được, dù nó đã có mốc")
    void chuongHenGioChuaMoDuoc() {
        assertThatThrownBy(() -> chapterService.findDetailEntity(scheduledChapterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Không có tác vụ nền nào chạy giữa hai câu lệnh dưới đây. Chương xuất hiện
     * vì mốc của nó đã lùi về quá khứ, và câu truy vấn hỏi lại điều đó ở mỗi lần
     * có người đọc.
     */
    @Test
    @DisplayName("tới giờ thì chương tự hiện, không cần ai bấm gì")
    void toiGioThiTuHien() {
        assertThat(list().content()).hasSize(1);

        Chapter scheduled = chapterRepository.findById(scheduledChapterId).orElseThrow();
        scheduled.setPublishedAt(past());
        chapterRepository.saveAndFlush(scheduled);

        assertThat(list().content()).extracting(ChapterSummaryDto::id)
                .containsExactly(publishedChapterId, scheduledChapterId);
    }

    @Test
    @DisplayName("truyện chưa đăng thì giấu luôn cả chương đã đăng của nó")
    void truyenNhapGiauCaChuongDaDang() {
        story.setPublishedAt(null);
        storyRepository.saveAndFlush(story);

        // Một chương lẻ không mở được nếu trang truyện dẫn tới nó còn là bản
        // nháp; để hở chỗ này thì việc giấu cả truyện chỉ còn là giấu mục lục.
        assertThatThrownBy(() -> chapterService.findDetailEntity(publishedChapterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("nút chương sau bỏ qua chương chưa đăng")
    void chuongSauBoQuaBanNhap() {
        Long fourth = saveChapter(4, "Chương 4 đã đăng", past());

        Optional<Chapter> next = chapterRepository.findNext(
                story.getId(), 1, false, Instant.now());

        // Chương 2 là nháp, chương 3 hẹn giờ. Trỏ vào một trong hai là đẩy người
        // đang nghe liên tục thẳng vào trang 404.
        assertThat(next).get().extracting(Chapter::getId).isEqualTo(fourth);
    }

    /* ------------------------------------------------------------------ */
    /* Quản trị viên                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("quản trị viên thấy cả ba trạng thái, kèm nhãn của từng cái")
    void quanTriVienThayTatCa() {
        asAdmin();

        assertThat(list().content())
                .extracting(ChapterSummaryDto::publishState)
                .containsExactly("PUBLISHED", "DRAFT", "SCHEDULED");
    }

    @Test
    @DisplayName("quản trị viên mở được bản nháp để sửa tiếp")
    void quanTriVienMoDuocBanNhap() {
        asAdmin();

        assertThat(chapterService.findDetailEntity(draftChapterId).getTitle())
                .isEqualTo("Chương nháp");
    }

    @Test
    @DisplayName("đổi trạng thái xuất bản không đụng tới nội dung hay mức khóa")
    void doiTrangThaiKhongDungToiThuKhac() {
        asAdmin();

        ChapterSummaryDto result = chapterService.changePublication(draftChapterId, false, null);

        assertThat(result.publishState()).isEqualTo("PUBLISHED");
        assertThat(result.accessLevel()).isEqualTo(AccessLevel.VIP.name());

        Chapter reloaded = chapterRepository.findById(draftChapterId).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("Nội dung chương.");
    }

    @Test
    @DisplayName("gỡ một chương xuống là đặt lại mốc về null")
    void goChuongXuong() {
        asAdmin();

        chapterService.changePublication(publishedChapterId, true, null);

        assertThat(chapterRepository.findById(publishedChapterId).orElseThrow().getPublishedAt())
                .isNull();
        assertThat(publicationService.canSeeUnpublished()).isTrue();
    }

    /* ------------------------------------------------------------------ */
    /* Dữ liệu dựng sẵn                                                    */
    /* ------------------------------------------------------------------ */

    private PageResponse<ChapterSummaryDto> list() {
        return chapterService.listByStory(story.getId(), null, true, 0, 50);
    }

    private Long saveChapter(int number, String title, Instant publishedAt) {
        return chapterRepository.saveAndFlush(Chapter.builder()
                .story(story)
                .title(title)
                .content("Nội dung chương.")
                .chapterNumber(number)
                .accessLevel(AccessLevel.VIP)
                .publishedAt(publishedAt)
                .build()).getId();
    }

    private void asReader() {
        when(currentUserService.currentPrincipal())
                .thenReturn(Optional.of(new AppUserPrincipal(user(Role.MEMBER))));
        when(currentUserService.currentUserId()).thenReturn(Optional.of(1L));
    }

    private void asAdmin() {
        when(currentUserService.currentPrincipal())
                .thenReturn(Optional.of(new AppUserPrincipal(user(Role.ADMIN))));
        when(currentUserService.currentUserId()).thenReturn(Optional.of(1L));
    }

    private static User user(Role role) {
        return User.builder().id(1L).username("ai-do").passwordHash("x")
                .role(role).enabled(true).build();
    }

    private static Instant past() {
        return Instant.now().minus(1, ChronoUnit.HOURS);
    }

    private static Instant future() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }
}
