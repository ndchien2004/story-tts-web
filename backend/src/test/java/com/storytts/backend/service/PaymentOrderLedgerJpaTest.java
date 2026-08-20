package com.storytts.backend.service;

import com.storytts.backend.domain.NotificationType;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.PaymentOrder;
import com.storytts.backend.domain.PaymentOrderKind;
import com.storytts.backend.domain.PaymentOrderStatus;
import com.storytts.backend.domain.VipPlan;
import com.storytts.backend.repository.NotificationRepository;
import com.storytts.backend.repository.UserRepository;
import com.storytts.backend.repository.PaymentOrderRepository;
import com.storytts.backend.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sổ đơn VIP chạy trên một cơ sở dữ liệu thật (H2), không phải trên mock.
 *
 * <p>Có bài này vì mock không thấy được thứ hỏng ở đây. {@code PaymentOrder.user} là
 * liên kết LAZY, nên trong đời thật nó là một proxy chưa nạp; khi câu UPDATE giành
 * quyền cộng hạn dọn sạch persistence context, proxy ấy bị tách khỏi phiên và lần
 * chạm kế tiếp ném {@code LazyInitializationException} — giữa đúng lúc cộng tiền.
 * Entity dựng bằng builder trong bài test mock không có proxy nào, nên nó đi qua
 * lỗi ấy mà không hay biết.
 *
 * <p>Bài này cũng là chỗ kiểm rằng câu UPDATE có điều kiện thật sự chỉ ăn một lần
 * trên một cơ sở dữ liệu biết đếm số dòng đã sửa.
 */
@DataJpaTest
@Import({PaymentOrderLedger.class, WalletService.class, NotificationService.class})
class PaymentOrderLedgerJpaTest {

    @Autowired
    private PaymentOrderLedger ledger;
    @Autowired
    private PaymentOrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private WalletService walletService;
    @Autowired
    private NotificationRepository notificationRepository;

    /** Chỉ đường mở đơn cần tới, và bài này dựng sẵn đơn chứ không đi qua đó. */
    @MockitoBean
    private VipPlanService planService;
    @MockitoBean
    private CoinPackageService coinPackageService;

    private Long orderCode;
    private Long buyerId;

    @BeforeEach
    void setUp() {
        User buyer = userRepository.save(User.builder()
                .username("nguoimua")
                .email("mua@test.local")
                .passwordHash("hash")
                .displayName("Người Mua")
                .role(Role.MEMBER)
                .vipGranted(false)
                .enabled(true)
                .build());
        buyerId = buyer.getId();

        orderCode = Instant.now().getEpochSecond() * 1000L + 7;
        orderRepository.save(PaymentOrder.builder()
                .orderCode(orderCode)
                .kind(com.storytts.backend.domain.PaymentOrderKind.VIP_PLAN)
                .user(buyer)
                .itemName("Gói 3 tháng")
                .months(3)
                .amountVnd(99_000L)
                .status(PaymentOrderStatus.PENDING)
                .build());

        // Dọn context trước khi giao cho sổ đơn. Không có bước này thì đơn đọc ra
        // vẫn là chính đối tượng vừa lưu, với {@code user} là một User thật —
        // trong khi ở máy chủ đang chạy, đơn luôn được nạp mới và {@code user} là
        // một proxy chưa mở. Đúng cái proxy ấy mới là chỗ hỏng.
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("cộng hạn VIP qua một liên kết LAZY, sau khi câu UPDATE đã dọn context")
    void creditsVipThroughALazyAssociation() {
        assertThat(ledger.recordWebhook(orderCode, true)).isTrue();

        assertThat(orderRepository.findByOrderCode(orderCode))
                .get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
                    assertThat(order.getPaidAt()).isNotNull();
                    assertThat(order.getVipUntilAfter()).isNotNull();
                });

        assertThat(userRepository.findById(buyerId)).get()
                .satisfies(user -> assertThat(user.getVipUntil()).isNotNull());
    }

    @Test
    @DisplayName("gọi lại webhook cho cùng một đơn không cộng thêm tháng nào")
    void aRepeatedWebhookAddsNothing() {
        ledger.recordWebhook(orderCode, true);
        Instant afterFirst = vipUntilFromDatabase();

        ledger.recordWebhook(orderCode, true);
        ledger.recordWebhook(orderCode, true);

        assertThat(vipUntilFromDatabase()).isEqualTo(afterFirst);
    }

    /**
     * Hạn VIP đọc thẳng từ cơ sở dữ liệu.
     *
     * <p>Qua đĩa chứ không đọc bản trong bộ nhớ: {@code Instant} trong bộ nhớ có
     * độ chính xác tới nano giây, còn cột {@code datetime(6)} chỉ giữ tới micro
     * giây, nên so một bên vừa ghi với một bên vừa đọc sẽ lệch ở những chữ số
     * không ai quan tâm. Cho cả hai lần đo đi cùng một đường thì phép so sánh nói
     * đúng điều đang cần hỏi: hạn có bị cộng thêm lần nữa không.
     */
    private Instant vipUntilFromDatabase() {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(buyerId).orElseThrow().getVipUntil();
    }

    @Test
    @DisplayName("câu UPDATE có điều kiện chỉ ăn đúng một lần")
    void theConditionalUpdateOnlyLandsOnce() {
        Long id = orderRepository.findByOrderCode(orderCode).orElseThrow().getId();

        assertThat(orderRepository.markPaidIfPending(id, Instant.now())).isEqualTo(1);
        assertThat(orderRepository.markPaidIfPending(id, Instant.now())).isZero();
    }

    @Test
    @DisplayName("đơn bị hủy: ghi trạng thái mà không chạm tới hạn VIP")
    void cancellingLeavesVipAlone() {
        assertThat(ledger.cancel(orderCode).status()).isEqualTo(PaymentOrderStatus.CANCELLED.name());
        assertThat(userRepository.findById(buyerId).orElseThrow().getVipUntil()).isNull();
    }

    /* ------------------------------------------------------------------ */
    /* Đơn nạp Xu — cùng đường tiền, khác thứ được giao                    */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("đơn nạp Xu: tiền về thì Xu vào ví và sổ cái có dòng tương ứng")
    void aPaidCoinOrderCreditsTheWallet() {
        Long coinOrderCode = openCoinOrder(550L);

        assertThat(ledger.recordWebhook(coinOrderCode, true)).isTrue();

        entityManager.flush();
        entityManager.clear();
        assertThat(walletService.balanceOf(buyerId)).isEqualTo(550L);
        // Bất biến của cả hệ thống: tổng sổ cái bằng số dư.
        assertThat(walletService.ledgerTotalOf(buyerId)).isEqualTo(550L);
    }

    @Test
    @DisplayName("webhook gửi lại cho đơn nạp Xu không cộng Xu lần thứ hai")
    void aRepeatedCoinWebhookCreditsOnlyOnce() {
        Long coinOrderCode = openCoinOrder(550L);

        ledger.recordWebhook(coinOrderCode, true);
        ledger.recordWebhook(coinOrderCode, true);
        ledger.recordWebhook(coinOrderCode, true);

        entityManager.flush();
        entityManager.clear();
        assertThat(walletService.balanceOf(buyerId)).isEqualTo(550L);
        assertThat(walletService.ledgerTotalOf(buyerId)).isEqualTo(550L);
    }

    @Test
    @DisplayName("đơn nạp Xu chưa trả tiền thì ví vẫn trống")
    void anUnpaidCoinOrderCreditsNothing() {
        Long coinOrderCode = openCoinOrder(550L);

        // PayOS báo một trạng thái khác "đã trả".
        ledger.recordWebhook(coinOrderCode, false);

        entityManager.flush();
        entityManager.clear();
        assertThat(walletService.balanceOf(buyerId)).isZero();
    }

    /* ------------------------------------------------------------------ */
    /* Hộp thư của người mua                                               */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("đơn VIP thanh toán xong: một lời chúc mừng kèm hạn thật từ máy chủ")
    void aPaidVipOrderCongratulates() {
        ledger.recordWebhook(orderCode, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findAll()).singleElement().satisfies(sent -> {
            assertThat(sent.getUser().getId()).isEqualTo(buyerId);
            assertThat(sent.getType()).isEqualTo(NotificationType.VIP_GRANTED);
            // Hạn lấy từ chính phép cộng vừa chạy, không phải từ thân request.
            assertThat(sent.getMessage()).contains("có hiệu lực tới");
            assertThat(sent.getMetadata()).contains("vipUntil");
        });
    }

    @Test
    @DisplayName("đơn nạp Xu thanh toán xong: một thông báo kèm đúng số Xu vừa cộng")
    void aPaidCoinOrderIsAnnounced() {
        Long coinOrderCode = openCoinOrder(550L);

        ledger.recordWebhook(coinOrderCode, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findAll()).singleElement().satisfies(sent -> {
            assertThat(sent.getType()).isEqualTo(NotificationType.PAYMENT);
            assertThat(sent.getMessage()).contains("550 Xu");
        });
    }

    @Test
    @DisplayName("webhook gửi lại ba lần chỉ để lại một thông báo")
    void aRepeatedWebhookAnnouncesOnce() {
        ledger.recordWebhook(orderCode, true);
        ledger.recordWebhook(orderCode, true);
        ledger.recordWebhook(orderCode, true);
        entityManager.flush();
        entityManager.clear();

        // Câu UPDATE có điều kiện đã chặn lượt giao hàng thứ hai; khóa sự kiện
        // `payment:<id đơn>` là chốt chặn thứ hai, ở tầng cơ sở dữ liệu.
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("đơn chưa trả tiền thì không có thông báo nào")
    void anUnpaidOrderSaysNothing() {
        ledger.recordWebhook(orderCode, false);
        entityManager.flush();
        entityManager.clear();

        // Không có gì được giao thì cũng không có gì để báo — và một lời báo
        // "thanh toán thành công" cho một đơn chưa trả tiền là kiểu sai tệ nhất.
        assertThat(notificationRepository.count()).isZero();
    }

    /** Dựng thẳng một đơn nạp Xu đang chờ, không đi qua đường tạo đơn có gọi PayOS. */
    private Long openCoinOrder(long coins) {
        long code = orderCode + 1;
        orderRepository.save(PaymentOrder.builder()
                .orderCode(code)
                .kind(PaymentOrderKind.COIN_PACKAGE)
                .user(userRepository.findById(buyerId).orElseThrow())
                .itemName("Gói 50.000đ")
                .coinsGranted(coins)
                .amountVnd(50_000L)
                .status(PaymentOrderStatus.PENDING)
                .build());
        entityManager.flush();
        entityManager.clear();
        return code;
    }
}
