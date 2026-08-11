import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { vipApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import { Alert, Button, ButtonLink, EmptyState, Spinner } from "../components/ui";
import { formatDate, formatVnd } from "../utils/format";

/**
 * Buying VIP.
 *
 * The price list is public — it is what someone weighs before deciding whether
 * to sign up at all — while the buy button needs an account, because an order
 * has to belong to somebody. Signed-out visitors therefore see the same table
 * with a link to sign in where the button would be.
 */
export default function UpgradePage() {
  const { isAuthenticated, refresh } = useAuth();

  const [plans, setPlans] = useState(null);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState(null);
  const [buyingId, setBuyingId] = useState(null);

  useEffect(() => {
    vipApi
      .plans()
      .then(setPlans)
      .catch((err) => {
        setPlans([]);
        setError(err.message);
      });
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setStatus(null);
      return;
    }
    vipApi
      .status()
      .then(setStatus)
      .catch(() => setStatus(null));
    // The account's VIP flag may have changed since the token was issued.
    refresh?.();
  }, [isAuthenticated, refresh]);

  /**
   * Creates the order, then hands the browser to PayOS.
   *
   * A full navigation rather than a popup: the gateway's own page is where the
   * bank app and the QR code live, and a popup blocked by the browser would
   * leave the reader staring at a button that did nothing.
   */
  async function handleBuy(plan) {
    setBuyingId(plan.id);
    setError(null);
    try {
      const order = await vipApi.createOrder(plan.id);
      if (!order.checkoutUrl) {
        throw new Error("Cổng thanh toán chưa trả về liên kết. Vui lòng thử lại.");
      }
      window.location.assign(order.checkoutUrl);
    } catch (err) {
      setError(err.message);
      setBuyingId(null);
    }
  }

  if (plans === null) {
    return <Spinner label="Đang tải bảng giá…" />;
  }

  const paymentOff = status && !status.paymentAvailable;

  return (
    <div className="stack" style={{ gap: "var(--space-6)" }}>
      <header className="upgrade-head">
        <span className="label">Nâng cấp</span>
        <h1>Mở khóa toàn bộ chương VIP</h1>
        <p className="muted" style={{ maxWidth: "60ch" }}>
          Tài khoản VIP đọc và nghe được mọi chương, kể cả những chương tác giả đặt ở mức VIP. Gói
          tính theo tháng, hết hạn thì tài khoản trở lại thành viên thường — không tự động gia hạn,
          không lưu thông tin thẻ.
        </p>
      </header>

      {error && <Alert tone="error">{error}</Alert>}

      {status?.granted && (
        <Alert tone="info">
          Tài khoản của bạn đã được quản trị viên cấp VIP vĩnh viễn. Bạn không cần mua thêm gói nào.
        </Alert>
      )}

      {status?.vipUntil && !status.granted && (
        <Alert tone="info">
          Bạn đang là VIP tới hết ngày <strong>{formatDate(status.vipUntil)}</strong>. Mua thêm gói
          sẽ cộng dồn vào thời hạn này.
        </Alert>
      )}

      {paymentOff && (
        <Alert tone="warning">
          Cổng thanh toán chưa được cấu hình nên tạm thời chưa mua được gói. Vui lòng liên hệ quản
          trị viên.
        </Alert>
      )}

      {plans.length === 0 ? (
        <EmptyState title="Chưa có gói nào được bán">
          Quản trị viên chưa mở gói VIP nào. Vui lòng quay lại sau.
        </EmptyState>
      ) : (
        <div className="plan-grid">
          {plans.map((plan) => (
            <article key={plan.id} className="plan-card">
              <header className="plan-card-head">
                <h2>{plan.name}</h2>
                <span className="plan-term">
                  {plan.months} tháng · {formatVnd(plan.pricePerMonth)}/tháng
                </span>
              </header>

              <p className="plan-price">{formatVnd(plan.priceVnd)}</p>

              {plan.description && <p className="muted">{plan.description}</p>}

              <div className="plan-card-foot">
                {isAuthenticated ? (
                  <Button
                    variant="primary"
                    block
                    disabled={status?.granted || paymentOff}
                    loading={buyingId === plan.id}
                    onClick={() => handleBuy(plan)}
                  >
                    {status?.granted ? "Đã có VIP" : "Mua gói này"}
                  </Button>
                ) : (
                  <ButtonLink to="/dang-nhap" variant="primary" block>
                    Đăng nhập để mua
                  </ButtonLink>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <section className="upgrade-notes">
        <h2 className="label">Cần biết trước khi mua</h2>
        <ul>
          <li>Thanh toán qua PayOS bằng chuyển khoản ngân hàng hoặc quét mã QR.</li>
          <li>Hạn VIP được cộng ngay khi ngân hàng báo có, thường trong vài giây.</li>
          <li>Mua tiếp khi vẫn còn hạn thì thời gian được cộng dồn, không mất phần chưa dùng.</li>
          <li>
            Đơn chưa thanh toán sẽ tự hết hiệu lực sau 30 phút; xem lại đơn của bạn ở{" "}
            <Link to="/tai-khoan">trang tài khoản</Link>.
          </li>
        </ul>
      </section>
    </div>
  );
}
