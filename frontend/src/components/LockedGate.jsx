import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { formatCoins } from "../utils/format";
import { Alert, Button, ButtonLink } from "./ui";

/**
 * Shown in place of chapter content when the API refuses.
 *
 * Two refusals, two screens, and the difference is whether the reader can do
 * anything about it here and now:
 *
 * <ul>
 *   <li><b>403</b> — thiếu cấp bậc. Ngõ cụt tại chỗ: đăng nhập, hoặc đi mua VIP.</li>
 *   <li><b>402</b> — chương có giá Xu. Có một nút ngay đây, và màn hình phải nói
 *       đủ giá, số dư và phần còn thiếu để người đọc quyết định mà không phải mở
 *       tab khác kiểm tra ví.</li>
 * </ul>
 *
 * Wording branches on `requiredAccessLevel` so the reader is told exactly what
 * is missing.
 */
export default function LockedGate({
  requiredAccessLevel,
  message,
  purchase,
  onPurchase,
  purchasing = false,
  purchaseError,
}) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (purchase) {
    return (
      <PurchaseGate
        purchase={purchase}
        onPurchase={onPurchase}
        purchasing={purchasing}
        error={purchaseError}
      />
    );
  }

  const needsVip = requiredAccessLevel === "VIP";
  const canFixByLoggingIn = !isAuthenticated;

  return (
    <div className="locked-gate">
      <span className="locked-gate-tag">{needsVip ? "Nội dung VIP" : "Nội dung hạn chế"}</span>

      <h2>{needsVip ? "Chương dành cho thành viên VIP" : "Chương yêu cầu đăng nhập"}</h2>

      <p className="muted" style={{ maxWidth: "44ch" }}>
        {message ??
          (needsVip
            ? "Bạn cần tài khoản VIP để đọc và nghe chương này."
            : "Vui lòng đăng nhập để tiếp tục đọc chương này.")}
      </p>

      {canFixByLoggingIn ? (
        <div className="row" style={{ justifyContent: "center" }}>
          <ButtonLink to="/dang-nhap" state={{ from: location }} variant="primary" size="lg">
            Đăng nhập
          </ButtonLink>
          <ButtonLink to="/dang-ky" size="lg">
            Đăng ký tài khoản
          </ButtonLink>
        </div>
      ) : (
        /* A signed-in member is one step from being able to read this, so the
           screen has to offer that step. Telling them to contact an admin left
           them at a dead end while the upgrade page was working all along. */
        <div className="stack" style={{ gap: "0.75rem", alignItems: "center" }}>
          <ButtonLink to="/nang-cap" variant="primary" size="lg">
            Nâng cấp VIP
          </ButtonLink>
          <p className="muted" style={{ fontSize: "0.85rem" }}>
            Hoặc liên hệ quản trị viên nếu bạn đã có quyền VIP mà vẫn thấy màn hình này.
          </p>
        </div>
      )}
    </div>
  );
}

/**
 * Chương bán lẻ bằng Xu.
 *
 * <p>Ba con số nằm sẵn trên màn hình — giá, số dư, phần còn thiếu — vì cả ba đến
 * cùng một lần gọi và người đọc cần cả ba để quyết định. Bắt họ mở trang ví ra
 * đối chiếu là cách chắc chắn nhất để họ bỏ dở.
 *
 * @param purchase {{ coinPrice, balance, shortfall }}
 */
function PurchaseGate({ purchase, onPurchase, purchasing, error }) {
  const { coinPrice, balance, shortfall } = purchase;
  const affordable = shortfall <= 0;

  return (
    <div className="locked-gate locked-gate-purchase">
      <span className="locked-gate-tag">Chương trả phí</span>

      <h2>Mở khóa chương này</h2>

      <p className="muted" style={{ maxWidth: "44ch" }}>
        Chương này được mở lẻ bằng Xu. Mở một lần rồi là của bạn vĩnh viễn.
      </p>

      <dl className="purchase-facts">
        <div>
          <dt>Giá chương</dt>
          <dd className="tabular-num">{formatCoins(coinPrice)}</dd>
        </div>
        <div>
          <dt>Ví của bạn</dt>
          <dd className="tabular-num">{formatCoins(balance)}</dd>
        </div>
        {!affordable && (
          <div className="purchase-shortfall">
            <dt>Còn thiếu</dt>
            <dd className="tabular-num">{formatCoins(shortfall)}</dd>
          </div>
        )}
      </dl>

      {error && <Alert tone="error">{error}</Alert>}

      <div className="stack" style={{ gap: "0.75rem", alignItems: "center" }}>
        {affordable ? (
          <>
            <Button variant="primary" size="lg" loading={purchasing} onClick={onPurchase}>
              Mở khóa với {formatCoins(coinPrice)}
            </Button>
            <p className="muted" style={{ fontSize: "0.85rem" }}>
              Hoặc <Link to="/nang-cap">nâng cấp VIP</Link> để đọc mọi chương mà không tốn Xu.
            </p>
          </>
        ) : (
          <>
            <ButtonLink to="/nap-xu" variant="primary" size="lg">
              Nạp thêm Xu
            </ButtonLink>
            <p className="muted" style={{ fontSize: "0.85rem" }}>
              Hoặc <Link to="/nang-cap">nâng cấp VIP</Link> để đọc mọi chương mà không tốn Xu.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
