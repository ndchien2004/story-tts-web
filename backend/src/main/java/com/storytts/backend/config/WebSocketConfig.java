package com.storytts.backend.config;

import com.storytts.backend.service.support.SupportHandshakeInterceptor;
import com.storytts.backend.service.support.SupportWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Đường WebSocket của hộp thư hỗ trợ, và mấy cái trần của nó.
 *
 * <h3>Vì sao WebSocket thô, không phải STOMP</h3>
 * Spring có sẵn một tầng STOMP đầy đủ — môi giới tin, đích đến, đăng ký kênh,
 * tích hợp bảo mật riêng. Nó giải quyết những vấn đề mà một hộp thư hỗ trợ hai
 * bên không có: ở đây chỉ có hai nhóm người nhận, một loại nội dung, và không
 * có gì cần định tuyến theo mẫu đích đến. Đổi lại nó mang theo một mô hình phân
 * quyền thứ hai đặt cạnh mô hình đã có — đúng thứ đặc tả cấm.
 *
 * <p>Nên ở đây là {@code WebSocketHandler} thô với khung JSON: cùng hình dạng
 * với {@code SseHub} — một sổ kết nối, một bộ khung tin, và không gì khác.
 *
 * <h3>{@code setAllowedOrigins} là một hàng rào thật, không phải CORS</h3>
 * Đây là chỗ dễ hiểu nhầm nhất của WebSocket, nên nó đáng được nói thẳng:
 * <b>trình duyệt không áp CORS lên WebSocket</b>. Một trang bất kỳ mở được kết
 * nối tới máy chủ này, và header {@code Origin} có được gửi kèm nhưng không ai
 * ở phía trình duyệt kiểm nó. Phép kiểm ở dòng dưới là phép kiểm duy nhất tồn
 * tại.
 *
 * <p>Nó dùng lại đúng danh sách {@code app.cors.allowed-origins} đã cấu hình cho
 * REST, nên không có hai danh sách để lệch nhau — và một tên miền bị gỡ khỏi
 * danh sách ấy mất luôn cả hai đường cùng lúc.
 *
 * <p>Cần nói rõ giới hạn: hàng rào này chặn một trang lạ mở kết nối <i>từ trình
 * duyệt của nạn nhân</i>. Nó không chặn được một chương trình tự viết, vì
 * {@code Origin} khi ấy là thứ kẻ gọi tự đặt. Thứ chặn chương trình tự viết là
 * cái vé, và phép kiểm quyền cho từng lệnh.
 *
 * <h3>Vì sao không có SockJS</h3>
 * SockJS mô phỏng WebSocket qua long-polling cho những trình duyệt không hỗ
 * trợ. Trình duyệt ấy không còn tồn tại trong thực tế, và cái giá của nó thì có
 * thật: mỗi kết nối giả lập là một chuỗi request HTTP treo, tức là đúng cái áp
 * lực mà máy chủ hai mươi luồng này không chịu nổi. Đường lui ở đây là REST —
 * hộp thư vẫn gửi và vẫn đọc được, chỉ không tức thời. Xem
 * {@code SupportController}.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Đường của kết nối.
     *
     * <p>Ngoài {@code /api/**} có chủ ý: nó không phải REST, không trả JSON theo
     * request/response, và không nên bị lẫn vào những luật đường dẫn viết cho
     * REST — {@code RateLimitRules} chẳng hạn, nơi mọi mẫu đều bắt đầu bằng
     * {@code /api}.
     */
    private static final String ENDPOINT = "/ws/support";

    private final SupportWebSocketHandler handler;
    private final SupportHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    /*
     * Trần kích thước khung tin KHÔNG nằm ở đây, và chỗ nó nằm là một quyết
     * định đáng ghi lại.
     *
     * Cách hiển nhiên là một bean ServletServerContainerFactoryBean với
     * setMaxTextMessageBufferSize(...). Nó hỏng ở một chỗ cụ thể: bean ấy đi
     * tìm thuộc tính 'jakarta.websocket.server.ServerContainer' trong
     * ServletContext lúc khởi tạo, và thuộc tính ấy chỉ tồn tại khi có một vùng
     * chứa servlet THẬT. Mọi bài kiểm @SpringBootTest chạy với môi trường web
     * giả, nên bean ấy làm hỏng việc nạp context của cả những bài kiểm không
     * liên quan gì tới WebSocket.
     *
     * Đặt trần theo từng phiên thay vì theo vùng chứa
     * (WebSocketSession.setTextMessageSizeLimit, gọi trong
     * afterConnectionEstablished — xem SupportWebSocketHandler) làm đúng cùng
     * một việc, chạy được ở mọi môi trường, và có thêm một điểm hơn: nó được
     * tính từ chính app.support.max-message-length, nên nới trần tin nhắn trong
     * cấu hình không bao giờ dẫn tới cảnh cấu hình nói một đằng còn vùng chứa
     * cắt một nẻo.
     */
}
