package com.storytts.backend.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * Trần số kết nối đang mở của cả máy chủ.
     *
     * <p>Mỗi kết nối là một request bất đồng bộ đang treo. Servlet bất đồng bộ đã
     * trả luồng Tomcat về rồi nên chúng không ăn vào 20 luồng ấy, nhưng vẫn tốn
     * một socket và một chỗ trong bộ nhớ — và heap ở đây là 224MB. Chạm trần thì
     * từ chối kết nối mới chứ không đá kết nối cũ ra: người đang đọc dở không
     * đáng bị mất thông báo vì có người mới mở tab.
     */
    private static final int MAX_SUBSCRIBERS = 500;

    /** Nhịp giữ kết nối, ngắn hơn hạn chờ của mọi proxy thường gặp. */
    private static final long HEARTBEAT_MS = 25_000L;

    private final Map<Long, Set<SseEmitter>> byChapter = new ConcurrentHashMap<>();
    private final AtomicInteger openCount = new AtomicInteger();

    /**
     * Một trình duyệt bắt đầu theo dõi một chương.
     *
     * @return null khi đã chạm trần — bên gọi trả 503 và trang đọc chạy tiếp mà
     *         không có thông báo tức thời, chứ không hỏng
     */
    public SseEmitter subscribe(Long chapterId) {
        if (openCount.get() >= MAX_SUBSCRIBERS) {
            log.warn("Từ chối theo dõi chương {}: đã có {} kết nối đang mở", chapterId, openCount.get());
            return null;
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TTL.toMillis());
        Set<SseEmitter> listeners =
                byChapter.computeIfAbsent(chapterId, key -> new CopyOnWriteArraySet<>());
        listeners.add(emitter);
        openCount.incrementAndGet();

        // Cả ba đường kết thúc đều phải dọn: đóng bình thường, hết hạn, và lỗi
        // đường truyền. Thiếu một đường là rò rỉ chậm cho tới lúc chạm trần.
        emitter.onCompletion(() -> remove(chapterId, emitter));
        emitter.onTimeout(() -> remove(chapterId, emitter));
        emitter.onError(error -> remove(chapterId, emitter));

        // Một khung mở màn, để trình duyệt biết kết nối đã thật sự thông. Không
        // có nó, một proxy đệm lại response sẽ khiến trang đọc tưởng mình đang
        // được theo dõi trong khi chưa có gì tới nơi.
        try {
            emitter.send(SseEmitter.event().name("subscribed").data(chapterId));
        } catch (IOException | IllegalStateException ex) {
            remove(chapterId, emitter);
            return null;
        }

        return emitter;
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
        Set<SseEmitter> listeners = byChapter.get(event.chapterId());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        log.info("Báo chương {} lên phiên bản {} cho {} trình duyệt",
                event.chapterId(), event.contentVersion(), listeners.size());

        listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("chapter-updated")
                        .data(new ChapterUpdatedPayload(
                                "CHAPTER_UPDATED",
                                event.chapterId(),
                                event.storyId(),
                                event.contentVersion())));
            } catch (IOException | IllegalStateException ex) {
                // Trình duyệt đã đi khỏi mà máy chủ chưa kịp biết. Không phải lỗi
                // đáng báo, chỉ là một kết nối cần dọn.
                remove(event.chapterId(), emitter);
            }
        });
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
        byChapter.forEach((chapterId, listeners) -> listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("nhip"));
            } catch (IOException | IllegalStateException ex) {
                remove(chapterId, emitter);
            }
        }));
    }

    /** Số kết nối đang mở — dùng cho kiểm thử và cho log lúc chạm trần. */
    public int openConnections() {
        return openCount.get();
    }

    private void remove(Long chapterId, SseEmitter emitter) {
        Set<SseEmitter> listeners = byChapter.get(chapterId);
        if (listeners == null || !listeners.remove(emitter)) {
            // Đã bị dọn bởi một đường khác — hai callback cùng bắn là chuyện
            // thường. Không giảm bộ đếm lần thứ hai.
            return;
        }
        openCount.decrementAndGet();

        // Bỏ luôn khóa rỗng: một máy chủ chạy lâu sẽ đi qua hàng nghìn chương, và
        // giữ lại một Set rỗng cho mỗi chương là một rò rỉ chậm.
        listeners = byChapter.get(chapterId);
        if (listeners != null && listeners.isEmpty()) {
            byChapter.remove(chapterId, listeners);
        }
    }

    /**
     * Khung tin gửi xuống trình duyệt.
     *
     * <p>Đúng bốn trường, và không trường nào là nội dung chương — xem
     * {@link ChapterContentUpdated}.
     */
    public record ChapterUpdatedPayload(String type, Long chapterId, Long storyId, int contentVersion) {
    }
}
