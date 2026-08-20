package com.storytts.backend.service;

import com.storytts.backend.config.PayosProperties;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.PaymentOrder;
import com.storytts.backend.domain.PaymentOrderStatus;
import com.storytts.backend.domain.VipPlan;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.PaymentOrderRepository;
import com.storytts.backend.security.AppUserPrincipal;
import com.storytts.backend.service.payment.PayosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử ranh giới giao dịch và tính đúng một lần của luồng mua VIP.
 *
 * <p>Hai điều đang được giữ ở đây:
 *
 * <ol>
 *   <li><b>Lệnh gọi PayOS nằm ngoài giao dịch.</b> Nó phải rơi vào khoảng giữa hai
 *       lần gọi {@link PaymentOrderLedger} — mỗi lần gọi ấy là một giao dịch ngắn — chứ
 *       không nằm gọn trong một. PayOS có hạn chờ 20 giây, và một giao dịch bao
 *       quanh nó là một kết nối trong pool đứng im chừng ấy lâu.</li>
 *   <li><b>Hạn VIP chỉ được cộng một lần.</b> Webhook và trang kết quả là hai
 *       đường độc lập cùng dẫn tới lượt cộng, nên việc giành quyền cộng phải là
 *       một câu UPDATE có điều kiện chứ không phải một phép đọc rồi ghi.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentOrderBoundaryTest {

    private static final Long USER_ID = 9L;
    private static final Long ORDER_CODE = 1_700_000_000_123L;

    /* ------------------------------------------------------------------ */
    /* Bên điều phối: PayOS ở giữa hai giao dịch                           */
    /* ------------------------------------------------------------------ */

    @Mock
    private PaymentOrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentOrderLedger ledger;
    @Mock
    private PayosClient payosClient;
    @Mock
    private PayosProperties payosProperties;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private VipPlanService planService;
    @Mock
    private CoinPackageService coinPackageService;
    @Mock
    private WalletService walletService;
    @Mock
    private com.storytts.backend.service.notification.NotificationService notificationService;

    private PaymentOrderService service;
    private PaymentOrderLedger realLedger;

    @BeforeEach
    void setUp() {
        service = new PaymentOrderService(orderRepository, userRepository, ledger,
                payosClient, payosProperties, currentUserService);
        realLedger = new PaymentOrderLedger(orderRepository, userRepository, planService,
                coinPackageService, walletService, notificationService);

        when(currentUserService.currentPrincipal())
                .thenReturn(Optional.of(new AppUserPrincipal(member())));
        when(payosClient.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("tạo đơn: đơn được lưu và commit trước khi gọi PayOS")
    void orderIsCommittedBeforeTheGatewayCall() {
        when(ledger.openVipOrder(USER_ID, 3L))
                .thenReturn(new PaymentOrderLedger.Draft(ORDER_CODE, 99_000L, "Người Mua", "mua@test.local"));
        when(payosClient.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new PayosClient.PayosPaymentLink("link-1", "https://pay.test/1", "PENDING"));

        service.createVipOrder(3L);

        InOrder order = inOrder(ledger, payosClient);
        order.verify(ledger).openVipOrder(USER_ID, 3L);       // giao dịch 1, đã đóng
        order.verify(payosClient).createPaymentLink(
                eq(ORDER_CODE), eq(99_000L), anyString(), anyString(), anyString());
        order.verify(ledger).attachPaymentLink(ORDER_CODE, "link-1", "https://pay.test/1");  // giao dịch 2
    }

    @Test
    @DisplayName("PayOS hỏng: lỗi được ném lên, và đơn vừa lưu ở lại làm dấu vết")
    void aFailedGatewayCallLeavesTheOrderBehind() {
        when(ledger.openVipOrder(USER_ID, 3L))
                .thenReturn(new PaymentOrderLedger.Draft(ORDER_CODE, 99_000L, "Người Mua", "mua@test.local"));
        when(payosClient.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new BadRequestException("Không kết nối được tới cổng thanh toán."));

        assertThatThrownBy(() -> service.createVipOrder(3L))
                .isInstanceOf(BadRequestException.class);

        // Không có giao dịch nào bao lệnh gọi PayOS, nên lần lưu ở trên đã commit
        // và không bị cuộn ngược theo.
        verify(ledger).openVipOrder(USER_ID, 3L);
        verify(ledger, never()).attachPaymentLink(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("đơn đã xong thì không hỏi lại PayOS")
    void aSettledOrderIsNotResynced() {
        when(ledger.facts(ORDER_CODE))
                .thenReturn(new PaymentOrderLedger.OrderFacts(USER_ID, PaymentOrderStatus.PAID));

        service.checkMyOrder(ORDER_CODE);

        verify(payosClient, never()).fetchStatus(anyLong());
        verify(ledger).view(ORDER_CODE);
    }

    @Test
    @DisplayName("đơn của người khác: trả về không tìm thấy, và không hỏi PayOS")
    void anotherUsersOrderIsNotFound() {
        when(ledger.facts(ORDER_CODE))
                .thenReturn(new PaymentOrderLedger.OrderFacts(404L, PaymentOrderStatus.PENDING));

        assertThatThrownBy(() -> service.checkMyOrder(ORDER_CODE))
                .hasMessageContaining("Không tìm thấy đơn");

        verify(payosClient, never()).fetchStatus(anyLong());
    }

    /* ------------------------------------------------------------------ */
    /* Sổ đơn: hạn VIP chỉ cộng một lần                                    */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("cộng hạn VIP khi giành được quyền ghi")
    void creditsVipWhenItWinsTheUpdate() {
        User buyer = member();
        PaymentOrder order = pendingOrder(buyer, 3);
        when(orderRepository.findByOrderCode(ORDER_CODE)).thenReturn(Optional.of(order));
        when(orderRepository.markPaidIfPending(eq(order.getId()), any(Instant.class))).thenReturn(1);

        assertThat(realLedger.recordWebhook(ORDER_CODE, true)).isTrue();

        verify(userRepository).save(buyer);
        assertThat(buyer.getVipUntil()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getVipUntilAfter()).isEqualTo(buyer.getVipUntil());
    }

    @Test
    @DisplayName("thua cuộc giành quyền ghi: không cộng hạn lần thứ hai")
    void doesNotCreditTwiceWhenAnotherThreadWon() {
        User buyer = member();
        PaymentOrder order = pendingOrder(buyer, 3);
        when(orderRepository.findByOrderCode(ORDER_CODE)).thenReturn(Optional.of(order));
        // Luồng kia đã chuyển đơn sang PAID trước, nên câu UPDATE này sửa 0 dòng.
        when(orderRepository.markPaidIfPending(eq(order.getId()), any(Instant.class))).thenReturn(0);

        assertThat(realLedger.recordWebhook(ORDER_CODE, true)).isTrue();

        verify(userRepository, never()).save(any(User.class));
        assertThat(buyer.getVipUntil()).isNull();
    }

    @Test
    @DisplayName("webhook cho đơn lạ: không ghi gì và báo lại là không biết đơn ấy")
    void anUnknownOrderIsReportedBack() {
        when(orderRepository.findByOrderCode(ORDER_CODE)).thenReturn(Optional.empty());

        assertThat(realLedger.recordWebhook(ORDER_CODE, true)).isFalse();

        verify(orderRepository, never()).markPaidIfPending(anyLong(), any(Instant.class));
        verify(userRepository, never()).save(any(User.class));
    }

    /* ------------------------------------------------------------------ */

    private User member() {
        return User.builder()
                .id(USER_ID)
                .username("nguoimua")
                .email("mua@test.local")
                .passwordHash("hash")
                .displayName("Người Mua")
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build();
    }

    private PaymentOrder pendingOrder(User buyer, int months) {
        VipPlan plan = VipPlan.builder().id(3L).name("Gói %d tháng".formatted(months))
                .months(months).priceVnd(99_000L).active(true).build();
        return PaymentOrder.builder()
                .id(11L)
                .orderCode(ORDER_CODE)
                .kind(com.storytts.backend.domain.PaymentOrderKind.VIP_PLAN)
                .user(buyer)
                .plan(plan)
                .itemName(plan.getName())
                .months(months)
                .amountVnd(plan.getPriceVnd())
                .status(PaymentOrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }
}
