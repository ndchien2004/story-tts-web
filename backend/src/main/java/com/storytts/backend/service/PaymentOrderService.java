package com.storytts.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.storytts.backend.config.PayosProperties;
import com.storytts.backend.domain.PaymentOrderStatus;
import com.storytts.backend.dto.payment.PaymentOrderDto;
import com.storytts.backend.dto.vip.VipStatusDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.PaymentOrderRepository;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.payment.PayosClient;
import com.storytts.backend.service.payment.PayosSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mua VIP: tạo đơn, nhận kết quả từ PayOS, cộng hạn.
 *
 * <h3>Vì sao có hai đường xác nhận</h3>
 * Webhook là đường chính thức, nhưng nó đòi máy chủ phải công khai ra Internet —
 * chạy dưới localhost thì PayOS không gọi vào được. Nên trang kết quả còn có
 * thể tự hỏi lại PayOS ({@link #syncFromGateway}). Cả hai đều đổ về
 * {@link PaymentOrderLedger}, và ở đó hạn VIP chỉ được cộng đúng một lần cho mỗi đơn,
 * nên gọi trùng bao nhiêu lần cũng không cấp thừa.
 *
 * <h3>Vì sao không tin số tiền do client gửi lên</h3>
 * Client chỉ gửi id gói. Số tháng và số tiền đọc từ cơ sở dữ liệu rồi chép vào
 * đơn; nếu nhận từ client thì ai cũng tự đặt giá 1.000đ cho gói một năm được.
 *
 * <h3>Vì sao lớp này gần như không còn {@code @Transactional}</h3>
 * Mỗi thao tác ở đây xen kẽ hai loại việc: ghi cơ sở dữ liệu, và chờ PayOS trả
 * lời qua mạng (hạn chờ 20 giây, chưa kể 10 giây bắt tay). Bọc cả hai trong một
 * giao dịch nghĩa là giữ một kết nối trong pool suốt quãng chờ ấy — với pool mười
 * kết nối, vài người bấm mua cùng lúc là đủ để phần còn lại của trang web phải
 * xếp hàng.
 *
 * <p>Nên lớp này chỉ còn là bên điều phối: nó xét quyền, gọi PayOS, và giao mọi
 * lần chạm cơ sở dữ liệu cho {@link PaymentOrderLedger} — mỗi lần một giao dịch ngắn
 * riêng, mở ra và đóng lại quanh lệnh gọi mạng chứ không bao lấy nó.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderService {

    private final PaymentOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentOrderLedger ledger;
    private final PayosClient payosClient;
    private final PayosProperties payosProperties;
    private final CurrentUserService currentUserService;

    /* ------------------------------------------------------------------ */
    /* Người dùng                                                          */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public VipStatusDto myStatus() {
        return VipStatusDto.from(
                userRepository.findById(currentUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản.")),
                payosClient.isConfigured());
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderDto> myOrders() {
        return orderRepository.findTop20ByUserIdOrderByCreatedAtDesc(currentUserId())
                .stream().map(PaymentOrderDto::from).toList();
    }

    /**
     * Mua một gói VIP.
     *
     * <p>Đơn được lưu và commit <b>trước</b> khi gọi PayOS. Nhờ vậy lệnh gọi hỏng
     * giữa chừng vẫn để lại dấu vết của lần thử đó: một đơn PENDING không có link.
     * Người dùng nhận đúng thông báo lỗi như trước và bấm mua lại; đơn dở dang kia
     * hết hạn theo {@code app.payos.order-timeout-minutes} rồi thôi.
     */
    public PaymentOrderDto createVipOrder(Long planId) {
        return checkout(ledger.openVipOrder(currentUserId(), planId), "VIP");
    }

    /** Nạp Xu. Cùng đường đi, khác mỗi thứ được giao khi tiền về. */
    public PaymentOrderDto createCoinOrder(Long packageId) {
        return checkout(ledger.openCoinOrder(currentUserId(), packageId), "XU");
    }

    /**
     * Xin PayOS một link thanh toán cho đơn vừa mở, rồi gắn nó vào đơn.
     *
     * <p>Lệnh gọi mạng nằm giữa hai giao dịch ngắn, không nằm trong giao dịch nào.
     *
     * @param prefix tiền tố nội dung chuyển khoản, để soi sao kê biết đơn thuộc loại nào
     */
    private PaymentOrderDto checkout(PaymentOrderLedger.Draft draft, String prefix) {
        // Nội dung chuyển khoản bị PayOS giới hạn 25 ký tự, nên mã đơn là thứ
        // đáng giữ nhất: nó là cái duy nhất tra ngược lại được.
        String description = prefix + " " + draft.orderCode();

        // Ngoài mọi giao dịch — xem ghi chú ở đầu lớp.
        PayosClient.PayosPaymentLink link = payosClient.createPaymentLink(
                draft.orderCode(),
                draft.amountVnd(),
                description,
                draft.buyerName(),
                draft.buyerEmail());

        return ledger.attachPaymentLink(draft.orderCode(), link.paymentLinkId(), link.checkoutUrl());
    }

    /**
     * Tra cứu một đơn của chính mình, hỏi lại cổng thanh toán nếu đơn còn treo.
     *
     * <p>Đây là thứ trang kết quả gọi sau khi PayOS đưa người dùng quay về.
     */
    public PaymentOrderDto checkMyOrder(Long orderCode) {
        AppUserPrincipal principal = requirePrincipal();
        PaymentOrderLedger.OrderFacts facts = ledger.facts(orderCode);

        // Đơn của người khác thì trả về "không tìm thấy": xác nhận sự tồn tại
        // của nó cũng đã là rò rỉ.
        if (!facts.ownerId().equals(principal.getId()) && !principal.isAdmin()) {
            throw new ResourceNotFoundException("Không tìm thấy đơn " + orderCode);
        }

        return syncFromGateway(orderCode, facts.status());
    }

    public PaymentOrderDto cancelMyOrder(Long orderCode) {
        AppUserPrincipal principal = requirePrincipal();
        PaymentOrderLedger.OrderFacts facts = ledger.facts(orderCode);

        if (!facts.ownerId().equals(principal.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy đơn " + orderCode);
        }
        if (facts.status() != PaymentOrderStatus.PENDING) {
            throw new BadRequestException("Đơn này không còn ở trạng thái chờ thanh toán.");
        }

        // Hủy bên PayOS là việc dọn dẹp, không phải điều kiện: link hết hạn
        // cũng không cản trở việc đóng đơn ở phía mình.
        try {
            payosClient.cancelPaymentLink(orderCode, "Người dùng hủy đơn");
        } catch (RuntimeException ex) {
            log.warn("Không hủy được link PayOS cho đơn {}: {}", orderCode, ex.getMessage());
        }

        return ledger.cancel(orderCode);
    }

    /* ------------------------------------------------------------------ */
    /* Webhook                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Xử lý một lần PayOS gọi về.
     *
     * <p>Chữ ký được đối chiếu trước mọi thứ khác — đó là thứ duy nhất phân biệt
     * PayOS với một người bất kỳ biết địa chỉ endpoint này. Việc đối chiếu là
     * phép băm thuần túy, không chạm cơ sở dữ liệu, nên nó nằm ngoài giao dịch:
     * một lần gọi giả mạo bị loại mà không tốn kết nối nào.
     *
     * @return true nếu đã ghi nhận, false nếu bỏ qua (chữ ký sai hoặc đơn lạ)
     */
    public boolean handleWebhook(JsonNode payload) {
        JsonNode data = payload.path("data");
        if (data.isMissingNode() || data.isNull()) {
            log.warn("Webhook PayOS không có khối data");
            return false;
        }

        String expected = PayosSignature.forWebhookData(payosProperties.checksumKey(), data);
        if (!PayosSignature.matches(expected, payload.path("signature").asText(null))) {
            log.warn("Webhook PayOS sai chữ ký — bỏ qua");
            return false;
        }

        long orderCode = data.path("orderCode").asLong(0);
        if (orderCode == 0) {
            return false;
        }

        // PayOS gửi một lần gọi thử với orderCode 123 khi đăng ký webhook.
        boolean known = ledger.recordWebhook(orderCode, "00".equals(payload.path("code").asText("")));
        if (!known) {
            log.info("Webhook PayOS cho đơn không có trong hệ thống: {}", orderCode);
        }
        return known;
    }

    /* ------------------------------------------------------------------ */
    /* Quản trị                                                            */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderDto> listAll(PaymentOrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(status, pageable), PaymentOrderDto::from);
    }

    /** Admin bấm đối chiếu lại một đơn treo với cổng thanh toán. */
    public PaymentOrderDto refresh(Long orderCode) {
        return syncFromGateway(orderCode, ledger.facts(orderCode).status());
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Hỏi PayOS tình trạng thật của một đơn còn treo và ghi lại.
     *
     * <p>Lệnh gọi mạng nằm giữa hai giao dịch ngắn chứ không nằm trong giao dịch
     * nào: đọc trạng thái, thả kết nối, hỏi PayOS, rồi mới lấy kết nối để ghi.
     *
     * @param known trạng thái vừa đọc được; chỉ đơn còn PENDING mới đáng hỏi lại
     */
    private PaymentOrderDto syncFromGateway(Long orderCode, PaymentOrderStatus known) {
        if (known != PaymentOrderStatus.PENDING || !payosClient.isConfigured()) {
            return ledger.view(orderCode);
        }

        String status;
        try {
            status = payosClient.fetchStatus(orderCode);
        } catch (RuntimeException ex) {
            log.warn("Không hỏi được tình trạng đơn {}: {}", orderCode, ex.getMessage());
            return ledger.view(orderCode);
        }

        return ledger.applyGatewayStatus(orderCode, status);
    }

    private AppUserPrincipal requirePrincipal() {
        return currentUserService.currentPrincipal()
                .orElseThrow(() -> new BadRequestException("Bạn cần đăng nhập."));
    }

    /**
     * Id người đang gọi, lấy từ token chứ không phải từ bảng users.
     *
     * <p>Không cần một câu SELECT chỉ để biết mình là ai, và cũng không cần một
     * kết nối cho việc đó — {@link PaymentOrderLedger} tự đọc người dùng bên trong
     * giao dịch của nó khi thật sự cần tới.
     */
    private Long currentUserId() {
        return requirePrincipal().getId();
    }
}
