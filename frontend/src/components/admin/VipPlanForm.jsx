import { useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import { Button, Field, TextInput } from "../ui";

const EMPTY = {
  name: "",
  months: 1,
  priceVnd: "",
  description: "",
  active: true,
  sortOrder: 0,
};

/**
 * The form that writes a VIP plan.
 *
 * Shared by the two screens that need it: the main VIP page, where it creates
 * plans across the full width, and the price-list page, where it opens over a
 * card to edit that one. One form rather than two copies — a price field that
 * behaves differently depending on which screen you opened it from is a bug
 * waiting to happen.
 *
 * @param plan    the plan being edited, or null to create a new one
 * @param wide    lay the fields out in columns rather than a single stack
 * @param onDone  called with a message once the server has accepted the change
 * @param onError called with a message when it has not
 * @param onCancel shown as a cancel button when provided
 */
export default function VipPlanForm({ plan, wide = false, onDone, onError, onCancel }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);

  // Reopening on a different plan has to reload the fields, or the previous
  // plan's price is what gets saved over this one.
  useEffect(() => {
    setForm(
      plan
        ? {
            name: plan.name,
            months: plan.months,
            priceVnd: String(plan.priceVnd),
            description: plan.description ?? "",
            active: plan.active,
            sortOrder: plan.sortOrder,
          }
        : EMPTY,
    );
  }, [plan]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);

    const payload = {
      name: form.name,
      months: Number(form.months),
      priceVnd: Number(form.priceVnd),
      description: form.description || undefined,
      active: form.active,
      sortOrder: Number(form.sortOrder),
    };

    try {
      if (plan) {
        await adminApi.updateVipPlan(plan.id, payload);
        onDone?.(`Đã lưu gói “${payload.name}”.`);
      } else {
        await adminApi.createVipPlan(payload);
        setForm(EMPTY);
        onDone?.(`Đã tạo gói “${payload.name}”.`);
      }
    } catch (err) {
      onError?.(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className={`vip-plan-form ${wide ? "vip-form-wide" : ""}`} onSubmit={handleSubmit}>
      <Field label="Tên gói" htmlFor="plan-name">
        <TextInput
          id="plan-name"
          required
          placeholder="VIP 6 tháng"
          value={form.name}
          onChange={(event) => updateField("name", event.target.value)}
        />
      </Field>

      <Field label="Số tháng" htmlFor="plan-months">
        <TextInput
          id="plan-months"
          type="number"
          min="1"
          max="120"
          required
          value={form.months}
          onChange={(event) => updateField("months", event.target.value)}
        />
      </Field>

      <Field label="Giá (đồng)" htmlFor="plan-price">
        <TextInput
          id="plan-price"
          type="number"
          min="1000"
          step="1000"
          required
          placeholder="49000"
          value={form.priceVnd}
          onChange={(event) => updateField("priceVnd", event.target.value)}
        />
      </Field>

      <Field label="Thứ tự hiển thị" htmlFor="plan-sort">
        <TextInput
          id="plan-sort"
          type="number"
          min="0"
          max="999"
          value={form.sortOrder}
          onChange={(event) => updateField("sortOrder", event.target.value)}
        />
      </Field>

      {/* Wrapped so the grid can give it the room two figures would take: it
          holds a sentence, not a number. */}
      <div className="vip-form-desc">
        <Field
          label="Mô tả"
          htmlFor="plan-description"
          hint="Một dòng hiển thị dưới giá trên trang nâng cấp."
        >
          <TextInput
            id="plan-description"
            value={form.description}
            onChange={(event) => updateField("description", event.target.value)}
          />
        </Field>
      </div>

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
            {plan ? "Lưu thay đổi" : "Thêm gói"}
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
