package com.storytts.backend.service.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sổ kết nối SSE, đánh theo một khóa tùy bên dùng chọn.
 *
 * <h3>Vì sao lớp này tồn tại</h3>
 * Trang này có hai đường đẩy tin xuống trình duyệt, và chúng khác nhau đúng ở
 * <b>một</b> chỗ: cái gì làm khóa.
 *
 * <pre>
 *   {@link ChapterEventStream} → khóa là chương  → "chương này vừa đổi"
 *   {@link UserEventStream}    → khóa là người   → "bạn có một thông báo"
 * </pre>
 *
 * Mọi thứ còn lại giống hệt nhau, và không có phần nào trong số đó là hiển
 * nhiên: dọn sổ trên cả ba đường kết thúc (đóng, hết hạn, lỗi), không trừ bộ
 * đếm hai lần khi hai callback cùng bắn, bỏ khóa rỗng để một máy chủ chạy lâu
 * không tích dần hàng nghìn {@code Set} trống, và trần số kết nối để một heap
 * 224MB không bị hàng nghìn request treo ăn hết.
 *
 * <p>Chép bộ ấy sang một lớp thứ hai là chép cả những chỗ dễ viết sai. Nên nó
 * nằm ở đây, đúng một bản, và hai lớp kia chỉ còn phần khác nhau thật: khóa là
 * gì, gửi khung tin nào, và nói gì trong log.
 *
 * <h3>Vì sao là một lớp thường chứ không phải một bean</h3>
 * Mỗi luồng cần trần và hạn sống của riêng nó — kết nối theo chương sống đúng
 * một lượt đọc, kết nối theo người sống suốt phiên đăng nhập — nên thứ được
 * dùng chung là <i>mã</i>, không phải một thể hiện. Bên dùng tự dựng một cái
 * trong hàm khởi tạo của mình và tự bắt nhịp tim cho nó, vì {@code @Scheduled}
 * chỉ chạy trên bean.
 *
 * @param <K> khóa gom nhóm kết nối: id chương, id người dùng…
 */
@Slf4j
public class SseHub<K> {

    /** Tên ngắn của luồng, chỉ dùng cho log. */
    private final String name;

    /**
     * Trần số kết nối đang mở của luồng này.
     *
     * <p>Mỗi kết nối là một request bất đồng bộ đang treo. Servlet bất đồng bộ
     * đã trả luồng Tomcat về rồi nên chúng không ăn vào 20 luồng ấy, nhưng vẫn
     * tốn một socket và một chỗ trong bộ nhớ. Chạm trần thì từ chối kết nối mới
     * chứ không đá kết nối cũ ra: người đang dùng dở không đáng bị mất tin vì có
     * người mới mở tab.
     */
    private final int maxSubscribers;

    private final long ttlMillis;

    private final Map<K, Set<SseEmitter>> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger openCount = new AtomicInteger();

    public SseHub(String name, int maxSubscribers, Duration ttl) {
        this.name = name;
        this.maxSubscribers = maxSubscribers;
        this.ttlMillis = ttl.toMillis();
    }

    /**
     * Mở một kết nối mới cho một khóa.
     *
     * @param helloEvent tên khung mở màn gửi ngay khi kết nối thông
     * @return null khi đã chạm trần hoặc khung mở màn không đi được — bên gọi
     *         trả 503 và trang vẫn chạy tiếp, chỉ mất phần tin tức thời
     */
    public SseEmitter subscribe(K key, String helloEvent) {
        if (openCount.get() >= maxSubscribers) {
            log.warn("Từ chối kết nối {} cho khóa {}: đã có {} kết nối đang mở",
                    name, key, openCount.get());
            return null;
        }

        SseEmitter emitter = new SseEmitter(ttlMillis);
        byKey.computeIfAbsent(key, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        openCount.incrementAndGet();

        // Cả ba đường kết thúc đều phải dọn: đóng bình thường, hết hạn, và lỗi
        // đường truyền. Thiếu một đường là rò rỉ chậm cho tới lúc chạm trần.
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(error -> remove(key, emitter));

        // Một khung mở màn, để trình duyệt biết kết nối đã thật sự thông. Không
        // có nó, một proxy đệm lại response sẽ khiến trang tưởng mình đang được
        // theo dõi trong khi chưa có gì tới nơi.
        try {
            emitter.send(SseEmitter.event().name(helloEvent).data(String.valueOf(key)));
        } catch (IOException | IllegalStateException ex) {
            remove(key, emitter);
            return null;
        }

        return emitter;
    }

    /**
     * Gửi một khung tin tới mọi kết nối của một khóa.
     *
     * <p>Mỗi lần gửi được bọc riêng: một trình duyệt đã chết không được kéo theo
     * những trình duyệt còn sống, và cũng không được kéo theo giao dịch nghiệp
     * vụ vừa commit ở phía trên.
     *
     * @return số kết nối đã nhận được khung tin
     */
    public int send(K key, String eventName, Object payload) {
        return dispatch(key, eventName, payload, false);
    }

    /**
     * Như {@link #send}, nhưng đóng kết nối ngay sau khi khung tin đi ra.
     *
     * <p>Dành cho lời báo cuối cùng của một khóa — chương đã bị gỡ thì luồng này
     * sẽ không bao giờ có gì khác để nói về nó nữa. Thứ tự bắt buộc là gửi
     * trước, đóng sau: đóng trước thì khung tin không bao giờ rời khỏi máy chủ.
     */
    public int sendAndClose(K key, String eventName, Object payload) {
        return dispatch(key, eventName, payload, true);
    }

    private int dispatch(K key, String eventName, Object payload, boolean close) {
        Set<SseEmitter> listeners = byKey.get(key);
        if (listeners == null || listeners.isEmpty()) {
            // Trường hợp thường gặp nhất: không ai đang mở. Một lần tra bản đồ,
            // không tốn gì thêm.
            return 0;
        }

        int delivered = 0;
        for (SseEmitter emitter : listeners) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
                delivered++;
                if (close) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException ex) {
                // Trình duyệt đã đi khỏi mà máy chủ chưa kịp biết. Không phải
                // lỗi đáng báo, chỉ là một kết nối cần dọn.
                log.debug("Không gửi được khung {} của luồng {} tới một kết nối của khóa {}",
                        eventName, name, key);
            } finally {
                if (close) {
                    // Bỏ sổ ngay tại đây thay vì đợi callback onCompletion mà
                    // complete() ở trên sẽ kích hoạt. Callback ấy do vùng chứa
                    // servlet gọi, và nó *sẽ* tới — nhưng tới lúc đó bộ đếm vẫn
                    // tính những kết nối này vào trần. remove() bỏ qua lần gọi
                    // thứ hai, nên callback tới sau không trừ bộ đếm lần nữa.
                    remove(key, emitter);
                }
            }
        }
        return delivered;
    }

    /**
     * Nhịp tim giữ kết nối qua các lớp proxy.
     *
     * <p>Nền tảng triển khai đóng một kết nối im lặng quá lâu, và một kết nối bị
     * đóng sau lưng thì cả hai đầu đều không biết. Một dòng comment SSE đủ để
     * giữ nó, và nó cũng là cách phát hiện sớm những kết nối đã chết.
     *
     * <p>Bên gọi quyết định nhịp, vì {@code @Scheduled} chỉ chạy trên bean.
     */
    public void heartbeat() {
        byKey.forEach((key, listeners) -> listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("nhip"));
            } catch (IOException | IllegalStateException ex) {
                remove(key, emitter);
            }
        }));
    }

    /** Số kết nối đang mở — dùng cho kiểm thử và cho log lúc chạm trần. */
    public int openConnections() {
        return openCount.get();
    }

    /** Có ai đang mở khóa này không — dùng để bỏ sớm việc dựng khung tin. */
    public boolean hasListeners(K key) {
        Set<SseEmitter> listeners = byKey.get(key);
        return listeners != null && !listeners.isEmpty();
    }

    private void remove(K key, SseEmitter emitter) {
        Set<SseEmitter> listeners = byKey.get(key);
        if (listeners == null || !listeners.remove(emitter)) {
            // Đã bị dọn bởi một đường khác — hai callback cùng bắn là chuyện
            // thường. Không giảm bộ đếm lần thứ hai.
            return;
        }
        openCount.decrementAndGet();

        // Bỏ luôn khóa rỗng: một máy chủ chạy lâu sẽ đi qua hàng nghìn khóa, và
        // giữ lại một Set rỗng cho mỗi khóa là một rò rỉ chậm.
        listeners = byKey.get(key);
        if (listeners != null && listeners.isEmpty()) {
            byKey.remove(key, listeners);
        }
    }
}
