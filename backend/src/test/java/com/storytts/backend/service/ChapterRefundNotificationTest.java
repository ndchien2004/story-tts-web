package com.storytts.backend.service;

import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ChapterEntitlement;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.repository.ChapterEntitlementRepository;
import com.storytts.backend.service.notification.NotificationDraft;
import com.storytts.backend.service.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thứ tự giữa việc hoàn Xu và việc nói rằng đã hoàn Xu.
 *
 * <h3>Điều được ghim ở đây</h3>
 * Đặc tả cấm đúng một trạng thái, và nó là trạng thái dễ tạo ra nhất:
 *
 * <pre>
 *   chương đã xóa + hoàn tiền hỏng + thông báo nói "đã hoàn"
 * </pre>
 *
 * Ở đây điều đó được ngăn bằng hai thứ cùng lúc, và bài kiểm này nhắm vào cái
 * thứ nhất: lệnh cộng Xu chạy <b>trước</b>, và nó ném thì đường ghi thông báo
 * không bao giờ tới lượt. Cái thứ hai — cả hai nằm trong một giao dịch, nên một
 * lần cuộn ngược xóa cả hai — được kiểm ở {@code ContentDeletionJpaTest}, nơi có
 * cơ sở dữ liệu thật để cuộn ngược.
 *
 * <p>Dùng mock ở đây chứ không dùng cơ sở dữ liệu, vì thứ cần dựng là một lần
 * cộng Xu <i>hỏng</i> — một tình huống không có cách nào tạo ra bằng dữ liệu
 * hợp lệ.
 */
@ExtendWith(MockitoExtension.class)
class ChapterRefundNotificationTest {

    private static final Long READER = 7L;
    private static final Long OTHER_READER = 8L;
    private static final Long CHAPTER = 41L;
    private static final Long STORY = 3L;

    @Mock
    private ChapterEntitlementRepository entitlementRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChapterRefundService refundService;

    /* ------------------------------------------------------------------ */
    /* Hoàn hỏng                                                           */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("cộng Xu hỏng thì không có thông báo nào nói rằng đã hoàn")
    void aFailedRefundAnnouncesNothing() {
        when(entitlementRepository.findPaidByChapter(CHAPTER))
                .thenReturn(List.of(paidEntitlement(READER, 50)));
        when(walletService.credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("ví hỏng"));

        assertThatThrownBy(() -> refundService.refundChapter(CHAPTER))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationService, never()).notifyAll(any());
    }

    @Test
    @DisplayName("hoàn hỏng ở người thứ hai thì cả lượt dừng lại, không báo cho ai")
    void aPartialFailureAnnouncesNothingAtAll() {
        when(entitlementRepository.findPaidByChapter(CHAPTER))
                .thenReturn(List.of(paidEntitlement(READER, 50), paidEntitlement(OTHER_READER, 30)));
        when(walletService.credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString()))
                .thenReturn(50L)
                .thenThrow(new IllegalStateException("ví hỏng"));

        assertThatThrownBy(() -> refundService.refundChapter(CHAPTER))
                .isInstanceOf(IllegalStateException.class);

        // Người đầu đã được cộng trong bộ nhớ, nhưng giao dịch sẽ cuộn ngược —
        // nên báo cho riêng người ấy là báo về một khoản tiền sắp biến mất.
        verify(notificationService, never()).notifyAll(any());
    }

    /* ------------------------------------------------------------------ */
    /* Hoàn xong                                                           */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("gỡ một chương: mỗi người mua nhận đúng một thông báo, kèm số Xu thật")
    void oneNotificationPerBuyer() {
        when(entitlementRepository.findPaidByChapter(CHAPTER))
                .thenReturn(List.of(paidEntitlement(READER, 50), paidEntitlement(OTHER_READER, 30)));
        when(walletService.credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString()))
                .thenReturn(0L);

        ChapterRefundService.Refunds refunds = refundService.refundChapter(CHAPTER);

        assertThat(refunds.coins()).isEqualTo(80);
        assertThat(refunds.readers()).isEqualTo(2);

        List<NotificationDraft> drafts = capturedDrafts();
        assertThat(drafts).hasSize(2);

        NotificationDraft first = drafts.get(0);
        assertThat(first.userId()).isEqualTo(READER);
        // Số Xu trong câu chữ là tổng của chính những dòng vừa ghi vào sổ, không
        // phải một con số tính lại từ giá chương — giá có thể đã đổi.
        assertThat(first.metadata()).containsEntry("refundedCoins", 50L);
        assertThat(first.message()).contains("50 Xu");
        // Khóa tự nhiên: xử lý lại cùng một lần xóa không sinh dòng thứ hai.
        assertThat(first.eventId()).isEqualTo("chapter-deleted:" + CHAPTER + ":" + READER);

        assertThat(drafts.get(1).userId()).isEqualTo(OTHER_READER);
        assertThat(drafts.get(1).metadata()).containsEntry("refundedCoins", 30L);
    }

    @Test
    @DisplayName("một người mua nhiều chương của một truyện bị gỡ chỉ nhận một thông báo")
    void manyChaptersOnePersonOneNotification() {
        when(entitlementRepository.findPaidByStory(STORY)).thenReturn(List.of(
                paidEntitlement(READER, 50),
                paidEntitlement(READER, 30),
                paidEntitlement(READER, 20)));
        when(walletService.credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString()))
                .thenReturn(0L);

        ChapterRefundService.Refunds refunds = refundService.refundStory(STORY);

        // Sổ cái vẫn ghi ba dòng — nó là chứng từ. Hộp thư thì nói với một
        // *người*, và ba dòng liền nhau ở đó là spam chứ không phải thông tin.
        verify(walletService, org.mockito.Mockito.times(3))
                .credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString());
        assertThat(refunds.readers()).isEqualTo(1);

        List<NotificationDraft> drafts = capturedDrafts();
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).eventId()).isEqualTo("story-deleted:" + STORY + ":" + READER);
        assertThat(drafts.get(0).metadata())
                .containsEntry("refundedCoins", 100L)
                .containsEntry("chapters", 3);
        // Truyện đã biến mất, nên không gắn thực thể nào: một nút "về danh sách
        // chương" ở đây sẽ dẫn tới 404.
        assertThat(drafts.get(0).relatedEntityType()).isNull();
    }

    @Test
    @DisplayName("không ai mua thì không hoàn gì và cũng không báo gì")
    void nothingSoldNothingSaid() {
        when(entitlementRepository.findPaidByChapter(CHAPTER)).thenReturn(List.of());

        assertThat(refundService.refundChapter(CHAPTER).any()).isFalse();
        verify(notificationService, never()).notifyAll(any());
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private List<NotificationDraft> capturedDrafts() {
        ArgumentCaptor<Collection<NotificationDraft>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).notifyAll(captor.capture());
        return List.copyOf(captor.getValue());
    }

    private static ChapterEntitlement paidEntitlement(Long userId, long coinsSpent) {
        Story story = Story.builder().id(STORY).title("Truyện thử").build();
        Chapter chapter = Chapter.builder()
                .id(CHAPTER).story(story).title("Chương 1").chapterNumber(1).build();

        return ChapterEntitlement.builder()
                .user(User.builder().id(userId).username("u" + userId).build())
                .chapter(chapter)
                .coinsSpent(coinsSpent)
                .build();
    }

    /** Giữ cho các hằng của thông báo không lặng lẽ đổi nghĩa. */
    @Test
    @DisplayName("thông báo gỡ chương giữ đúng loại, mức và hành động")
    void theShapeOfTheNotificationIsFixed() {
        when(entitlementRepository.findPaidByChapter(CHAPTER))
                .thenReturn(List.of(paidEntitlement(READER, 50)));
        when(walletService.credit(anyLong(), anyLong(), any(), any(), anyLong(), anyString()))
                .thenReturn(0L);

        refundService.refundChapter(CHAPTER);

        NotificationDraft draft = capturedDrafts().get(0);
        assertThat(draft.type()).isEqualTo(NotificationType.CHAPTER_DELETED);
        assertThat(draft.priority()).isEqualTo(NotificationPriority.IMPORTANT);
        assertThat(draft.actionType()).isEqualTo(NotificationAction.VIEW_REFUND_HISTORY);
        // Truyện vẫn còn sống, nên thông báo mang theo một chỗ để quay về.
        assertThat(draft.relatedEntityType()).isEqualTo(NotificationEntityType.STORY);
        assertThat(draft.relatedEntityId()).isEqualTo(STORY);
    }
}
