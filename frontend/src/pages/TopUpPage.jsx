import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { walletApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import { Alert, Button, ButtonLink, EmptyState, Spinner } from "../components/ui";
import { formatCoins, formatVnd } from "../utils/format";

/**
 * Nạp Xu.
 *
 * Cùng hình dạng với trang nâng cấp VIP, và cố ý giống: hai thứ bán được đi qua
 * cùng một cổng thanh toán và cùng một trang kết quả, nên hai trang bán hàng
 * không nên bắt người dùng học hai cách mua.
 *
 * <p>Bảng giá công khai — đó là thứ người ta cân nhắc trước khi quyết định có
 * đăng ký tài khoản hay không — còn nút mua thì cần đăng nhập, vì Xu phải vào ví
 * của một người cụ thể.
 *
 * <h3>Vì sao trang này tách khỏi trang VIP</h3>
 * Hai thứ trả lời hai nhu cầu khác nhau: VIP là "tôi đọc nhiều, mở hết cho tôi",
 * Xu là "tôi chỉ muốn đúng chương này". Gộp vào một trang thì người đang muốn mở
 * một chương phải đọc qua bảng giá thuê bao trước, và ngược lại.
 */
export default function TopUpPage() {
  const { isAuthenticated } = useAuth();

  const [packages, setPackages] = useState(null);
  const [balance, setBalance] = useState(null);
  const [error, setError] = useState(null);
  const [buyingId, setBuyingId] = useState(null);

  useEffect(() => {
    walletApi
      .packages()
      .then(setPackages)
      .catch((err) => {
        setPackages([]);
        setError(err.message);
      });
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setBalance(null);
      return;
    }
    walletApi
      .balance()
      .then((data) => setBalance(data.balance))
      .catch(() => setBalance(null));
  }, [isAuthenticated]);

  /**
   * Tạo đơn rồi giao trình duyệt cho PayOS.
   *
   * Chuyển trang thẳng chứ không mở popup: trang của cổng thanh toán là nơi có
   * mã QR và đường mở ứng dụng ngân hàng, mà một popup bị chặn sẽ để người mua
   * ngồi nhìn một cái nút vừa bấm mà không có gì xảy ra.
   */
  async function handleBuy(pack) {
    setBuyingId(pack.id);
    setError(null);
    try {
      const order = await walletApi.createOrder(pack.id);
      if (!order.checkoutUrl) {
        throw new Error("Cổng thanh toán chưa trả về liên kết. Vui lòng thử lại.");
      }
      window.location.assign(order.checkoutUrl);
    } catch (err) {
      setError(err.message);
      setBuyingId(null);
    }
  }

  if (packages === null) {
    return <Spinner label="Đang tải bảng giá…" />;
  }

  return (
    <div className="stack" style={{ gap: "var(--space-6)" }}>
      <header className="upgrade-head">
        <span className="label">Nạp Xu</span>
        <h1>Mở khóa từng chương bằng Xu</h1>
        <p className="muted" style={{ maxWidth: "60ch" }}>
          Xu dùng để mở những chương được đặt giá lẻ. Chương đã mở là của bạn vĩnh viễn — Xu không
          có hạn sử dụng và không tự trừ đi theo thời gian.
        </p>
      </header>

      {error && <Alert tone="error">{error}</Alert>}

      {isAuthenticated && balance !== null && (
        <Alert tone="info">
          Ví của bạn đang có <strong>{formatCoins(balance)}</strong>.
        </Alert>
      )}

      {packages.length === 0 ? (
        <EmptyState title="Chưa có gói nạp nào">
          Quản trị viên chưa mở gói nạp Xu nào. Vui lòng quay lại sau.
        </EmptyState>
      ) : (
        <div className="plan-grid">
          {packages.map((pack) => (
            <article key={pack.id} className="plan-card">
              <header className="plan-card-head">
                <h2>{pack.name}</h2>
                <span className="plan-term">
                  {/* Phần tặng nói riêng chứ không cộng gộp: "550 Xu" không giải
                      thích được vì sao gói lớn đáng mua hơn, "500 + 50 tặng" thì có. */}
                  {pack.bonusCoins > 0
                    ? `${formatCoins(pack.coins)} + ${pack.bonusCoins.toLocaleString("vi-VN")} tặng`
                    : formatCoins(pack.coins)}
                </span>
              </header>

              <p className="plan-price">{formatVnd(pack.priceVnd)}</p>

              <p className="plan-coins tabular-num">
                Nhận <strong>{formatCoins(pack.totalCoins)}</strong>
              </p>

              {pack.description && <p className="muted">{pack.description}</p>}

              <div className="plan-card-foot">
                {isAuthenticated ? (
                  <Button
                    variant="primary"
                    block
                    loading={buyingId === pack.id}
                    onClick={() => handleBuy(pack)}
                  >
                    Nạp gói này
                  </Button>
                ) : (
                  <ButtonLink to="/dang-nhap" variant="primary" block>
                    Đăng nhập để nạp
                  </ButtonLink>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <section className="upgrade-notes">
        <h2 className="label">Cần biết trước khi nạp</h2>
        <ul>
          <li>Thanh toán qua PayOS bằng chuyển khoản ngân hàng hoặc quét mã QR.</li>
          <li>Xu được cộng ngay khi ngân hàng báo có, thường trong vài giây.</li>
          <li>Chương đã mở bằng Xu thuộc về tài khoản của bạn vĩnh viễn.</li>
          <li>
            Đọc nhiều thì <Link to="/nang-cap">gói VIP</Link> mở được mọi chương mà không tốn Xu
            nào.
          </li>
          <li>
            Mọi lần cộng trừ Xu đều được ghi lại ở{" "}
            <Link to="/tai-khoan">trang tài khoản</Link>.
          </li>
        </ul>
      </section>
    </div>
  );
}
