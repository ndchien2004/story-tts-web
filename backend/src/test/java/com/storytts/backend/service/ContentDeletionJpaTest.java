package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Favorite;
import com.storytts.backend.domain.RatingComment;
import com.storytts.backend.domain.ReadingProgress;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.FavoriteRepository;
import com.storytts.backend.repository.RatingCommentRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xóa chương và xóa truyện, trên một cơ sở dữ liệu thật.
 *
 * <h3>Lỗi được ghim ở đây</h3>
 * Admin bấm "Xóa chương" trên một chương đã có người đọc thì nhận về "Đã có lỗi
 * xảy ra ở máy chủ". Nguyên nhân là một khóa ngoại không khai báo hành vi xóa
 * nào, nên mặc định của cơ sở dữ liệu là chặn. Cùng hình dạng ấy còn nằm ở hai
 * chỗ nữa và cả hai đều chặn việc xóa cả một truyện — nên mỗi lần sửa một cái
 * thì cái tiếp theo mới lộ ra, và đó là lý do bài kiểm này dựng sẵn <i>cả ba</i>
 * loại dữ liệu phụ thuộc chứ không phải một.
 *
 * <p>Chạy trên cơ sở dữ liệu thật vì thứ đang được kiểm chính là hành vi của cơ
 * sở dữ liệu. Lược đồ ở đây do entity sinh ra, nên bài kiểm chỉ có nghĩa khi quy
 * tắc xóa được khai báo trên entity chứ không chỉ trong migration — xem
 * {@code @OnDelete} ở {@link ReadingProgress}.
 */
@DataJpaTest
@Import({ChapterService.class, StoryService.class, PublicationService.class,
        ChapterAccessService.class, AccessControlService.class})
class ContentDeletionJpaTest {

    @Autowired
    private ChapterService chapterService;
    @Autowired
    private StoryService storyService;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReadingProgressRepository progressRepository;
    @Autowired
    private FavoriteRepository favoriteRepository;
    @Autowired
    private RatingCommentRepository ratingCommentRepository;
    @Autowired
    private AudioFileRepository audioFileRepository;
    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private CurrentUserService currentUserService;
    /**
     * Việc dọn file được kiểm riêng ở {@code StoredAudioCleanupTest}: nó chỉ chạy
     * sau khi giao dịch commit, mà @DataJpaTest thì luôn cuộn ngược. Ở đây chỉ
     * cần biết đường xóa có gọi tới nó hay không.
     */
    @MockitoBean
    private StoredAudioCleanup storedAudioCleanup;

    // Những bean còn lại của StoryService: bài kiểm này chỉ nói về việc xóa.
    @MockitoBean
    private GenreService genreService;
    @MockitoBean
    private AuthorService authorService;
    @MockitoBean
    private RatingCommentService ratingCommentService;
    @MockitoBean
    private FavoriteService favoriteService;
    @MockitoBean
    private ReadingProgressService readingProgressService;
    @MockitoBean
    private com.storytts.backend.repository.ViewEventRepository viewEventRepository;

    private Story story;
    private Chapter chapter;
    private User reader;

    @BeforeEach
    void setUp() {
        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        reader = userRepository.save(User.builder()
                .username("nguoidoc").email("doc@test.local").passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build());

        story = storyRepository.save(Story.builder()
                .title("Truyện thử")
                .publishedAt(Instant.now().minusSeconds(60))
                .build());

        chapter = chapterRepository.save(Chapter.builder()
                .story(story)
                .title("Chương 1")
                .content("Nội dung.")
                .chapterNumber(1)
                .accessLevel(AccessLevel.PUBLIC)
                .publishedAt(Instant.now().minusSeconds(60))
                .build());
    }

    /* ------------------------------------------------------------------ */
    /* Lỗi được báo                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("xóa được chương mà người ta đang đọc dở")
    void xoaDuocChuongDaCoNguoiDoc() {
        progressRepository.save(ReadingProgress.builder()
                .user(reader).chapter(chapter).audioPositionSeconds(42).build());
        flushAndClear();

        // Trước lần sửa này, đúng dòng dưới đây ném ConstraintViolationException
        // và Admin nhận về một câu "lỗi máy chủ" không nói gì.
        assertThatCode(() -> chapterService.delete(chapter.getId())).doesNotThrowAnyException();
        flushAndClear();

        assertThat(chapterRepository.findById(chapter.getId())).isEmpty();
        // Tiến độ đọc của một chương không còn tồn tại thì cũng không còn nghĩa.
        assertThat(progressRepository.count()).isZero();
    }

    @Test
    @DisplayName("xóa được truyện đã có người yêu thích và bình luận")
    void xoaDuocTruyenDaCoTuongTac() {
        favoriteRepository.save(Favorite.builder().user(reader).story(story).build());
        ratingCommentRepository.save(RatingComment.builder()
                .user(reader).story(story).rating(5).comment("Hay").build());
        progressRepository.save(ReadingProgress.builder()
                .user(reader).chapter(chapter).audioPositionSeconds(7).build());
        flushAndClear();

        // Ba khóa ngoại khác nhau cùng chặn đường này, nên sửa một cái chỉ làm
        // cái tiếp theo lộ ra. Bài kiểm dựng cả ba để không ai sửa được nửa vời.
        assertThatCode(() -> storyService.delete(story.getId())).doesNotThrowAnyException();
        flushAndClear();

        assertThat(storyRepository.findById(story.getId())).isEmpty();
        assertThat(chapterRepository.count()).isZero();
        assertThat(favoriteRepository.count()).isZero();
        assertThat(ratingCommentRepository.count()).isZero();
        assertThat(progressRepository.count()).isZero();
    }
    /* ------------------------------------------------------------------ */
    /* File audio trên nơi lưu                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("xóa chương thì có yêu cầu dọn file audio của nó")
    void xoaChuongThiYeuCauDonFile() {
        audioFileRepository.save(audio("ban-thu.mp3"));
        flushAndClear();

        chapterService.delete(chapter.getId());

        // Cơ sở dữ liệu tự dọn hàng, nhưng file MP3 nằm ngoài nó — với bản dựng
        // bằng ElevenLabs thì mỗi file bỏ quên là một khoản tiền đã trả nằm lại
        // trên Cloudinary. Phần dọn thật nằm ở StoredAudioCleanupTest.
        verify(storedAudioCleanup).purgeChapterAfterCommit(chapter.getId());
    }

    @Test
    @DisplayName("xóa truyện thì dọn theo cả truyện, không lặp qua từng chương")
    void xoaTruyenThiDonTheoCaTruyen() {
        storyService.delete(story.getId());

        // Một truyện nghìn chương mà hỏi từng chương là nghìn câu truy vấn cho
        // một thao tác vốn chỉ cần một.
        verify(storedAudioCleanup).purgeStoryAfterCommit(story.getId());
        verify(storedAudioCleanup, never()).purgeChapterAfterCommit(anyLong());
    }

    /**
     * Đẩy mọi thay đổi xuống cơ sở dữ liệu rồi bỏ bối cảnh nhớ đệm.
     *
     * <p>Cần thiết vì @DataJpaTest gói cả bài kiểm trong một bối cảnh duy nhất:
     * không dọn thì thực thể dựng sẵn ở trên vẫn giữ tham chiếu tới chương vừa bị
     * xóa, và Hibernate báo lỗi về một quan hệ mà lúc chạy thật không tồn tại —
     * ở đó lệnh xóa là một giao dịch riêng, không ai còn cầm bản ghi cũ.
     */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private AudioFile audio(String path) {
        return AudioFile.builder()
                .chapter(chapter)
                .source(AudioSource.UPLOAD)
                .status(AudioStatus.READY)
                .voice("el:mot")
                .speed(0)
                .contentVersion(1)
                .filePath(path)
                .build();
    }
}
