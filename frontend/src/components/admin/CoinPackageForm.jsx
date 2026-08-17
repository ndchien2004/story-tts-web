import { useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import { formatCoins, formatVnd } from "../../utils/format";
import { Button, Field, TextInput } from "../ui";

const EMPTY = {
  name: "",
  priceVnd: "",
  coins: "",
  bonusCoins: 0,
  description: "",
  active: true,
  sortOrder: 0,
};

/**
 * Biểu mẫu viết một gói nạp Xu.
 *
 * Cùng hình dạng với {@code VipPlanForm}, và cùng lý do tồn tại: tỉ lệ quy đổi
 * là dữ liệu quản trị viên nhập, không phải hằng số trong mã nguồn.
 *
 * <p>Chỗ khác biệt duy nhất đáng nói là dòng xem trước ở cuối. Một gói nạp có
 * ba con số dễ nhầm lẫn với nhau — tiền, Xu cơ bản, Xu tặng — và câu người mua
 * thật sự đọc là kết quả của cả ba. Hiện sẵn câu ấy ngay lúc gõ thì một gói đặt
 * sai bị phát hiện trước khi lưu, chứ không phải sau khi có người mua.
 *
 * @param pack     gói đang sửa, hoặc null để tạo mới
 * @param onDone   gọi kèm một câu thông báo khi máy chủ đã nhận
 * @param onError  gọi kèm câu lỗi khi không
 * @param onCancel hiện nút hủy khi được truyền
 */
export default function CoinPackageForm({ pack, onDone, onError, onCancel }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);

  // Mở lại trên một gói khác thì phải nạp lại các ô, nếu không giá của gói
  // trước là thứ được ghi đè lên gói này.
  useEffect(() => {
    setForm(
      pack
        ? {
            name: pack.name,
            priceVnd: String(pack.priceVnd),
            coins: String(pack.coins),
            bonusCoins: pack.bonusCoins,
            description: pack.description ?? "",
            active: pack.active,
            sortOrder: pack.sortOrder,
          }
        : EMPTY,
    );
  }, [pack]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);

    const payload = {
      name: form.name,
      priceVnd: Number(form.priceVnd),
      coins: Number(form.coins),
      bonusCoins: Number(form.bonusCoins) || 0,
      description: form.description || undefined,
      active: form.active,
      sortOrder: Number(form.sortOrder),
    };

    try {
      if (pack) {
        await adminApi.updateCoinPackage(pack.id, payload);
        onDone?.(`Đã lưu gói “${payload.name}”.`);
      } else {
        await adminApi.createCoinPackage(payload);
        setForm(EMPTY);
        onDone?.(`Đã tạo gói “${payload.name}”.`);
      }
    } catch (err) {
      onError?.(err.message);
    } finally {
      setSaving(false);
    }
  }

  const price = Number(form.priceVnd) || 0;
  const total = (Number(form.coins) || 0) + (Number(form.bonusCoins) || 0);

  return (
    <form className="vip-plan-form" onSubmit={handleSubmit}>
      <Field label="Tên gói" htmlFor="pack-name">
        <TextInput
          id="pack-name"
          required
          placeholder="Gói 50.000đ"
          value={form.name}
          onChange={(event) => updateField("name", event.target.value)}
        />
      </Field>

      <Field label="Giá (đồng)" htmlFor="pack-price">
        <TextInput
          id="pack-price"
          type="number"
          min="1000"
          step="1000"
          required
          placeholder="50000"
          value={form.priceVnd}
          onChange={(event) => updateField("priceVnd", event.target.value)}
        />
      </Field>

      <Field label="Số Xu" htmlFor="pack-coins">
        <TextInput
          id="pack-coins"
          type="number"
          min="1"
          required
          placeholder="500"
          value={form.coins}
          onChange={(event) => updateField("coins", event.target.value)}
        />
      </Field>

      <Field
        label="Xu tặng thêm"
        htmlFor="pack-bonus"
        hint="Để 0 nếu gói không tặng."
      >
        <TextInput
          id="pack-bonus"
          type="number"
          min="0"
          value={form.bonusCoins}
          onChange={(event) => updateField("bonusCoins", event.target.value)}
        />
      </Field>

      <Field label="Thứ tự hiển thị" htmlFor="pack-sort">
        <TextInput
          id="pack-sort"
          type="number"
          min="0"
          max="999"
          value={form.sortOrder}
          onChange={(event) => updateField("sortOrder", event.target.value)}
        />
      </Field>

      <div className="vip-form-desc">
        <Field
          label="Mô tả"
          htmlFor="pack-description"
          hint="Một dòng hiển thị dưới giá trên trang nạp Xu."
        >
          <TextInput
            id="pack-description"
            value={form.description}
            onChange={(event) => updateField("description", event.target.value)}
          />
        </Field>
      </div>

      {/* Câu người mua sẽ đọc, dựng từ đúng những con số vừa gõ. */}
      {price > 0 && total > 0 && (
        <p className="muted coin-form-preview">
          Người mua trả <strong>{formatVnd(price)}</strong> và nhận{" "}
          <strong>{formatCoins(total)}</strong>
          {Number(form.bonusCoins) > 0 && ` (gồm ${form.bonusCoins} Xu tặng)`}.
        </p>
      )}

      <div className="vip-form-foot">
        <label className="row" style={{ gap: "var(--space-2)" }}>
          <input
            type="checkbox"
            className="nb-checkbox"
            checked={form.active}
            onChange={(event) => updateField("active", event.target.checked)}
          />
          <span>Đang bán</span>
        </label>

        <div className="row" style={{ gap: "var(--space-2)" }}>
          <Button type="submit" variant="primary" loading={saving}>
            {pack ? "Lưu thay đổi" : "Thêm gói"}
          </Button>
          {onCancel && (
            <Button variant="ghost" onClick={onCancel}>
              Hủy
            </Button>
          )}
        </div>
      </div>
    </form>
  );
}
