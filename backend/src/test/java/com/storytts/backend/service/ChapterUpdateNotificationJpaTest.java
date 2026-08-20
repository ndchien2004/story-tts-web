package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.EntitlementSource;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.service.notification.NotificationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Nội dung một chương đổi, và ai được báo về việc ấy.
 *
 * <h3>Vì sao chỉ báo cho người đã mở khóa</h3>
 * Một chương công khai có thể đã đi qua hàng nghìn lượt đọc, phần lớn là người
 * đọc một lần rồi đi. Ghi một hàng vào hộp thư của từng người là biến một lần
 * sửa chính tả thành hàng nghìn lượt ghi, cho một tin mà gần hết số người nhận
 * không quan tâm. Người đã bỏ Xu — hoặc được cấp quyền — cho đúng chương ấy thì
 * khác: tập ấy nhỏ, có giới hạn tự nhiên, và họ có lý do thật để biết bản mình
 * đang giữ không còn là bản mới nhất.
 *
 * <p>Người <i>đang mở</i> chương ngay lúc này đi đường khác và nhanh hơn: một
 * dòng ngay trên trang đọc, qua luồng SSE công khai theo chương. Hai đường
 * không thay thế nhau — cái kia là lời mời tải lại ngay, cái này là một dòng
 * trong hộp thư đọc lúc nào cũng được.
 */
@DataJpaTest
@Import({ChapterService.class, PublicationService.class, NotificationService.class})
class ChapterUpdateNotificationJpaTest {

    private static final String NOI_DUNG_GOC = "Chữ của phiên bản đầu tiên.";
    private static final String NOI_DUNG_MOI = "Chữ đã được sửa lại.";

    @Autowired
    private ChapterService chapterService;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChapterEntitlementRepository entitlementRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ChapterAccessService chapterAccessService;
    @MockitoBean
    private CurrentUserService currentUserService;
    @MockitoBean
    private StoredAudioCleanup storedAudioCleanup;
    @MockitoBean
    private ChapterRefundService chapterRefundService;

    private Long chapterId;
    private User buyer;
    private User stranger;

    @BeforeEach
    void setUp() {
        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());
        when(currentUserService.currentUserId()).thenReturn(Optional.empty());

        // Quyền đọc không phải thứ đang kiểm ở đây; mọi chương đều mở, để phần
        // được kiểm thật sự là ai nhận được thông báo.
        when(chapterAccessService.decide(any(Chapter.class)))
                .thenReturn(ChapterAccessDecision.ALLOWED_FREE);
        when(chapterAccessService.decide(any(Chapter.class), anyBoolean()))
                .thenReturn(ChapterAccessDecision.ALLOWED_FREE);

        Story story = storyRepository.save(Story.builder().title("Truyện thử")
                .publishedAt(Instant.now().minusSeconds(60)).build());

        chapterId = chapterRepository.save(Chapter.builder()
                .story(story)
                .title("Chương 1")
                .content(NOI_DUNG_GOC)
                .chapterNumber(1)
                .accessLevel(AccessLevel.MEMBER)
                .coinPrice(50)
                .publishedAt(Instant.now().minusSeconds(60))
                .build()).getId();

        buyer = userRepository.save(User.builder()
                .username("nguoimua").email("mua@test.local").passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build());
        stranger = userRepository.save(User.builder()
                .username("nguoidiqua").email("qua@test.local").passwordHash("hash")
                .role(Role.MEMBER).enabled(true).build());

        flushAndClear();
    }

    @Test
    @DisplayName("sửa nội dung: người đã mở khóa được báo, người chưa mở thì không")
    void onlyUnlockedReadersAreTold() {
        unlock(buyer, EntitlementSource.COIN_PURCHASE, 50);
        flushAndClear();

        chapterService.update(chapterId, rewrite(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(notificationRepository.findAll()).singleElement().satisfies(sent -> {
            assertThat(sent.getUser().getId()).isEqualTo(buyer.getId());
            assertThat(sent.getType()).isEqualTo(NotificationType.CHAPTER_UPDATED);
            // Không có gì bị mất và không có tiền nào đổi chỗ: đây là loại tin
            // mà đặc tả gọi là "đừng làm mọi thông báo đều chói mắt".
            assertThat(sent.getPriority()).isEqualTo(NotificationPriority.INFO);
            assertThat(sent.getActionType()).isEqualTo(NotificationAction.VIEW_CHAPTER);
            assertThat(sent.getRelatedEntityType()).isEqualTo(NotificationEntityType.CHAPTER);
            assertThat(sent.getRelatedEntityId()).isEqualTo(chapterId);
            assertThat(sent.getMetadata()).contains("\"contentVersion\":2");
        });

        assertThat(notificationRepository.countUnread(stranger.getId())).isZero();
    }

    @Test
    @DisplayName("quyền do quản trị viên cấp cũng được báo — họ cũng đang giữ một bản đã cũ")
    void agrantedUnlockCountsToo() {
        unlock(buyer, EntitlementSource.ADMIN_GRANT, 0);
        flushAndClear();

        chapterService.update(chapterId, rewrite(NOI_DUNG_MOI));
        flushAndClear();

        // Khác hẳn câu hỏi của đường hoàn Xu ("ai phải được trả lại tiền"): ở
        // đây câu hỏi là "ai đang giữ một bản chương đã cũ".
        assertThat(notificationRepository.countUnread(buyer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("sửa mỗi tiêu đề thì không báo ai — những chữ đem đi đọc không đổi")
    void renamingTellsNobody() {
        unlock(buyer, EntitlementSource.COIN_PURCHASE, 50);
        flushAndClear();

        chapterService.update(chapterId, new ChapterRequest(
                "Tiêu đề hoàn toàn mới", NOI_DUNG_GOC, 1, AccessLevel.MEMBER, false, null));
        flushAndClear();

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("lưu lại đúng phiên bản ấy không sinh thông báo thứ hai")
    void thesameVersionIsAnnouncedOnce() {
        unlock(buyer, EntitlementSource.COIN_PURCHASE, 50);
        flushAndClear();

        chapterService.update(chapterId, rewrite(NOI_DUNG_MOI));
        flushAndClear();
        // Lưu lại đúng nội dung vừa lưu: phiên bản không tăng, nên khóa sự kiện
        // `chapter-updated:<chương>:<phiên bản>:<người>` không đổi.
        chapterService.update(chapterId, rewrite(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("không ai mở khóa thì lần sửa nào cũng im lặng")
    void anUnsoldChapterTellsNobody() {
        chapterService.update(chapterId, rewrite(NOI_DUNG_MOI));
        flushAndClear();

        assertThat(notificationRepository.count()).isZero();
    }

    private ChapterRequest rewrite(String content) {
        return new ChapterRequest("Chương 1", content, 1, AccessLevel.MEMBER, false, null);
    }

    private void unlock(User user, EntitlementSource source, long coinsSpent) {
        entitlementRepository.save(ChapterEntitlement.builder()
                .user(user)
                .chapter(chapterRepository.findById(chapterId).orElseThrow())
                .source(source)
                .coinsSpent(coinsSpent)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
