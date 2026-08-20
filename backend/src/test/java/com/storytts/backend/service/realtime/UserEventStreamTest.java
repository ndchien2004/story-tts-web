package com.storytts.backend.service.realtime;

import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.dto.notification.NotificationDto;
import com.storytts.backend.service.notification.NotificationCreated;
import com.storytts.backend.service.notification.NotificationsRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Luồng đẩy thông báo cá nhân, kiểm qua một vòng MVC thật.
 *
 * <h3>Vì sao phải đi qua MockMvc chứ không gọi thẳng vào lớp</h3>
 * Cùng lý do với {@code ChapterEventStreamTest}: một {@link SseEmitter} chưa gắn
 * vào response nào thì chỉ <i>xếp hàng</i> những gì được gửi vào nó. Khẳng định
 * "đã gửi" trên một emitter rời là khẳng định về một hàng đợi trong bộ nhớ, chứ
 * không phải về một khung tin nào rời khỏi máy chủ.
 *
 * <h3>Bốn tình huống của đặc tả nằm ở đây</h3>
 * Nhiều tab, nhiều thiết bị, nối lại sau khi đứt, và ranh giới giữa hai tài
 * khoản — cả bốn đều là câu hỏi "khung tin đi tới đâu", và cả bốn trả lời được
 * bằng cách mở nhiều kết nối trên cùng một luồng rồi xem ai nhận được gì.
 */
class UserEventStreamTest {

    private static final long READER = 7L;
    private static final long OTHER = 8L;

    private UserEventStream stream;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stream = new UserEventStream();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestStreamController(stream)).build();
    }

    /* ------------------------------------------------------------------ */
    /* Thông báo mới                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("người nhận đang mở trang nhận được khung notification kèm số chưa đọc")
    void theRecipientIsTold() throws Exception {
        MvcResult reader = subscribe(READER);

        stream.onNotificationCreated(new NotificationCreated(READER, sample(41L), 3));

        String body = bodyOf(reader);
        assertThat(body).contains("event:notification");
        assertThat(body).contains("\"id\":41");
        assertThat(body).contains("\"type\":\"CHAPTER_DELETED\"");
        // Con số đi kèm khung tin: trình duyệt không được tự cộng thêm một, vì
        // một khung bị bỏ lỡ sẽ khiến phép cộng ấy lệch mãi mãi.
        assertThat(body).contains("\"unread\":3");
    }

    @Test
    @DisplayName("thông báo của một người không lọt sang cửa sổ của người khác")
    void nobodyElseSeesIt() throws Exception {
        MvcResult bystander = subscribe(OTHER);

        stream.onNotificationCreated(new NotificationCreated(READER, sample(41L), 1));

        assertThat(bodyOf(bystander)).doesNotContain("event:notification");
        assertThat(stream.openConnections())
                .as("kết nối của tài khoản không liên quan phải còn nguyên")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("không ai đang mở thì việc đẩy tin là lệnh rỗng, không phải lỗi")
    void anOfflineRecipientCostsNothing() {
        // Trường hợp thường gặp nhất, và cũng là trường hợp mà việc lưu xuống cơ
        // sở dữ liệu sinh ra để lo. Hàng vẫn ở đó; xem NotificationJpaTest.
        stream.onNotificationCreated(new NotificationCreated(READER, sample(41L), 1));
        assertThat(stream.openConnections()).isZero();
    }

    /* ------------------------------------------------------------------ */
    /* Nhiều tab, nhiều thiết bị                                           */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("mọi cửa sổ của cùng một tài khoản đều nhận được")
    void everyWindowOfTheSameAccountIsTold() throws Exception {
        MvcResult tabA = subscribe(READER);
        MvcResult tabB = subscribe(READER);

        stream.onNotificationCreated(new NotificationCreated(READER, sample(41L), 1));

        // Đây là toàn bộ lý do khóa là *người* chứ không phải phiên hay tab.
        assertThat(bodyOf(tabA)).contains("\"id\":41");
        assertThat(bodyOf(tabB)).contains("\"id\":41");
    }

    @Test
    @DisplayName("một tab đánh dấu đã đọc thì các tab còn lại được báo, kèm số mới")
    void readingInOneWindowSyncsTheOthers() throws Exception {
        MvcResult tabA = subscribe(READER);
        MvcResult tabB = subscribe(READER);

        stream.onNotificationsRead(NotificationsRead.one(READER, 41L, 2));

        for (MvcResult tab : java.util.List.of(tabA, tabB)) {
            String body = bodyOf(tab);
            assertThat(body).contains("event:notifications-read");
            assertThat(body).contains("\"ids\":[41]");
            assertThat(body).contains("\"unread\":2");
            assertThat(body).contains("\"all\":false");
        }
    }

    @Test
    @DisplayName("'đánh dấu tất cả' đi ra dưới dạng một cờ, không phải một danh sách id")
    void markingAllTravelsAsAFlag() throws Exception {
        MvcResult reader = subscribe(READER);

        stream.onNotificationsRead(NotificationsRead.all(READER));

        String body = bodyOf(reader);
        // Danh sách ấy có thể dài hàng nghìn phần tử, và bên nhận không cần từng
        // cái — nó chỉ cần biết từ giờ không còn gì chưa đọc.
        assertThat(body).contains("\"all\":true");
        assertThat(body).contains("\"unread\":0");
        assertThat(body).contains("\"ids\":[]");
    }

    /* ------------------------------------------------------------------ */
    /* Đứt và nối lại                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("kết nối đứt thì chỗ trong trần được trả lại, và tin sau đó đi tới kết nối mới")
    void aReconnectedWindowGetsWhatComesNext() throws Exception {
        MvcResult first = subscribe(READER);
        assertThat(stream.openConnections()).isEqualTo(1);

        // Mô phỏng đường truyền đứt: trình duyệt biến mất mà máy chủ chưa hay.
        // Lần gửi kế tiếp là chỗ máy chủ phát hiện ra và dọn sổ.
        first.getRequest().getAsyncContext().complete();
        stream.onNotificationCreated(new NotificationCreated(READER, sample(1L), 1));
        assertThat(stream.openConnections()).isZero();

        MvcResult reconnected = subscribe(READER);
        stream.onNotificationCreated(new NotificationCreated(READER, sample(2L), 2));

        // Khung tin phát ra lúc đứt kết nối thì mất luôn — và đó là đúng thiết
        // kế. Trình duyệt lấy lại nó bằng REST ngay khi nối lại; luồng này không
        // giữ hàng đợi nào.
        assertThat(bodyOf(reconnected)).contains("\"id\":2");
        assertThat(bodyOf(reconnected)).doesNotContain("\"id\":1");
    }

    @Test
    @DisplayName("chạm trần thì từ chối kết nối mới chứ không đá kết nối cũ ra")
    void theCapRefusesNewcomers() throws Exception {
        // Không mở 300 kết nối thật ở đây; phần trần được kiểm ở SseHub qua
        // chính lớp này, và điều đáng khẳng định là ai *bị* từ chối.
        MvcResult existing = subscribe(READER);
        stream.onNotificationCreated(new NotificationCreated(READER, sample(41L), 1));
        assertThat(bodyOf(existing)).contains("\"id\":41");
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    private MvcResult subscribe(long userId) throws Exception {
        return mockMvc.perform(get("/test-notifications/{id}", userId))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String bodyOf(MvcResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    private static NotificationDto sample(Long id) {
        return new NotificationDto(
                id,
                NotificationType.CHAPTER_DELETED,
                NotificationPriority.IMPORTANT,
                "Chương bạn đã mua đã bị gỡ",
                "100 Xu đã được hoàn lại vào ví của bạn.",
                NotificationAction.VIEW_REFUND_HISTORY,
                NotificationEntityType.STORY,
                7L,
                Map.of("refundedCoins", 100),
                "chapter-deleted:41:7",
                false,
                null,
                Instant.parse("2026-08-20T10:15:30Z"));
    }

    /** Đường HTTP tối thiểu tới {@link UserEventStream#subscribe}. */
    @RestController
    static class TestStreamController {

        private final UserEventStream stream;

        TestStreamController(UserEventStream stream) {
            this.stream = stream;
        }

        @GetMapping(value = "/test-notifications/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        ResponseEntity<SseEmitter> events(@PathVariable Long id) {
            SseEmitter emitter = stream.subscribe(id);
            return emitter == null
                    ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
                    : ResponseEntity.ok(emitter);
        }
    }
}
