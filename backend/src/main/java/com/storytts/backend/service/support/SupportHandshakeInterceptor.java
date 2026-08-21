package com.storytts.backend.service.support;

import com.storytts.backend.config.CorsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Cửa vào của kết nối WebSocket: đổi vé lấy danh tính, hoặc từ chối bắt tay.
 *
 * <h3>"Đã kết nối" không có nghĩa là "đã xác thực"</h3>
 * Đó là toàn bộ lý do lớp này tồn tại, và nó là chỗ duy nhất trong tính năng
 * này có thể trả lời câu ấy: sau khi bắt tay xong thì không còn request HTTP
 * nào để chuỗi filter chạy trên đó nữa. Bắt tay là <i>một</i> request HTTP —
 * lần duy nhất — và đây là chỗ nó đi qua.
 *
 * <p>Trả về {@code false} nghĩa là không có kết nối nào được mở và trình duyệt
 * nhận một lỗi HTTP. Không có trạng thái nửa vời "đã nối nhưng chưa xác thực",
 * vì một trạng thái như thế là một trạng thái mà một dòng mã về sau sẽ quên
 * kiểm.
 *
 * <h3>Hai phép kiểm, không phải một</h3>
 * <pre>
 *   1. vé có đổi được không            → danh tính
 *   2. tài khoản ấy còn dùng được không → trạng thái
 * </pre>
 *
 * Phép thứ hai không thừa dù vé chỉ sống chín mươi giây: quản trị viên hoàn
 * toàn có thể khóa một tài khoản trong khoảng giữa lúc phát vé và lúc bắt tay.
 * Nó cũng là chỗ lấy ra vai trò — thứ quyết định kết nối này nằm ở nhóm nào
 * trong sổ.
 *
 * <h3>Vì sao vai trò được chốt ở đây rồi vẫn kiểm lại ở mỗi lệnh</h3>
 * Vì hai câu hỏi khác nhau. Vai trò trong sổ trả lời "khung tin nào đi tới kết
 * nối này" — một câu về định tuyến, và nó phải trả lời được mà không đụng cơ sở
 * dữ liệu, vì nó được hỏi cho mỗi tin nhắn của mọi luồng. Câu "người này có
 * được phép làm việc vừa yêu cầu không" thì đọc lại từ cơ sở dữ liệu, mỗi lần.
 * Xem {@code SupportService.resolveActor}.
 */
@Component
@Slf4j
public class SupportHandshakeInterceptor implements HandshakeInterceptor {

    /** Khóa của danh tính trong túi thuộc tính mà phiên WebSocket mang theo. */
    static final String ATTR_USER_ID = "supportUserId";
    static final String ATTR_ROLE = "supportRole";

    private final SupportStreamTickets tickets;
    private final SupportService supportService;

    /**
     * Phép kiểm nguồn của chính Spring, chạy <b>trước</b> phép đổi vé.
     *
     * <h3>Vì sao phải tự gọi nó ở đây thay vì để Spring gọi</h3>
     * Spring <i>có</i> gọi nó — nhưng <b>sau</b> interceptor này. Xem
     * {@code AbstractWebSocketHandlerRegistration.getInterceptors()}: nó nối
     * thêm một {@code OriginHandshakeInterceptor} vào <i>cuối</i> danh sách,
     * sau mọi interceptor do ứng dụng đăng ký.
     *
     * <p>Thứ tự ấy có hai hậu quả, và cả hai đều đắt đúng vào lúc đang gỡ lỗi
     * trên bản chạy thật:
     *
     * <ol>
     *   <li><b>Vé bị tiêu oan.</b> Vé là vé dùng một lần: {@code redeem} xóa nó
     *       khỏi bản đồ. Nếu phép kiểm nguồn từ chối <i>sau</i> đó thì cái vé
     *       đã mất, và mỗi lần trình duyệt thử lại là một vé nữa bị đốt cho một
     *       kết nối không bao giờ mở được.</li>
     *   <li><b>Không có dòng nhật ký nào.</b>
     *       {@code OriginHandshakeInterceptor} chỉ ghi ở mức {@code DEBUG}. Trên
     *       bản chạy thật thì một nguồn bị từ chối đi ra thành một con số 403
     *       trần trụi, không kèm chữ nào nói vì sao — trong khi đây là hàng rào
     *       <i>duy nhất</i> đứng trước đường WebSocket, vì trình duyệt không áp
     *       CORS lên nó.</li>
     * </ol>
     *
     * <p>Ủy quyền cho chính lớp của Spring chứ không tự so chuỗi: danh sách
     * nguồn có những luật nhỏ dễ viết sai một mình — cắt dấu gạch chéo cuối,
     * bỏ qua khi không có header {@code Origin} (máy khách không phải trình
     * duyệt), so không phân biệt hoa thường. Một bản chép tay ở đây sẽ là bản
     * thứ hai để lệch với bản Spring vẫn chạy sau nó.
     */
    private final OriginHandshakeInterceptor originCheck;

    public SupportHandshakeInterceptor(SupportStreamTickets tickets,
                                       SupportService supportService,
                                       CorsProperties corsProperties) {
        this.tickets = tickets;
        this.supportService = supportService;
        this.originCheck = new OriginHandshakeInterceptor(corsProperties.allowedOrigins());
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler handler,
                                   Map<String, Object> attributes) {

        // TRƯỚC khi tiêu vé. Xem ghi chú ở `originCheck`.
        if (!checkOrigin(request, response, handler, attributes)) {
            return false;
        }

        Long userId = tickets.redeem(ticketOf(request));
        if (userId == null) {
            // Không ghi cái vé vào nhật ký, kể cả khi nó sai: một chuỗi sai hôm
            // nay có thể là một chuỗi đúng bị gõ nhầm một ký tự.
            log.info("WEBSOCKET_AUTH_FAILED ho-tro: vé không hợp lệ hoặc đã dùng");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        SupportService.Actor actor;
        try {
            actor = supportService.resolveActor(userId);
        } catch (RuntimeException ex) {
            // Tài khoản bị khóa hoặc không còn tồn tại trong khoảng giữa lúc
            // phát vé và lúc bắt tay. Trả 401 chứ không nói rõ là vì sao —
            // cùng chính sách với chuỗi filter HTTP.
            log.info("WEBSOCKET_AUTH_FAILED ho-tro: tài khoản {} không dùng được", userId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, actor.id());
        attributes.put(ATTR_ROLE, actor.role());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
        // Không có gì để làm. Việc vào sổ xảy ra ở afterConnectionEstablished,
        // nơi đã có một WebSocketSession thật để ghi lại — ở đây thì chưa.
    }

    /**
     * Nguồn của trang mở kết nối có nằm trong danh sách cho phép không.
     *
     * <p>Phần việc là của Spring; phần thêm vào ở đây là <b>một dòng nhật ký ở
     * mức INFO</b>. Nó nói ra đúng ba thứ cần để sửa: nguồn bị từ chối là gì,
     * danh sách đang cho phép những gì, và tên biến môi trường phải sửa. Không
     * có nó, một tên miền mới trên Vercel — hoặc một dấu gạch chéo thừa trong
     * {@code CORS_ALLOWED_ORIGINS} — hiện ra dưới dạng "WebSocket im lặng", và
     * không có gì trong nhật ký máy chủ nối được hai chuyện ấy với nhau.
     */
    private boolean checkOrigin(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            if (originCheck.beforeHandshake(request, response, handler, attributes)) {
                return true;
            }
        } catch (Exception ex) {
            // Lớp của Spring khai báo `throws Exception` nhưng nhánh này không
            // ném. Từ chối nếu nó ném: một phép kiểm bảo mật không kết luận
            // được thì câu trả lời an toàn là "không".
            log.warn("Không chạy được phép kiểm nguồn WebSocket", ex);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        log.info("WEBSOCKET_ORIGIN_REJECTED ho-tro: nguồn {} không nằm trong danh sách "
                        + "cho phép {}. Sửa biến môi trường CORS_ALLOWED_ORIGINS.",
                request.getHeaders().getOrigin(), corsOrigins());
        return false;
    }

    /** Danh sách đang áp dụng, cho dòng nhật ký ở trên. */
    private String corsOrigins() {
        return String.join(", ", originCheck.getAllowedOrigins());
    }

    /**
     * Cái vé, đọc từ chuỗi truy vấn.
     *
     * <p>Trên URL chứ không trên header, vì API {@code WebSocket} của trình
     * duyệt là một hàm dựng nhận một URL và không có chỗ nào đặt header — đúng
     * hạn chế của {@code EventSource}. Lý lẽ về việc vì sao thứ nằm trên URL là
     * một cái vé chứ không phải token phiên nằm ở {@code OneTimeTicketStore}.
     */
    private static String ticketOf(ServerHttpRequest request) {
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
    }
}
