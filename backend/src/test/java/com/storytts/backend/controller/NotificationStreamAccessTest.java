package com.storytts.backend.controller;

import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.dto.notification.NotificationDto;
import com.storytts.backend.service.CurrentUserService;
import com.storytts.backend.service.notification.NotificationCreated;
import com.storytts.backend.service.notification.NotificationService;
import com.storytts.backend.service.notification.NotificationStreamTickets;
import com.storytts.backend.service.realtime.UserEventStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai mở được luồng thông báo của ai.
 *
 * <h3>Vì sao đường này cần một bài kiểm riêng ở tầng HTTP</h3>
 * Đây là điểm duy nhất của cả API mà {@code SecurityConfig} <b>không</b> chặn
 * trước: {@code EventSource} của trình duyệt không gửi được header, nên chuỗi
 * lọc xác thực không có gì để đọc. Việc kiểm quyền chuyển vào bên trong
 * controller, và một bài kiểm ở tầng service sẽ không nhìn thấy nó.
 *
 * <p>Nói cách khác: nếu dòng đổi vé trong {@code NotificationController.stream}
 * bị xóa đi, không một bài kiểm nào khác trong dự án này đỏ lên. Bài này đỏ.
 */
class NotificationStreamAccessTest {

    private static final long READER = 7L;

    private NotificationStreamTickets tickets;
    private UserEventStream stream;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tickets = new NotificationStreamTickets();
        stream = new UserEventStream();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(
                        Mockito.mock(NotificationService.class),
                        tickets,
                        stream,
                        Mockito.mock(CurrentUserService.class)))
                .build();
    }

    @Test
    @DisplayName("không có vé thì không mở được luồng")
    void noTicketNoStream() throws Exception {
        mockMvc.perform(get("/api/notifications/stream").param("ticket", "khong-phai-ve"))
                .andExpect(status().isUnauthorized());

        assertThat(stream.openConnections()).isZero();
    }

    @Test
    @DisplayName("thiếu hẳn tham số vé là một request sai, không phải một luồng mở")
    void amissingTicketIsARefusal() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().is4xxClientError());

        assertThat(stream.openConnections()).isZero();
    }

    @Test
    @DisplayName("vé hợp lệ mở đúng luồng của người đã xin nó")
    void avalidTicketOpensTheOwnersStream() throws Exception {
        MvcResult opened = mockMvc
                .perform(get("/api/notifications/stream").param("ticket", tickets.issue(READER)))
                .andExpect(request().asyncStarted())
                .andReturn();

        stream.onNotificationCreated(new NotificationCreated(READER, sample(), 1));

        assertThat(opened.getResponse().getContentAsString()).contains("event:notification");
    }

    @Test
    @DisplayName("vé đã dùng không mở lại được — kể cả khi nó vẫn còn trong hạn")
    void aspentTicketCannotBeReplayed() throws Exception {
        String ticket = tickets.issue(READER);

        mockMvc.perform(get("/api/notifications/stream").param("ticket", ticket))
                .andExpect(request().asyncStarted());

        // Đây cũng chính là cơ chế tự nối lại của EventSource bị từ chối, và đó
        // là hành vi mong muốn: mỗi lần nối lại phải kèm một lần đồng bộ hộp
        // thư, nên nó phải đi qua mã của trình duyệt chứ không qua trình duyệt.
        mockMvc.perform(get("/api/notifications/stream").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("vé của người này không mở được hộp thư của người kia")
    void aticketDoesNotCrossAccounts() throws Exception {
        MvcResult opened = mockMvc
                .perform(get("/api/notifications/stream").param("ticket", tickets.issue(READER)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Thông báo của một tài khoản khác đi qua cùng một luồng, cùng một máy
        // chủ — và không được lọt vào kết nối này.
        stream.onNotificationCreated(new NotificationCreated(8L, sample(), 1));

        assertThat(opened.getResponse().getContentAsString()).doesNotContain("event:notification");
    }

    private static NotificationDto sample() {
        return new NotificationDto(
                41L,
                NotificationType.VIP_GRANTED,
                NotificationPriority.SUCCESS,
                "Chúc mừng, bạn đã là thành viên VIP",
                "Quản trị viên vừa cấp quyền VIP cho tài khoản của bạn.",
                NotificationAction.VIEW_VIP,
                NotificationEntityType.STORY,
                null,
                Map.of(),
                "vip-granted:7:1",
                false,
                null,
                Instant.parse("2026-08-20T10:15:30Z"));
    }
}
