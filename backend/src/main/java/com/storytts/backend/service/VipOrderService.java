package com.storytts.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.storytts.backend.config.PayosProperties;
import com.storytts.backend.domain.*;
import com.storytts.backend.dto.vip.VipOrderDto;
import com.storytts.backend.dto.vip.VipStatusDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.VipOrderRepository;
import com.storytts.backend.service.payment.PayosClient;
import com.storytts.backend.service.payment.PayosSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * Mua VIP: tạo đơn, nhận kết quả từ PayOS, cộng hạn.
 *
 * <h3>Vì sao có hai đường xác nhận</h3>
 * Webhook là đường chính thức, nhưng nó đòi máy chủ phải công khai ra Internet —
 * chạy dưới localhost thì PayOS không gọi vào được. Nên trang kết quả còn có
 * thể tự hỏi lại PayOS ({@link #syncFromGateway}). Cả hai đều đổ về
 * {@link #markPaid}, và hàm đó chỉ cộng hạn đúng một lần cho mỗi đơn, nên gọi
 * trùng bao nhiêu lần cũng không cấp thừa.
 *
 * <h3>Vì sao không tin số tiền do client gửi lên</h3>
 * Client chỉ gửi id gói. Số tháng và số tiền đọc từ cơ sở dữ liệu rồi chép vào
 * đơn; nếu nhận từ client thì ai cũng tự đặt giá 1.000đ cho gói một năm được.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VipOrderService {

    private final VipOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VipPlanService planService;
    private final PayosClient payosClient;
    private final PayosProperties payosProperties;
    private final CurrentUserService currentUserService;

    private final SecureRandom random = new SecureRandom();

    /* ------------------------------------------------------------------ */
    /* Người dùng                                                          */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public VipStatusDto myStatus() {
        return VipStatusDto.from(currentUser(), payosClient.isConfigured());
    }

    @Transactional(readOnly = true)
    public List<VipOrderDto> myOrders() {
        return orderRepository.findTop20ByUserIdOrderByCreatedAtDesc(currentUser().getId())
                .stream().map(VipOrderDto::from).toList();
    }

    /**
     * Tạo đơn và trả về đơn kèm link thanh toán.
     *
     * <p>Đơn được lưu trước khi gọi PayOS, để nếu lệnh gọi hỏng giữa chừng thì
     * vẫn còn dấu vết của lần thử đó thay vì mất hẳn.
     */
    @Transactional
    public VipOrderDto createOrder(Long planId) {
        User user = currentUser();

        if (user.isVipGranted()) {
            throw new BadRequestException(
                    "Tài khoản của bạn đã được cấp VIP vĩnh viễn, không cần mua thêm gói.");
        }

        VipPlan plan = planService.requireActivePlan(planId);

        VipOrder order = orderRepository.save(VipOrder.builder()
                .orderCode(nextOrderCode())
                .user(user)
                .plan(plan)
                .planName(plan.getName())
                .months(plan.getMonths())
                .amountVnd(plan.getPriceVnd())
                .status(VipOrderStatus.PENDING)
                .build());

        // Nội dung chuyển khoản bị PayOS giới hạn 25 ký tự, nên mã đơn là thứ
        // đáng giữ nhất: nó là cái duy nhất tra ngược lại được.
        String description = "VIP " + order.getOrderCode();

        PayosClient.PayosPaymentLink link = payosClient.createPaymentLink(
                order.getOrderCode(),
                order.getAmountVnd(),
                description,
                user.getDisplayName(),
                user.getEmail());

        order.setPaymentLinkId(link.paymentLinkId());
        order.setCheckoutUrl(link.checkoutUrl());
        orderRepository.save(order);

        log.info("Tạo đơn VIP {} cho {} — gói {}, {}đ",
                order.getOrderCode(), user.getUsername(), plan.getName(), plan.getPriceVnd());
        return VipOrderDto.from(order);
    }

    /**
     * Tra cứu một đơn của chính mình, hỏi lại cổng thanh toán nếu đơn còn treo.
     *
     * <p>Đây là thứ trang kết quả gọi sau khi PayOS đưa người dùng quay về.
     */
    @Transactional
    public VipOrderDto checkMyOrder(Long orderCode) {
        VipOrder order = findOrder(orderCode);
        User user = currentUser();

        // Đơn của người khác thì trả về "không tìm thấy": xác nhận sự tồn tại
        // của nó cũng đã là rò rỉ.
        if (!order.getUser().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new ResourceNotFoundException("Không tìm thấy đơn " + orderCode);
        }

        if (order.getStatus() == VipOrderStatus.PENDING) {
            syncFromGateway(order);
        }
        return VipOrderDto.from(order);
    }

    @Transactional
    public VipOrderDto cancelMyOrder(Long orderCode) {
        VipOrder order = findOrder(orderCode);
        User user = currentUser();

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy đơn " + orderCode);
        }
        if (order.getStatus() != VipOrderStatus.PENDING) {
            throw new BadRequestException("Đơn này không còn ở trạng thái chờ thanh toán.");
        }

        // Hủy bên PayOS là việc dọn dẹp, không phải điều kiện: link hết hạn
        // cũng không cản trở việc đóng đơn ở phía mình.
        try {
            payosClient.cancelPaymentLink(orderCode, "Người dùng hủy đơn");
        } catch (RuntimeException ex) {
            log.warn("Không hủy được link PayOS cho đơn {}: {}", orderCode, ex.getMessage());
        }

        order.setStatus(VipOrderStatus.CANCELLED);
        return VipOrderDto.from(orderRepository.save(order));
    }

    /* ------------------------------------------------------------------ */
    /* Webhook                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Xử lý một lần PayOS gọi về.
     *
     * <p>Chữ ký được đối chiếu trước mọi thứ khác — đó là thứ duy nhất phân biệt
     * PayOS với một người bất kỳ biết địa chỉ endpoint này.
     *
     * @return true nếu đã ghi nhận, false nếu bỏ qua (chữ ký sai hoặc đơn lạ)
     */
    @Transactional
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
        return orderRepository.findByOrderCode(orderCode)
                .map(order -> {
                    String code = payload.path("code").asText("");
                    if ("00".equals(code)) {
                        markPaid(order);
                    }
                    return true;
                })
                .orElseGet(() -> {
                    log.info("Webhook PayOS cho đơn không có trong hệ thống: {}", orderCode);
                    return false;
                });
    }

    /* ------------------------------------------------------------------ */
    /* Quản trị                                                            */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public PageResponse<VipOrderDto> listAll(VipOrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(status, pageable), VipOrderDto::from);
    }

    /** Admin bấm đối chiếu lại một đơn treo với cổng thanh toán. */
    @Transactional
    public VipOrderDto refresh(Long orderCode) {
        VipOrder order = findOrder(orderCode);
        if (order.getStatus() == VipOrderStatus.PENDING) {
            syncFromGateway(order);
        }
        return VipOrderDto.from(order);
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /** Hỏi PayOS tình trạng thật của đơn và ghi lại. */
    private void syncFromGateway(VipOrder order) {
        if (!payosClient.isConfigured()) {
            return;
        }

        String status;
        try {
            status = payosClient.fetchStatus(order.getOrderCode());
        } catch (RuntimeException ex) {
            log.warn("Không hỏi được tình trạng đơn {}: {}", order.getOrderCode(), ex.getMessage());
            return;
        }

        switch (status) {
            case "PAID" -> markPaid(order);
            case "CANCELLED" -> order.setStatus(VipOrderStatus.CANCELLED);
            case "EXPIRED" -> order.setStatus(VipOrderStatus.EXPIRED);
            default -> {
                // Vẫn đang chờ; không có gì để ghi.
            }
        }
        orderRepository.save(order);
    }

    /**
     * Ghi nhận tiền về và cộng hạn VIP — đúng một lần cho mỗi đơn.
     *
     * <p>Cả webhook lẫn trang kết quả đều gọi tới đây, và webhook có thể được
     * gửi lại nhiều lần, nên lần gọi thứ hai trở đi phải là lệnh rỗng.
     */
    private void markPaid(VipOrder order) {
        if (order.isPaid()) {
            return;
        }

        User user = order.getUser();
        Instant vipUntil = user.extendVip(order.getMonths());
        userRepository.save(user);

        order.setStatus(VipOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        order.setVipUntilAfter(vipUntil);
        orderRepository.save(order);

        log.info("Đơn {} đã thanh toán — {} được VIP tới {}",
                order.getOrderCode(), user.getUsername(), vipUntil);
    }

    /**
     * Sinh mã đơn cho PayOS.
     *
     * <p>Phải là số nguyên dương và không trùng trong cùng kênh thanh toán. Ghép
     * epoch giây với một hậu tố ngẫu nhiên: vừa không đoán được đơn của người
     * khác, vừa không đụng nhau khi hai người bấm mua cùng lúc.
     */
    private long nextOrderCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            long candidate = Instant.now().getEpochSecond() * 1000L + random.nextInt(1000);
            if (!orderRepository.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        throw new BadRequestException("Không tạo được mã đơn, vui lòng thử lại.");
    }

    private VipOrder findOrder(Long orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + orderCode));
    }

    private User currentUser() {
        Long id = currentUserService.currentUserId()
                .orElseThrow(() -> new BadRequestException("Bạn cần đăng nhập."));
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }
}
