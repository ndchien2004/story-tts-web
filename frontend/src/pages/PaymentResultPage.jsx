import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { vipApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import { Alert, Button, ButtonLink, Spinner } from "../components/ui";
import { formatCoins, formatDate, formatVnd } from "../utils/format";

/** How many times to re-ask before letting the reader drive. */
const MAX_POLLS = 5;
const POLL_MS = 2000;

/**
 * Where PayOS sends the reader back to.
 *
 * The gateway appends its own verdict to the URL, but that verdict is a query
 * string — anyone can type one. So it is never trusted: the page asks our own
 * server, which asks PayOS in turn. The parameters are used only to decide how
 * long to keep waiting.
 *
 * Payment confirmation normally arrives by webhook, which can land a moment
 * after the browser does, hence the short poll rather than a single check.
 *
 * <h3>Một trang cho cả hai thứ bán được</h3>
 * Gói VIP và gói Xu đi qua cùng một cổng và cùng một bảng đơn, nên đường tra cứu
 * cũng chung — {@code vipApi.checkOrder} nhận mọi mã đơn, không riêng đơn VIP.
 * Trang này chỉ rẽ nhánh ở hai chỗ: câu nói khi thành công, và nút dẫn đi tiếp.
 */
export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const { refresh } = useAuth();

  const orderCode = params.get("orderCode");
  const cancelled = params.get("cancel") === "true" || params.get("status") === "CANCELLED";

  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [polling, setPolling] = useState(true);
  const pollCount = useRef(0);

  const check = useCallback(async () => {
    if (!orderCode) {
      setError("Liên kết không kèm mã đơn nên không tra cứu được.");
      setPolling(false);
      return null;
    }
    try {
      const data = await vipApi.checkOrder(orderCode);
      setOrder(data);
      setError(null);
      return data;
    } catch (err) {
      setError(err.message);
      setPolling(false);
      return null;
    }
  }, [orderCode]);

  useEffect(() => {
    let timer = 0;
    let cancelledEffect = false;

    async function run() {
      const data = await check();
      if (cancelledEffect || !data) return;

      if (data.status === "PAID") {
        setPolling(false);
        // The token was minted before the purchase; without this the reader
        // stays a plain member in the UI until the next sign-in.
        refresh?.();
        return;
      }

      // Anything settled is settled — only a pending order is worth re-asking.
      if (data.status !== "PENDING" || pollCount.current >= MAX_POLLS) {
        setPolling(false);
        return;
      }

      pollCount.current += 1;
      timer = setTimeout(run, POLL_MS);
    }

    run();
    return () => {
      cancelledEffect = true;
      clearTimeout(timer);
    };
  }, [check, refresh]);

  const paid = order?.status === "PAID";
  const stillPending = order?.status === "PENDING";

  // Cùng một đơn, cùng một cổng, khác thứ vừa mua — nên trang này chỉ rẽ nhánh ở
  // câu nói kết quả và ở nút dẫn đi tiếp.
  const isCoinOrder = order?.kind === "COIN_PACKAGE";

  return (
    <div className="container-narrow stack" style={{ gap: "var(--space-5)" }}>
      <header className="stack" style={{ gap: "var(--space-2)" }}>
        <span className="label">Kết quả thanh toán</span>
        <h1>
          {polling && "Đang xác nhận giao dịch…"}
          {!polling && paid && "Thanh toán thành công"}
          {!polling && !paid && stillPending && "Chưa nhận được thanh toán"}
          {!polling && !paid && !stillPending && order && "Đơn đã kết thúc"}
          {!polling && !order && "Không tra cứu được đơn"}
        </h1>
      </header>

      {error && <Alert tone="error">{error}</Alert>}

      {polling && <Spinner label="Đang hỏi cổng thanh toán…" />}

      {order && (
        <dl className="payment-summary">
          <div>
            <dt>Mã đơn</dt>
            <dd className="tabular-num">{order.orderCode}</dd>
          </div>
          <div>
            <dt>Gói</dt>
            <dd>
              {order.itemName}
              {isCoinOrder
                ? ` · ${formatCoins(order.coinsGranted)}`
                : order.months
                  ? ` · ${order.months} tháng`
                  : ""}
            </dd>
          </div>
          <div>
            <dt>Số tiền</dt>
            <dd className="tabular-num">{formatVnd(order.amountVnd)}</dd>
          </div>
          <div>
            <dt>Trạng thái</dt>
            <dd>{order.statusLabel}</dd>
          </div>
        </dl>
      )}

      {!polling && paid && isCoinOrder && (
        <Alert tone="success">
          Đã cộng <strong>{formatCoins(order.coinsGranted)}</strong> vào ví của bạn. Số Xu này dùng
          để mở khóa từng chương và không có hạn sử dụng.
        </Alert>
      )}

      {!polling && paid && !isCoinOrder && (
        <Alert tone="success">
          Tài khoản của bạn là VIP tới hết ngày <strong>{formatDate(order.vipUntilAfter)}</strong>.
          Mọi chương VIP đã mở khóa.
        </Alert>
      )}

      {!polling && stillPending && !cancelled && (
        <Alert tone="warning">
          Ngân hàng có thể cần thêm một chút thời gian. Nếu bạn đã chuyển khoản, hãy bấm kiểm tra
          lại sau ít phút — {isCoinOrder ? "Xu" : "hạn VIP"} sẽ được cộng ngay khi tiền về.
        </Alert>
      )}

      {!polling && stillPending && cancelled && (
        <Alert tone="info">
          Bạn đã rời khỏi trang thanh toán. Đơn vẫn còn hiệu lực trong 30 phút nếu bạn muốn trả
          tiếp.
        </Alert>
      )}

      <div className="row">
        {stillPending && (
          <>
            <Button
              onClick={() => {
                pollCount.current = 0;
                setPolling(true);
                check().finally(() => setPolling(false));
              }}
            >
              Kiểm tra lại
            </Button>
            {order?.checkoutUrl && (
              <a className="nb-btn nb-btn-primary" href={order.checkoutUrl}>
                Trả tiếp
              </a>
            )}
          </>
        )}

        {paid && (
          <ButtonLink to="/truyen" variant="primary">
            Bắt đầu đọc
          </ButtonLink>
        )}

        <ButtonLink to="/tai-khoan">Về trang tài khoản</ButtonLink>
        {!paid && (
          <ButtonLink to={isCoinOrder ? "/nap-xu" : "/nang-cap"}>Xem lại bảng giá</ButtonLink>
        )}
      </div>
    </div>
  );
}
