package com.storytts.backend.service.realtime;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Luồng SSE báo cho người đang đọc, kiểm qua một vòng MVC thật.
 *
 * <h3>Vì sao phải đi qua MockMvc chứ không gọi thẳng vào lớp</h3>
 * {@link SseEmitter} chưa gắn vào một response nào thì chỉ <i>xếp hàng</i> những
 * gì được gửi vào nó, và {@code complete()} của nó không gọi tới callback nào cả.
 * Gọi thẳng {@code onContentDeleted} rồi khẳng định "đã gửi" là khẳng định về
 * một hàng đợi trong bộ nhớ, không phải về một khung tin nào rời khỏi máy chủ.
 *
 * <p>Cho nó chạy qua MockMvc thì bộ xử lý giá trị trả về của Spring nối emitter
 * vào một response thật, nên phần được kiểm ở đây là đúng thứ trình duyệt sẽ
 * nhận: tên sự kiện, JSON bên trong, và việc kết nối có thật sự đóng lại sau đó.
 *
 * <p>Controller nhỏ ở cuối tệp tồn tại để tránh phải dựng cả cây phụ thuộc của
 * {@code ChapterController} — thứ duy nhất cần ở đây là một đường HTTP dẫn tới
 * {@link ChapterEventStream#subscribe}.
 */
class ChapterEventStreamTest {

    private static final long CHAPTER = 41L;
    private static final long OTHER_CHAPTER = 77L;
    private static final long STORY = 7L;

    private ChapterEventStream stream;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stream = new ChapterEventStream();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestStreamController(stream)).build();
    }

    /* ------------------------------------------------------------------ */
    /* Gỡ một chương                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("người đang mở chương nhận được khung content-deleted kèm CHAPTER_DELETED")
    void aReaderOnTheChapterIsToldItIsGone() throws Exception {
        MvcResult reader = subscribe(CHAPTER);

        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, false));

        String body = bodyOf(reader);
        assertThat(body).contains("event:content-deleted");
        assertThat(body).contains("\"type\":\"CHAPTER_DELETED\"");
        assertThat(body).contains("\"chapterId\":" + CHAPTER);
        assertThat(body).contains("\"storyId\":" + STORY);
        assertThat(body).contains("\"refunded\":false");
    }

    @Test
    @DisplayName("gỡ chương này không đụng tới người đang đọc chương khác")
    void otherChaptersAreLeftAlone() throws Exception {
        MvcResult bystander = subscribe(OTHER_CHAPTER);

        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, false));

        assertThat(bodyOf(bystander)).doesNotContain("content-deleted");
        assertThat(stream.openConnections())
                .as("kết nối của chương không liên quan phải còn nguyên")
                .isEqualTo(1);
    }

    /**
     * Cờ này là thứ quyết định trang đọc có nói câu về hoàn Xu hay không.
     *
     * <p>Nó nói "có người được hoàn", không nói "bạn được hoàn" — luồng này không
     * đòi đăng nhập nên nó không biết đang nói với ai. Xem
     * {@link ChapterEventStream.ContentDeletedPayload}.
     */
    @Test
    @DisplayName("có người được hoàn Xu thì cờ refunded đi kèm khung tin")
    void theRefundFlagTravelsWithTheFrame() throws Exception {
        MvcResult reader = subscribe(CHAPTER);

        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, true));

        assertThat(bodyOf(reader)).contains("\"refunded\":true");
    }

    /* ------------------------------------------------------------------ */
    /* Gỡ cả truyện                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("gỡ cả truyện: mọi chương đang có người đọc đều được báo, kèm STORY_DELETED")
    void everyOpenChapterOfADeletedStoryIsTold() throws Exception {
        MvcResult first = subscribe(CHAPTER);
        MvcResult second = subscribe(OTHER_CHAPTER);

        // Danh sách gồm cả một chương không ai mở — đúng hình dạng thật khi gỡ
        // một truyện dài: phần lớn chương không có kết nối nào.
        stream.onContentDeleted(ContentDeleted.story(STORY, List.of(CHAPTER, 999L, OTHER_CHAPTER), true));

        for (MvcResult reader : List.of(first, second)) {
            assertThat(bodyOf(reader)).contains("event:content-deleted");
            assertThat(bodyOf(reader)).contains("\"type\":\"STORY_DELETED\"");
        }

        // Mỗi người được báo về đúng chương mình đang mở, không phải về chương
        // của người kia: trang đọc lọc theo chapterId, và một id sai sẽ bị nó bỏ qua.
        assertThat(bodyOf(first)).contains("\"chapterId\":" + CHAPTER);
        assertThat(bodyOf(second)).contains("\"chapterId\":" + OTHER_CHAPTER);
    }

    @Test
    @DisplayName("chương không có ai mở thì bỏ qua, không ném lỗi")
    void chaptersWithNoListenersAreSkipped() {
        stream.onContentDeleted(ContentDeleted.story(STORY, List.of(1L, 2L, 3L), false));
        assertThat(stream.openConnections()).isZero();
    }

    /* ------------------------------------------------------------------ */
    /* Dọn dẹp                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Chương đã bị gỡ thì luồng này không bao giờ có gì khác để nói về nó nữa.
     *
     * <p>Giữ kết nối mở là giữ một chỗ trong trần {@code MAX_SUBSCRIBERS} cho
     * một chương không tồn tại, tới tận mười lăm phút sau — xem
     * {@link ChapterEventStream#onContentDeleted}.
     */
    @Test
    @DisplayName("báo xong thì đóng kết nối, và chỗ trong trần được trả lại")
    void theConnectionIsClosedOnceTheNewsIsOut() throws Exception {
        subscribe(CHAPTER);
        assertThat(stream.openConnections()).isEqualTo(1);

        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, false));

        assertThat(stream.openConnections())
                .as("kết nối tới một chương đã bị gỡ không được ở lại")
                .isZero();
    }

    /**
     * Thứ tự bắt buộc: gửi trước, đóng sau.
     *
     * <p>Đóng trước thì khung tin không bao giờ rời khỏi máy chủ và người đang
     * nghe dở ngồi lại trên một chương không còn tồn tại — đúng tình huống mà cả
     * sự kiện này sinh ra để chấm dứt. Bài kiểm này nhìn thấy sự khác nhau ấy vì
     * nó đọc nội dung response <i>sau</i> khi kết nối đã đóng.
     */
    @Test
    @DisplayName("khung tin tới nơi trọn vẹn dù kết nối đóng ngay sau đó")
    void theFrameSurvivesTheCloseThatFollowsIt() throws Exception {
        MvcResult reader = subscribe(CHAPTER);

        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, false));

        assertThat(stream.openConnections()).isZero();
        assertThat(bodyOf(reader)).contains("\"type\":\"CHAPTER_DELETED\"");
    }

    /* ------------------------------------------------------------------ */
    /* Cùng luồng, sự kiện kia vẫn chạy                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Hai sự kiện đi chung một kết nối, và phải phân biệt được ở đầu bên kia.
     *
     * <p>Trang đọc gắn hai bộ lắng nghe theo <i>tên sự kiện</i>, nên tên là toàn
     * bộ thứ giữ cho lời mời "đọc bản mới" không bị hiểu thành lời báo "đã bị gỡ".
     */
    @Test
    @DisplayName("sửa nội dung và gỡ nội dung là hai tên sự kiện khác nhau trên cùng một luồng")
    void thetwoEventsKeepTheirOwnNames() throws Exception {
        MvcResult reader = subscribe(CHAPTER);

        stream.onChapterUpdated(new ChapterContentUpdated(CHAPTER, STORY, 8));
        stream.onContentDeleted(ContentDeleted.chapter(CHAPTER, STORY, false));

        String body = bodyOf(reader);
        assertThat(body).contains("event:chapter-updated");
        assertThat(body).contains("\"type\":\"CHAPTER_UPDATED\"");
        assertThat(body).contains("event:content-deleted");
        assertThat(body).contains("\"type\":\"CHAPTER_DELETED\"");
    }

    /* ------------------------------------------------------------------ */
    /* Tiện ích                                                            */
    /* ------------------------------------------------------------------ */

    /** Mở một kết nối SSE thật qua MVC và trả về kết quả để đọc nội dung sau. */
    private MvcResult subscribe(long chapterId) throws Exception {
        return mockMvc.perform(get("/test-events/{id}", chapterId))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String bodyOf(MvcResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    /**
     * Đường HTTP tối thiểu tới {@link ChapterEventStream#subscribe}.
     *
     * <p>Cùng hình dạng với {@code ChapterController.events} — kể cả nhánh 503
     * khi chạm trần — nhưng không kéo theo cây phụ thuộc của controller thật.
     */
    @RestController
    static class TestStreamController {

        private final ChapterEventStream stream;

        TestStreamController(ChapterEventStream stream) {
            this.stream = stream;
        }

        @GetMapping(value = "/test-events/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        ResponseEntity<SseEmitter> events(@PathVariable Long id) {
            SseEmitter emitter = stream.subscribe(id);
            return emitter == null
                    ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
                    : ResponseEntity.ok(emitter);
        }
    }
}
