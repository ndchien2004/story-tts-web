package com.storytts.backend.service;

import com.storytts.backend.domain.RatingComment;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.interaction.CommentRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.RatingCommentRepository;
import com.storytts.backend.repository.StoryRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm quy tắc "mỗi người một điểm cho mỗi truyện" (mục 4.6).
 *
 * <p>Bảng {@code ratings_comments} giữ cả bình luận lẫn điểm trên cùng một dòng,
 * nên quy tắc này <b>không có ràng buộc unique nào đỡ ở tầng cơ sở dữ liệu</b>:
 * người đọc vẫn được bình luận nhiều lần, chỉ điểm là một lần, mà MySQL không có
 * unique index theo điều kiện. Những bài dưới đây là chốt duy nhất giữ nó — bỏ
 * chúng đi thì một lần sửa vô tình sẽ đưa điểm trung bình về chỗ cũ, nơi một
 * người bấm mười lần là dời được điểm của cả truyện.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingCommentServiceTest {

    private static final Long STORY_ID = 3L;
    private static final Long USER_ID = 42L;

    @Mock
    private RatingCommentRepository ratingCommentRepository;
    @Mock
    private StoryRepository storyRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private RatingCommentService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).username("nguoi-doc").passwordHash("x").build();

        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(storyRepository.findById(STORY_ID))
                .thenReturn(Optional.of(Story.builder().id(STORY_ID).title("Truyện thử").build()));
        when(ratingCommentRepository.findFirstByStoryIdAndUserIdAndRatingIsNotNull(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(ratingCommentRepository.save(any(RatingComment.class)))
                .thenAnswer(invocation -> {
                    RatingComment saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(101L);
                    return saved;
                });
    }

    @Test
    @DisplayName("Lần chấm đầu tiên: một dòng mới mang cả điểm lẫn bình luận")
    void lanDauTaoDongMoi() {
        service.create(STORY_ID, new CommentRequest(4, "Hay đấy."));

        RatingComment saved = captureSaved();
        assertThat(saved.getRating()).isEqualTo(4);
        assertThat(saved.getComment()).isEqualTo("Hay đấy.");
    }

    @Test
    @DisplayName("Chấm lại: SỬA điểm cũ, không cộng thêm một lượt vào điểm trung bình")
    void chamLaiThiSuaDiemCu() {
        RatingComment existing = rated(2);
        when(ratingCommentRepository.findFirstByStoryIdAndUserIdAndRatingIsNotNull(STORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        service.create(STORY_ID, new CommentRequest(5, null));

        assertThat(existing.getRating()).isEqualTo(5);
        verify(ratingCommentRepository).save(existing);
        // Đây là điểm mấu chốt: không có dòng nào được thêm, nên số lượt chấm của
        // truyện không tăng và điểm trung bình vẫn là bình quân trên số người.
        assertThat(savedRows()).hasSize(1);
    }

    @Test
    @DisplayName("Vừa chấm lại vừa bình luận: điểm về dòng cũ, bình luận thành dòng mới không mang điểm")
    void chamLaiKemBinhLuan() {
        RatingComment existing = rated(2);
        when(ratingCommentRepository.findFirstByStoryIdAndUserIdAndRatingIsNotNull(STORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        service.create(STORY_ID, new CommentRequest(5, "Đọc lại vẫn hay."));

        assertThat(existing.getRating()).isEqualTo(5);

        RatingComment fresh = savedRows().stream()
                .filter(row -> row != existing)
                .findFirst()
                .orElseThrow();
        // Bình luận vẫn là một dòng riêng để giữ thứ tự cuộc trò chuyện, nhưng nó
        // không được mang điểm — nếu mang thì người này lại có hai lượt chấm.
        assertThat(fresh.getRating()).isNull();
        assertThat(fresh.getComment()).isEqualTo("Đọc lại vẫn hay.");
    }

    @Test
    @DisplayName("Bình luận không kèm điểm: không đụng tới dòng đã chấm")
    void binhLuanKhongDungVaoDiem() {
        service.create(STORY_ID, new CommentRequest(null, "Chờ chương mới."));

        // Không có điểm trong yêu cầu thì cũng không cần đi tìm dòng đã chấm.
        verify(ratingCommentRepository, never())
                .findFirstByStoryIdAndUserIdAndRatingIsNotNull(anyLong(), anyLong());
        assertThat(captureSaved().getRating()).isNull();
    }

    @Test
    @DisplayName("Gửi rỗng: không chấm cũng không viết gì thì bị từ chối")
    void guiRongThiTuChoi() {
        assertThatThrownBy(() -> service.create(STORY_ID, new CommentRequest(null, "   ")))
                .isInstanceOf(BadRequestException.class);

        verify(ratingCommentRepository, never()).save(any());
    }

    private RatingComment rated(int rating) {
        return RatingComment.builder()
                .id(55L)
                .user(user)
                .story(Story.builder().id(STORY_ID).build())
                .rating(rating)
                .build();
    }

    private RatingComment captureSaved() {
        return savedRows().getLast();
    }

    private java.util.List<RatingComment> savedRows() {
        ArgumentCaptor<RatingComment> captor = ArgumentCaptor.forClass(RatingComment.class);
        verify(ratingCommentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }
}
