package com.storytts.backend.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

/**
 * Đường một chiều từ máy chủ tới những trình duyệt đang mở một chương.
 *
 * <h3>Vì sao SSE chứ không phải WebSocket</h3>
 * Thứ cần gửi chỉ đi một chiều và chỉ có một loại: "chương này giờ ở phiên bản
 * n". Trình duyệt không có gì để nói ngược lại — mọi hành động của người đọc đã
 * có REST lo. WebSocket sẽ mang theo một starter mới, một tầng bắt tay riêng, và
 * một cơ chế kết nối lại phải tự viết; {@link SseEmitter} có sẵn trong Spring MVC
 * và {@code EventSource} của trình duyệt tự kết nối lại khi đứt. Với đúng một
 * loại thông báo, chênh lệch ấy toàn là chi phí.
 *
 * <h3>Vì sao gửi thẳng trên luồng vừa commit, không đẩy sang {@code @Async}</h3>
 * Pool async chỉ có 2–4 luồng và đang dùng để dựng audio — mỗi lượt dựng chiếm
 * một luồng hàng phút. Xếp thông báo vào đúng hàng đợi ấy nghĩa là người đang
 * đọc biết tin sau vài phút, đúng lúc thông báo đã hết ý nghĩa. Ghi vào một
 * {@code SseEmitter} là ghi vào bộ đệm response, không phải một vòng mạng, nên
 * làm ngay tại chỗ là rẻ; đổi lại là mỗi lần gửi được bọc riêng, để một trình
 * duyệt đã chết không kéo theo lần lưu chương của Admin.
 *
 * <h3>Quan hệ với luồng thông báo cá nhân</h3>
 * Phần sổ sách kết nối nằm ở {@link SseHub} và được dùng chung với
 * {@link UserEventStream} — cùng một cơ chế thời gian thực, hai cách đánh khóa.
 * Lớp này giữ phần riêng của nó: khóa là chương, và hai loại khung tin dưới đây.
 */
@Component
@Slf4j
public class ChapterEventStream {

    /**
     * Sống lâu hơn một lượt đọc bình thường, nhưng không vô hạn.
     *
     * <p>Hết hạn không phải là lỗi: {@code EventSource} tự mở lại ngay, nên cái
     * giá là một request mỗi mười lăm phút cho mỗi tab đang mở. Đổi lại là những
     * kết nối của tab đã đóng mà máy chủ chưa kịp nhận ra không nằm lại mãi.
     */
    private static final Duration EMITTER_TTL = Duration.ofMinutes(15);

    private static final int MAX_SUBSCRIBERS = 500;

    /** Nhịp giữ kết nối, ngắn hơn hạn chờ của mọi proxy thường gặp. */
    private static final long HEARTBEAT_MS = 25_000L;

    private final SseHub<Long> hub = new SseHub<>("chuong", MAX_SUBSCRIBERS, EMITTER_TTL);

    /**
     * Một trình duyệt bắt đầu theo dõi một chương.
     *
     * @return null khi đã chạm trần — bên gọi trả 503 và trang đọc chạy tiếp mà
     *         không có thông báo tức thời, chứ không hỏng
     */
    public SseEmitter subscribe(Long chapterId) {
        return hub.subscribe(chapterId, "subscribed");
    }

    /**
     * Báo cho những ai đang mở chương vừa đổi.
     *
     * <p>{@code AFTER_COMMIT} là điều kiện, không phải lựa chọn: gửi sớm hơn thì
     * một giao dịch cuộn ngược vẫn kịp bảo mọi trình duyệt rằng chương đã đổi, và
     * chúng sẽ đi tải lại đúng nội dung chúng đang có — kèm việc bỏ dở đoạn nghe.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChapterUpdated(ChapterContentUpdated event) {
        int told = hub.send(event.chapterId(), "chapter-updated",
                new ChapterUpdatedPayload(
                        "CHAPTER_UPDATED",
                        event.chapterId(),
                        event.storyId(),
                        event.contentVersion()));

        if (told > 0) {
            log.info("Báo chương {} lên phiên bản {} cho {} trình duyệt",
                    event.chapterId(), event.contentVersion(), told);
        }
    }

    /**
     * Báo cho những ai đang mở một chương vừa bị gỡ.
     *
     * <h3>Vì sao gửi xong thì đóng luôn kết nối</h3>
     * Với {@link ChapterContentUpdated}, chương vẫn còn và vẫn có thể đổi lần
     * nữa, nên kết nối ở lại. Ở đây thì không: chương đã biến mất khỏi cơ sở dữ
     * liệu, nên luồng này sẽ không bao giờ có gì khác để nói về nó. Giữ nó mở là
     * giữ một chỗ trong trần {@link #MAX_SUBSCRIBERS} cho một chương không tồn
     * tại, tới tận mười lăm phút sau.
     *
     * <p>{@code complete()} chứ không {@code completeWithError()}: lời báo đã đi
     * tới nơi trọn vẹn, phần còn lại là dọn dẹp. Trình duyệt sẽ tự mở lại kết
     * nối sau một lần đóng sạch — đó là hành vi mặc định của {@code EventSource}
     * — nên bên nhận có trách nhiệm tự đóng khi nhận được khung này. Nó có làm
     * việc ấy: xem {@code useChapterUpdates}. Không làm được thì cái giá chỉ là
     * một kết nối vô ích tới một chương đã chết, không phải một vòng lặp.
     *
     * <p>Thứ tự bắt buộc là gửi trước, đóng sau — xem {@link SseHub#sendAndClose}.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentDeleted(ContentDeleted event) {
        String type = event.wholeStory() ? "STORY_DELETED" : "CHAPTER_DELETED";

        for (Long chapterId : event.chapterIds()) {
            int told = hub.sendAndClose(chapterId, "content-deleted",
                    new ContentDeletedPayload(type, chapterId, event.storyId(), event.refunded()));

            if (told > 0) {
                log.info("Báo chương {} đã bị gỡ ({}) cho {} trình duyệt", chapterId, type, told);
            }
        }
    }

    /**
     * Nhịp tim giữ kết nối qua các lớp proxy.
     *
     * <p>Render đóng một kết nối im lặng quá lâu, và một kết nối bị đóng sau lưng
     * thì cả hai đầu đều không biết — trang đọc tưởng mình vẫn đang được theo dõi
     * cho tới lần thao tác tiếp theo. Một dòng comment SSE mỗi 25 giây đủ để giữ
     * nó, và nó cũng là cách phát hiện sớm những kết nối đã chết.
     */
    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        hub.heartbeat();
    }

    /** Số kết nối đang mở — dùng cho kiểm thử và cho log lúc chạm trần. */
    public int openConnections() {
        return hub.openConnections();
    }

    /**
     * Khung tin gửi xuống trình duyệt.
     *
     * <p>Đúng bốn trường, và không trường nào là nội dung chương — xem
     * {@link ChapterContentUpdated}.
     */
    public record ChapterUpdatedPayload(String type, Long chapterId, Long storyId, int contentVersion) {
    }

    /**
     * Khung tin báo nội dung đã bị gỡ.
     *
     * <p>{@code type} là {@code CHAPTER_DELETED} hoặc {@code STORY_DELETED}, và
     * sự khác nhau ấy quyết định trang đọc còn chỗ nào để đưa người ta về hay
     * không: chương mất thì danh sách chương vẫn còn, cả truyện mất thì không.
     *
     * <p>{@code refunded} nói rằng <b>có người</b> được hoàn Xu, không nói người
     * nhận khung tin này có phải một trong số đó không. Luồng SSE này là đường
     * công khai — nó không đòi đăng nhập, vì nó không mang gì riêng tư — nên nó
     * không biết mình đang nói với ai. Trang đọc dùng cờ này để nói một câu có
     * điều kiện ("nếu bạn đã mua chương này…") thay vì một lời khẳng định mà nó
     * không có cơ sở để đưa ra. Câu khẳng định — kèm đúng số Xu — đi đường khác:
     * một thông báo cá nhân trên {@link UserEventStream}, sau lớp xác thực.
     */
    public record ContentDeletedPayload(String type, Long chapterId, Long storyId,
                                        boolean refunded) {
    }
}
