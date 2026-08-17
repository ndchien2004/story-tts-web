import { Link } from "react-router-dom";
import { formatCoins } from "../utils/format";
import { Button } from "./ui";

const CheckIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.6}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </svg>
);

/**
 * Biên nhận sau khi mở khóa một chương bằng Xu.
 *
 * <h3>Vì sao là một màn hình phải bấm qua, không phải một toast</h3>
 * Trước đây trừ Xu xong là vào thẳng chương. Về mặt kỹ thuật thì đúng — người
 * đọc có thứ họ vừa trả tiền — nhưng nó im lặng đúng vào lúc không được im
 * lặng: tiền vừa rời khỏi ví mà không có gì xác nhận, và số dư mới thì phải tự
 * đi tìm.
 *
 * <p>Một toast tự tắt sau ba giây cũng không đủ. Toast hợp với "đã lưu" — thứ
 * đọc một lần rồi thôi; còn đây là một giao dịch, và thứ người ta cần từ một
 * giao dịch là được nhìn con số đủ lâu để đối chiếu. Nên nó dừng lại và đợi một
 * cú bấm.
 *
 * <p>Đứng đúng chỗ màn hình mở khóa vừa đứng, nên mắt không phải đi tìm: chỗ vừa
 * có cái nút "Mở khóa" giờ là chỗ trả lời cho cú bấm ấy.
 *
 * <p>Chương được tải sẵn ở phía sau trong lúc biên nhận còn hiện, nên cú bấm
 * "Bắt đầu đọc" mở ra ngay chứ không đổi một lần chờ này lấy một lần chờ khác.
 *
 * @param coinsSpent số Xu vừa bị trừ
 * @param balance    số dư sau giao dịch
 * @param loading    chương chưa tải xong; nút chờ thay vì mở ra một trang trắng
 */
export default function PurchaseReceipt({ coinsSpent, balance, loading, onContinue }) {
  return (
    <div className="locked-gate purchase-receipt">
      <span className="purchase-receipt-mark" aria-hidden="true">
        <CheckIcon />
      </span>

      <span className="locked-gate-tag">Đã mở khóa</span>

      <h2>Chương này giờ là của bạn</h2>

      <p className="muted" style={{ maxWidth: "44ch" }}>
        Quyền đọc không có hạn sử dụng — lần sau quay lại bạn vào thẳng, không tốn thêm Xu nào.
      </p>

      <dl className="purchase-facts">
        <div>
          <dt>Đã trừ</dt>
          <dd className="tabular-num">{formatCoins(coinsSpent)}</dd>
        </div>
        <div>
          <dt>Ví còn</dt>
          <dd className="tabular-num">{formatCoins(balance)}</dd>
        </div>
      </dl>

      <div className="stack" style={{ gap: "0.75rem", alignItems: "center" }}>
        <Button variant="primary" size="lg" loading={loading} onClick={onContinue}>
          Bắt đầu đọc
        </Button>
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Giao dịch được ghi lại ở <Link to="/tai-khoan">trang tài khoản</Link>.
        </p>
      </div>
    </div>
  );
}
