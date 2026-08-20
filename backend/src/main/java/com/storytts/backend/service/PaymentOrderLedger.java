package com.storytts.backend.service;

import com.storytts.backend.domain.CoinPackage;
import com.storytts.backend.domain.NotificationAction;
import com.storytts.backend.domain.NotificationEntityType;
import com.storytts.backend.domain.NotificationPriority;
import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.PaymentOrder;
import com.storytts.backend.domain.PaymentOrderKind;
import com.storytts.backend.domain.PaymentOrderStatus;
import com.storytts.backend.domain.VipPlan;
import com.storytts.backend.domain.WalletReferenceType;
import com.storytts.backend.domain.WalletTransactionType;
import com.storytts.backend.dto.payment.PaymentOrderDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.PaymentOrderRepository;
import com.storytts.backend.service.notification.NotificationDraft;
import com.storytts.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Sổ đơn thanh toán: mọi lần ghi vào {@code payment_orders} đi qua đây, mỗi lần
 * một giao dịch ngắn.
 *
 * <h3>Vì sao tách khỏi {@link PaymentOrderService}</h3>
 * Một lượt mua xen kẽ hai loại việc: ghi cơ sở dữ liệu, và gọi PayOS qua mạng.
 * Trước đây cả hai nằm chung trong một {@code @Transactional}, nên kết nối cơ sở
 * dữ liệu bị giữ suốt lúc chờ PayOS trả lời — mà lệnh gọi ấy có hạn chờ 20 giây,
 * chưa kể 10 giây bắt tay. Với mười kết nối trong pool, vài người bấm mua cùng
 * lúc là đủ để phần còn lại của trang web đứng chờ kết nối.
 *
 * <p>Giờ {@code PaymentOrderService} là bên điều phối và <b>không mở giao dịch nào</b>;
 * nó gọi PayOS ở giữa hai lần gọi vào lớp này. Phải là một bean riêng chứ không
 * phải mấy method cùng lớp: {@code @Transactional} của Spring chạy bằng proxy, và
 * method tự gọi method trong cùng bean thì không đi qua proxy.
 *
 * <h3>Hai thứ bán được, một đường tiền</h3>
 * Gói VIP và gói Xu khác nhau đúng một chỗ: việc gì xảy ra khi tiền về. Phần tạo
 * đơn, hỏi lại cổng, hủy đơn, và — quan trọng nhất — cơ chế chống cộng hai lần
 * đều dùng chung. Xem {@link #fulfil}.
 *
 * <p>Lớp này không biết gì về PayOS, và cũng không quyết định ai được xem đơn nào —
 * đó là việc của {@code PaymentOrderService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderLedger {

    private final PaymentOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VipPlanService planService;
    private final CoinPackageService coinPackageService;
    private final WalletService walletService;
    private final NotificationService notificationService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Đơn vừa mở, kèm những gì lệnh gọi PayOS tiếp theo cần.
     *
     * <p>Chép ra thành record chứ không trả về entity: entity rời khỏi giao dịch
     * là entity detached, và đọc một trường lazy của nó ở ngoài sẽ ném ngoại lệ.
     */
    public record Draft(Long orderCode, long amountVnd, String buyerName, String buyerEmail) {
    }

    /* ------------------------------------------------------------------ */
    /* Đọc                                                                 */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public PaymentOrderDto view(Long orderCode) {
        return PaymentOrderDto.from(require(orderCode));
    }

    /** Chủ đơn và trạng thái hiện tại — đủ để bên gọi xét quyền và quyết định có hỏi PayOS không. */
    @Transactional(readOnly = true)
    public OrderFacts facts(Long orderCode) {
        PaymentOrder order = require(orderCode);
        return new OrderFacts(order.getUser().getId(), order.getStatus());
    }

    /** Phần của một đơn mà bên ngoài cần biết trước khi chạm tới cổng thanh toán. */
    public record OrderFacts(Long ownerId, PaymentOrderStatus status) {
    }

    /* ------------------------------------------------------------------ */
    /* Ghi                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Mở một đơn ở trạng thái chờ thanh toán, trước khi có link.
     *
     * <p>Lưu trước rồi mới gọi PayOS, đúng như ghi chú ở {@code PaymentOrderService}
     * vẫn nói: lệnh gọi hỏng giữa chừng thì còn lại dấu vết của lần thử đó. Trước
     * khi tách giao dịch, câu ấy chưa đúng — PayOS hỏng là cả giao dịch cuộn
     * ngược và đơn biến mất cùng nó.
     */
    @Transactional
    public Draft openVipOrder(Long userId, Long planId) {
        User user = requireUser(userId);

        if (user.isVipGranted()) {
            throw new BadRequestException(
                    "Tài khoản của bạn đã được cấp VIP vĩnh viễn, không cần mua thêm gói.");
        }

        VipPlan plan = planService.requireActivePlan(planId);

        PaymentOrder order = orderRepository.save(PaymentOrder.builder()
                .orderCode(nextOrderCode())
                .user(user)
                .kind(PaymentOrderKind.VIP_PLAN)
                .plan(plan)
                .itemName(plan.getName())
                .months(plan.getMonths())
                .amountVnd(plan.getPriceVnd())
                .status(PaymentOrderStatus.PENDING)
                .build());

        log.info("Tạo đơn VIP {} cho {} — gói {}, {}đ",
                order.getOrderCode(), user.getUsername(), plan.getName(), plan.getPriceVnd());

        return draftOf(order, user);
    }

    /**
     * Mở một đơn nạp Xu ở trạng thái chờ thanh toán.
     *
     * <p>Số Xu được chép vào đơn ngay lúc này, không đợi tới lúc tiền về: gói đổi
     * tỉ lệ quy đổi trong lúc người mua còn đang ở màn hình thanh toán không được
     * phép làm họ nhận về ít hơn con số đã thấy khi bấm mua.
     */
    @Transactional
    public Draft openCoinOrder(Long userId, Long packageId) {
        User user = requireUser(userId);
        CoinPackage pack = coinPackageService.requireActivePackage(packageId);

        PaymentOrder order = orderRepository.save(PaymentOrder.builder()
                .orderCode(nextOrderCode())
                .user(user)
                .kind(PaymentOrderKind.COIN_PACKAGE)
                .coinPackage(pack)
                .itemName(pack.getName())
                .coinsGranted(pack.totalCoins())
                .amountVnd(pack.getPriceVnd())
                .status(PaymentOrderStatus.PENDING)
                .build());

        log.info("Tạo đơn nạp Xu {} cho {} — gói {}, {}đ đổi {} Xu",
                order.getOrderCode(), user.getUsername(), pack.getName(),
                pack.getPriceVnd(), pack.totalCoins());

        return draftOf(order, user);
    }

    private Draft draftOf(PaymentOrder order, User user) {
        return new Draft(order.getOrderCode(), order.getAmountVnd(),
                user.getDisplayName(), user.getEmail());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }

    /** Gắn link thanh toán PayOS vừa cấp vào đơn. */
    @Transactional
    public PaymentOrderDto attachPaymentLink(Long orderCode, String paymentLinkId, String checkoutUrl) {
        PaymentOrder order = require(orderCode);
        order.setPaymentLinkId(paymentLinkId);
        order.setCheckoutUrl(checkoutUrl);
        return PaymentOrderDto.from(orderRepository.save(order));
    }

    /**
     * Ghi lại tình trạng PayOS vừa báo.
     *
     * @param gatewayStatus chuỗi trạng thái của PayOS; giá trị lạ thì không ghi gì
     */
    @Transactional
    public PaymentOrderDto applyGatewayStatus(Long orderCode, String gatewayStatus) {
        PaymentOrder order = require(orderCode);

        switch (gatewayStatus) {
            case "PAID" -> fulfil(order);
            case "CANCELLED" -> order.setStatus(PaymentOrderStatus.CANCELLED);
            case "EXPIRED" -> order.setStatus(PaymentOrderStatus.EXPIRED);
            default -> {
                // Vẫn đang chờ; không có gì để ghi.
            }
        }
        return PaymentOrderDto.from(orderRepository.save(order));
    }

    /** Đóng đơn theo yêu cầu của người mua. Đơn không còn chờ thì đây là lỗi. */
    @Transactional
    public PaymentOrderDto cancel(Long orderCode) {
        PaymentOrder order = require(orderCode);
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            throw new BadRequestException("Đơn này không còn ở trạng thái chờ thanh toán.");
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        return PaymentOrderDto.from(orderRepository.save(order));
    }

    /**
     * Ghi nhận một lần PayOS gọi về. Gọi trùng bao nhiêu lần cũng chỉ cộng hạn một lần.
     *
     * @param paid lệnh gọi này báo tiền đã về hay chỉ là một thông báo khác
     * @return false nếu mã đơn không có trong hệ thống
     */
    @Transactional
    public boolean recordWebhook(Long orderCode, boolean paid) {
        return orderRepository.findByOrderCode(orderCode)
                .map(order -> {
                    if (paid) {
                        fulfil(order);
                    }
                    return true;
                })
                .orElse(false);
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Ghi nhận tiền về và giao hàng — đúng một lần cho mỗi đơn.
     *
     * <h3>Vì sao việc giành quyền là một câu UPDATE</h3>
     * Webhook và trang kết quả đều đổ về đây, và chúng chạy được đồng thời: PayOS
     * gọi webhook đúng lúc người mua bấm quay lại là hai luồng cùng đọc một đơn
     * PENDING. Đọc-rồi-ghi bằng Java không chặn được điều đó — cả hai đều thấy
     * PENDING và cả hai đều giao hàng.
     *
     * <p>Nên việc giành quyền là một câu UPDATE có điều kiện: MySQL khóa dòng ấy
     * cho tới hết giao dịch này, luồng kia phải chờ rồi thấy PAID và nhận về 0
     * dòng. Không cần thêm khóa nào, và không có lượt giao thừa.
     *
     * <p>Cả CAS lẫn phần giao hàng nằm trong cùng một giao dịch, nên không có
     * trạng thái giữa chừng: hoặc đơn PAID và hàng đã giao, hoặc chưa gì cả.
     *
     * <h3>Vì sao cả VIP lẫn Xu dùng chung chỗ này</h3>
     * Đây chính là lý do hai loại đơn nằm chung một bảng. Cơ chế trên tinh vi vừa
     * đủ để viết sai; có hai bản sao của nó là có một bản sai. Ở đây chỉ có một
     * bản, và {@link PaymentOrderKind} quyết định giao cái gì.
     */
    private void fulfil(PaymentOrder order) {
        int claimed = orderRepository.markPaidIfPending(order.getId(), Instant.now());
        if (claimed == 0) {
            return;
        }

        // Câu UPDATE ở trên đi thẳng xuống cơ sở dữ liệu, nên bản trong bộ nhớ
        // phải được kéo theo cho khớp — DTO dựng ngay sau đây đọc từ nó.
        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(Instant.now());

        switch (order.getKind()) {
            case VIP_PLAN -> grantVip(order);
            case COIN_PACKAGE -> grantCoins(order);
        }

        orderRepository.save(order);
    }

    /** Cộng số tháng vừa mua vào hạn VIP. */
    private void grantVip(PaymentOrder order) {
        User user = order.getUser();
        Instant vipUntil = user.extendVip(order.getMonths());
        userRepository.save(user);
        order.setVipUntilAfter(vipUntil);

        log.info("Đơn {} đã thanh toán — {} được VIP tới {}",
                order.getOrderCode(), user.getUsername(), vipUntil);

        // Hạn lấy từ chính phép cộng vừa chạy ở trên, không phải từ một con số
        // trình duyệt gửi lên: đây là thứ người mua sẽ đọc để biết mình đã mua
        // được gì, nên nó phải đến từ cùng chỗ với sự thật.
        notify(order, NotificationType.VIP_GRANTED, NotificationAction.VIEW_VIP,
                "Chúc mừng, bạn đã là thành viên VIP",
                "Đơn “%s” đã thanh toán thành công. Quyền VIP của bạn có hiệu lực tới %s."
                        .formatted(order.getItemName(), formatExpiry(vipUntil)),
                draft -> draft
                        .meta("vipUntil", vipUntil.toString())
                        .meta("months", order.getMonths()));
    }

    /**
     * Cộng Xu vào ví.
     *
     * <p>Số Xu lấy từ chính đơn, không hỏi lại gói: gói có thể đã đổi giá hoặc bị
     * tắt trong lúc người mua còn ở màn hình thanh toán, và điều đã hứa lúc bấm
     * mua mới là điều phải giao.
     */
    private void grantCoins(PaymentOrder order) {
        User user = order.getUser();
        long coins = order.getCoinsGranted() == null ? 0L : order.getCoinsGranted();
        if (coins <= 0) {
            log.error("Đơn nạp Xu {} không ghi số Xu nào — không cộng gì", order.getOrderCode());
            return;
        }

        long balance = walletService.credit(
                user.getId(), coins,
                WalletTransactionType.DEPOSIT,
                WalletReferenceType.PAYMENT_ORDER, order.getId(),
                "Nạp Xu: " + order.getItemName());

        log.info("Đơn {} đã thanh toán — {} nhận {} Xu, số dư {}",
                order.getOrderCode(), user.getUsername(), coins, balance);

        // Cố ý không nhắc số dư trong câu chữ: nó đổi sau mỗi lần mở khóa một
        // chương, nên một con số chép cứng vào hộp thư sẽ sai ngay hôm sau và
        // trông như hệ thống đang mâu thuẫn với chính nó. Số Xu *của lần nạp
        // này* thì không bao giờ đổi, nên nó nói được.
        notify(order, NotificationType.PAYMENT, NotificationAction.VIEW_WALLET,
                "Nạp Xu thành công",
                "Đơn “%s” đã thanh toán thành công. %d Xu đã được cộng vào ví của bạn."
                        .formatted(order.getItemName(), coins),
                draft -> draft.meta("coins", coins));
    }

    /**
     * Ghi một thông báo cho chủ đơn, trong cùng giao dịch với việc giao hàng.
     *
     * <h3>Vì sao {@code eventId} là id đơn</h3>
     * PayOS gọi webhook nhiều lần cho cùng một đơn, và trang kết quả cũng đổ về
     * đây. Câu UPDATE có điều kiện ở {@link #fulfil} đã giữ cho hàng chỉ được
     * giao một lần; khóa này giữ cho lời báo cũng vậy, và nó làm điều đó bằng
     * một ràng buộc của cơ sở dữ liệu chứ không bằng việc tin rằng nhánh trên
     * luôn đúng.
     */
    private void notify(PaymentOrder order, NotificationType type, NotificationAction action,
                        String title, String message,
                        java.util.function.UnaryOperator<NotificationDraft> extras) {
        NotificationDraft draft = NotificationDraft.to(order.getUser().getId())
                .type(type)
                .priority(NotificationPriority.IMPORTANT)
                .title(title)
                .message(message)
                .action(action)
                .about(NotificationEntityType.PAYMENT_ORDER, order.getId())
                .meta("orderCode", order.getOrderCode())
                .meta("amountVnd", order.getAmountVnd())
                .event("payment:" + order.getId());

        notificationService.notify(extras.apply(draft).build());
    }

    /**
     * Hạn VIP thành câu chữ người đọc hiểu được.
     *
     * <p>Đổi sang giờ Việt Nam ở đây chứ không để nguyên {@code Instant}: câu này
     * đi thẳng vào một cột {@code varchar} và sẽ không được dựng lại lần nào nữa,
     * nên nó phải đúng ngay lúc viết. Con số máy đọc thì vẫn có, ở {@code metadata}.
     */
    private static String formatExpiry(Instant vipUntil) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(vipUntil);
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

    private PaymentOrder require(Long orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + orderCode));
    }
}
