import { useState } from "react";
import { giftCodeApi } from "../api/endpoints";
import { formatCoins } from "../utils/format";
import { Alert, Button, Field, TextInput } from "./ui";

/**
 * Ô đổi gift code của người đọc.
 *
 * <h3>Vì sao là một khối dùng lại được, không phải một trang</h3>
 * Nó xuất hiện ở hai chỗ — cạnh số dư trong trang tài khoản, và trên trang nạp
 * Xu — vì hai chỗ ấy là hai câu trả lời cho cùng một ý định ("tôi cần thêm Xu").
 * Một trang riêng thì phải tìm thấy trước khi dùng được, mà chẳng ai đi tìm một
 * trang để nhập cái mã họ vừa nhận được.
 *
 * <h3>Ba chi tiết nhỏ, mỗi cái sửa một điều gây bực</h3>
 * <ul>
 *   <li><b>Ô nhập không bị xóa khi lỗi.</b> Mã sai hay mã hết hạn thì thứ người
 *       ta cần làm là sửa một ký tự, không phải gõ lại từ đầu. Chỉ lần đổi thành
 *       công mới dọn ô.</li>
 *   <li><b>Nút khóa trong lúc chờ.</b> Bấm thêm lần nữa không cộng Xu thêm — máy
 *       chủ đã lo phần ấy bằng ràng buộc duy nhất — nhưng nó sinh ra một câu
 *       "bạn đã đổi mã này rồi" cho một người vừa đổi thành công, và câu ấy
 *       trông y hệt một lỗi.</li>
 *   <li><b>Chữ hoa hiện ngay lúc gõ.</b> Máy chủ chuẩn hóa mã thành chữ hoa, nên
 *       ô nhập hiện đúng thứ sẽ được gửi đi thay vì để người dùng đoán xem gõ
 *       thường có sao không.</li>
 * </ul>
 *
 * @param onRedeemed gọi kèm `{ code, coinAmount, balance }` sau mỗi lần đổi
 *                   được — chỗ để trang bao ngoài cập nhật số dư đang hiện
 * @param compact    thu gọn cho cột hẹp (bảng ví ở trang tài khoản)
 */
export default function GiftCodeRedeem({ onRedeemed, compact = false }) {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;

    const typed = code.trim();
    if (!typed) return;

    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await giftCodeApi.redeem(typed);
      setSuccess(result);
      // Chỉ dọn ô khi đã thành công — xem ghi chú ở đầu tệp.
      setCode("");
      onRedeemed?.(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={`gift-redeem ${compact ? "gift-redeem-compact" : ""}`} onSubmit={handleSubmit}>
      <Field
        label="Gift code"
        htmlFor="gift-code"
        hint={compact ? undefined : "Không phân biệt chữ hoa hay chữ thường."}
      >
        <div className="gift-redeem-row">
          <TextInput
            id="gift-code"
            value={code}
            // Chuẩn hóa ngay tại ô, cùng cách máy chủ chuẩn hóa lúc lưu.
            onChange={(event) => setCode(event.target.value.toUpperCase())}
            placeholder="SUMMER2026"
            maxLength={64}
            autoComplete="off"
            spellCheck={false}
            disabled={busy}
            aria-describedby={error ? "gift-code-error" : undefined}
          />
          <Button type="submit" variant="primary" loading={busy} disabled={!code.trim()}>
            {busy ? "Đang kiểm tra…" : "Đổi mã"}
          </Button>
        </div>
      </Field>

      {/* Câu trả lời nói ra cả hai con số người ta muốn biết: vừa nhận bao
          nhiêu, và bây giờ còn bao nhiêu. */}
      {success && (
        <Alert tone="success">
          🎉 Đổi mã thành công! Bạn nhận được{" "}
          <strong>+{formatCoins(success.coinAmount)}</strong>. Số dư hiện tại:{" "}
          <strong>{formatCoins(success.balance)}</strong>.
        </Alert>
      )}

      {error && (
        <div id="gift-code-error">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
    </form>
  );
}
