import { useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import { formatCoins } from "../../utils/format";
import { Button, Field, TextInput } from "../ui";

const EMPTY = {
  code: "",
  coinAmount: "",
  startAt: "",
  endAt: "",
  maxUses: "",
  enabled: true,
  description: "",
};

/**
 * Biểu mẫu viết một gift code.
 *
 * <h3>Ba ô để trống có nghĩa, và biểu mẫu phải nói ra nghĩa ấy</h3>
 * Bỏ trống thời gian bắt đầu là "có hiệu lực ngay", bỏ trống thời gian kết thúc
 * là "không hết hạn", bỏ trống số lượt là "không giới hạn". Không có gợi ý thì
 * mỗi ô trống là một câu hỏi người dùng phải tự đoán, và đoán sai ở ô số lượt là
 * phát Xu không giới hạn khi định phát 100 lượt.
 *
 * <p>Dòng xem trước ở cuối dựng lại đúng câu ấy từ những gì vừa gõ — cùng lý do
 * với dòng xem trước của {@code CoinPackageForm}: một cấu hình đặt sai bị phát
 * hiện trước khi lưu, chứ không phải sau khi có người đổi.
 *
 * <h3>Múi giờ</h3>
 * {@code <input type="datetime-local">} nhận và trả về giờ địa phương của trình
 * duyệt. Việc đổi sang UTC xảy ra đúng một lần, ở {@code toIso} bên dưới, và
 * việc đổi ngược lại đúng một lần ở {@code toLocalInput}. Không có chỗ nào khác
 * trong đường đi này chạm vào múi giờ, nên "20:00" quản trị viên gõ là "20:00"
 * mà máy chủ so sánh.
 *
 * @param giftCode mã đang sửa, hoặc null để tạo mới
 * @param onDone   gọi kèm một câu thông báo khi máy chủ đã nhận
 * @param onError  gọi kèm câu lỗi khi không
 * @param onCancel hiện nút hủy khi được truyền
 */
export default function GiftCodeForm({ giftCode, onDone, onError, onCancel }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);

  // Mở lại trên một mã khác thì phải nạp lại các ô, nếu không cấu hình của mã
  // trước là thứ được ghi đè lên mã này.
  useEffect(() => {
    setForm(
      giftCode
        ? {
            code: giftCode.code,
            coinAmount: String(giftCode.coinAmount),
            startAt: toLocalInput(giftCode.startAt),
            endAt: toLocalInput(giftCode.endAt),
            maxUses: giftCode.maxUses == null ? "" : String(giftCode.maxUses),
            enabled: giftCode.enabled,
            description: giftCode.description ?? "",
          }
        : EMPTY,
    );
  }, [giftCode]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  /** Mã đã có người đổi thì không đổi tên được — máy chủ cũng từ chối. */
  const locked = Boolean(giftCode && giftCode.usedCount > 0);

  async function handleGenerate() {
    setGenerating(true);
    try {
      updateField("code", await adminApi.generateGiftCode(null));
    } catch (err) {
      onError?.(err.message);
    } finally {
      setGenerating(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);

    const payload = {
      code: form.code.trim(),
      coinAmount: Number(form.coinAmount),
      // Chuỗi rỗng thành null, không thành 0 hay Invalid Date: null là giá trị
      // mang nghĩa "không đặt hạn", và nó phải tới máy chủ nguyên vẹn.
      startAt: toIso(form.startAt),
      endAt: toIso(form.endAt),
      maxUses: form.maxUses === "" ? null : Number(form.maxUses),
      enabled: form.enabled,
      description: form.description.trim() || null,
    };

    try {
      if (giftCode) {
        await adminApi.updateGiftCode(giftCode.id, payload);
        onDone?.(`Đã lưu gift code “${payload.code}”.`);
      } else {
        await adminApi.createGiftCode(payload);
        setForm(EMPTY);
        onDone?.(`Đã tạo gift code “${payload.code}”.`);
      }
    } catch (err) {
      onError?.(err.message);
    } finally {
      setSaving(false);
    }
  }

  const amount = Number(form.coinAmount) || 0;
  const uses = form.maxUses === "" ? null : Number(form.maxUses);

  return (
    <form className="vip-plan-form" onSubmit={handleSubmit}>
      <Field
        label="Gift code"
        htmlFor="gift-form-code"
        hint={
          locked
            ? `Mã đã có ${giftCode.usedCount} lượt đổi nên không đổi được nữa — lịch sử Xu của họ trỏ về nó.`
            : "Chữ, số và dấu gạch ngang. Không phân biệt hoa thường."
        }
      >
        <div className="gift-code-input-row">
          <TextInput
            id="gift-form-code"
            required
            disabled={locked}
            maxLength={64}
            autoComplete="off"
            spellCheck={false}
            placeholder="SUMMER2026"
            value={form.code}
            onChange={(event) => updateField("code", event.target.value.toUpperCase())}
          />
          {!locked && (
            <Button size="sm" loading={generating} onClick={handleGenerate}>
              Sinh mã
            </Button>
          )}
        </div>
      </Field>

      <Field label="Số Xu mỗi lượt" htmlFor="gift-form-amount">
        <TextInput
          id="gift-form-amount"
          type="number"
          min="1"
          required
          placeholder="500"
          value={form.coinAmount}
          onChange={(event) => updateField("coinAmount", event.target.value)}
        />
      </Field>

      <Field
        label="Bắt đầu"
        htmlFor="gift-form-start"
        hint="Để trống là có hiệu lực ngay. Đặt mốc tương lai để hẹn giờ."
      >
        <TextInput
          id="gift-form-start"
          type="datetime-local"
          value={form.startAt}
          onChange={(event) => updateField("startAt", event.target.value)}
        />
      </Field>

      <Field label="Kết thúc" htmlFor="gift-form-end" hint="Để trống là không hết hạn.">
        <TextInput
          id="gift-form-end"
          type="datetime-local"
          value={form.endAt}
          onChange={(event) => updateField("endAt", event.target.value)}
        />
      </Field>

      <Field
        label="Số lượt tối đa"
        htmlFor="gift-form-max"
        hint={
          locked
            ? `Không nhỏ hơn ${giftCode.usedCount} — số lượt đã phát ra.`
            : "Để trống là không giới hạn."
        }
      >
        <TextInput
          id="gift-form-max"
          type="number"
          min={locked ? giftCode.usedCount : 1}
          placeholder="Không giới hạn"
          value={form.maxUses}
          onChange={(event) => updateField("maxUses", event.target.value)}
        />
      </Field>

      <div className="vip-form-desc">
        <Field
          label="Ghi chú nội bộ"
          htmlFor="gift-form-description"
          hint="Chỉ quản trị viên thấy. Người đổi mã không bao giờ đọc dòng này."
        >
          <TextInput
            id="gift-form-description"
            maxLength={300}
            placeholder="Mã quà sự kiện hè 2026"
            value={form.description}
            onChange={(event) => updateField("description", event.target.value)}
          />
        </Field>
      </div>

      {/* Câu mô tả lại đúng những gì vừa gõ, kể cả những ô để trống. */}
      {amount > 0 && (
        <p className="muted coin-form-preview">
          Mỗi tài khoản đổi được <strong>một lần</strong> và nhận{" "}
          <strong>{formatCoins(amount)}</strong>
          {uses ? ` · tối đa ${uses.toLocaleString("vi-VN")} lượt` : " · không giới hạn lượt"}
          {form.startAt ? ` · từ ${describeLocal(form.startAt)}` : " · hiệu lực ngay"}
          {form.endAt ? ` · đến ${describeLocal(form.endAt)}` : " · không hết hạn"}
          {uses ? ` · tổng ngân sách tối đa ${formatCoins(amount * uses)}` : ""}.
        </p>
      )}

      <div className="vip-form-foot">
        <label className="row" style={{ gap: "var(--space-2)" }}>
          <input
            type="checkbox"
            className="nb-checkbox"
            checked={form.enabled}
            onChange={(event) => updateField("enabled", event.target.checked)}
          />
          <span>Đang bật</span>
        </label>

        <div className="row" style={{ gap: "var(--space-2)" }}>
          <Button type="submit" variant="primary" loading={saving}>
            {giftCode ? "Lưu thay đổi" : "Tạo gift code"}
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

/**
 * Chuỗi của ô `datetime-local` → mốc ISO theo UTC, hoặc null khi để trống.
 *
 * `new Date("2026-08-20T20:00")` được trình duyệt đọc theo giờ địa phương, nên
 * `toISOString()` cho ra đúng cái mốc UTC tương ứng. Đây là chỗ duy nhất trong
 * biểu mẫu này đổi múi giờ theo chiều đi.
 */
function toIso(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/**
 * Mốc ISO → chuỗi mà `<input type="datetime-local">` nhận.
 *
 * Ô ấy chỉ đọc được `YYYY-MM-DDTHH:mm` theo giờ địa phương, nên không đưa thẳng
 * chuỗi ISO có chữ Z vào được. Cùng phép biến đổi với `PublishControl`, và cùng
 * lý do.
 */
function toLocalInput(iso) {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/** Chuỗi của ô giờ → câu tiếng Việt cho dòng xem trước. */
function describeLocal(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
