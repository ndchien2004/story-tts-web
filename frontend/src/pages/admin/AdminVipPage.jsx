import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import Pagination from "../../components/Pagination";
import { formatDateTime, formatVnd } from "../../utils/format";
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  Field,
  Select,
  Spinner,
  TextInput,
} from "../../components/ui";

const EMPTY_FORM = {
  name: "",
  months: 1,
  priceVnd: "",
  description: "",
  active: true,
  sortOrder: 0,
};

const ORDER_FILTERS = [
  { value: "", label: "Mọi trạng thái" },
  { value: "PAID", label: "Đã thanh toán" },
  { value: "PENDING", label: "Chờ thanh toán" },
  { value: "CANCELLED", label: "Đã hủy" },
  { value: "EXPIRED", label: "Quá hạn" },
];

const PAGE_SIZE = 20;

/**
 * VIP plans and the orders they produced.
 *
 * Term and price are data, not constants in the code: opening a six-month plan
 * or running a holiday price is a form on this page, not a deploy. Plans are
 * never deleted — orders point at them and readers may look back at what they
 * bought — so withdrawing one from sale is a switch instead.
 */
export default function AdminVipPage() {
  const [plans, setPlans] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);

  const [orders, setOrders] = useState(null);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [refreshing, setRefreshing] = useState(null);

  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const loadPlans = useCallback(() => {
    adminApi
      .vipPlans()
      .then(setPlans)
      .catch((err) => {
        setPlans([]);
        setError(err.message);
      });
  }, []);

  const loadOrders = useCallback(() => {
    adminApi
      .vipOrders({ status: status || undefined, page, size: PAGE_SIZE })
      .then(setOrders)
      .catch((err) => setError(err.message));
  }, [status, page]);

  useEffect(loadPlans, [loadPlans]);
  useEffect(loadOrders, [loadOrders]);
  useEffect(() => setPage(0), [status]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  function startEdit(plan) {
    setEditingId(plan.id);
    setForm({
      name: plan.name,
      months: plan.months,
      priceVnd: String(plan.priceVnd),
      description: plan.description ?? "",
      active: plan.active,
      sortOrder: plan.sortOrder,
    });
  }

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);

    const payload = {
      name: form.name,
      months: Number(form.months),
      priceVnd: Number(form.priceVnd),
      description: form.description || undefined,
      active: form.active,
      sortOrder: Number(form.sortOrder),
    };

    try {
      if (editingId) {
        await adminApi.updateVipPlan(editingId, payload);
        setNotice(`Đã lưu gói “${payload.name}”.`);
      } else {
        await adminApi.createVipPlan(payload);
        setNotice(`Đã tạo gói “${payload.name}”.`);
      }
      resetForm();
      loadPlans();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(plan) {
    setError(null);
    try {
      await adminApi.setVipPlanActive(plan.id, !plan.active);
      loadPlans();
    } catch (err) {
      setError(err.message);
    }
  }

  /** Asks the gateway what really happened to an order still sitting pending. */
  async function refreshOrder(order) {
    setRefreshing(order.orderCode);
    setError(null);
    try {
      const updated = await adminApi.refreshVipOrder(order.orderCode);
      setNotice(`Đơn ${updated.orderCode}: ${updated.statusLabel}.`);
      loadOrders();
    } catch (err) {
      setError(err.message);
    } finally {
      setRefreshing(null);
    }
  }

  const rows = orders?.content ?? [];

  return (
    <AdminPage title="Gói VIP & thanh toán">
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="admin-split">
        {/* Left: the plan being written, and the list it lands in. */}
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">{editingId ? "Sửa gói" : "Thêm gói"}</span>
          </div>

          <form className="admin-catalog-form" onSubmit={handleSubmit}>
            <Field label="Tên gói" htmlFor="plan-name">
              <TextInput
                id="plan-name"
                required
                placeholder="VIP 6 tháng"
                value={form.name}
                onChange={(event) => updateField("name", event.target.value)}
              />
            </Field>

            <div className="row" style={{ gap: "var(--space-3)", flexWrap: "nowrap" }}>
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
            </div>

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
                {editingId ? "Lưu thay đổi" : "Thêm gói"}
              </Button>
              {editingId && (
                <Button variant="ghost" onClick={resetForm}>
                  Hủy
                </Button>
              )}
            </div>
          </form>

          <div className="admin-panel-body scroll-area">
            {plans === null && <Spinner />}

            {plans?.length === 0 && (
              <EmptyState title="Chưa có gói nào">
                Thêm gói đầu tiên bằng biểu mẫu phía trên.
              </EmptyState>
            )}

            {plans?.length > 0 && (
              <table className="nb-table">
                <thead>
                  <tr>
                    <th>Gói</th>
                    <th className="admin-cell-date">Giá</th>
                    <th className="admin-cell-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr key={plan.id} className={editingId === plan.id ? "admin-row-editing" : ""}>
                      <td className="admin-cell-main">
                        <strong>{plan.name}</strong>
                        <div className="muted" style={{ fontSize: "0.85rem" }}>
                          {plan.months} tháng · {formatVnd(plan.pricePerMonth)}/tháng
                          {!plan.active && " · đã tắt"}
                        </div>
                      </td>
                      <td className="admin-cell-date tabular-num">{formatVnd(plan.priceVnd)}</td>
                      <td className="admin-cell-actions">
                        <div className="admin-row-actions">
                          <Button size="sm" onClick={() => startEdit(plan)}>
                            Sửa
                          </Button>
                          <Button size="sm" onClick={() => toggleActive(plan)}>
                            {plan.active ? "Ngừng bán" : "Bán lại"}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </aside>

        {/* Right: what those plans actually sold. */}
        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Đơn nâng cấp</span>
            {orders && (
              <span className="admin-stat">
                <b>{orders.totalElements}</b>
                <span>đơn</span>
              </span>
            )}

            <div className="admin-search">
              <Select
                aria-label="Lọc theo trạng thái đơn"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                {ORDER_FILTERS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div className="admin-panel-body scroll-area">
            {orders === null && <Spinner />}

            {orders && rows.length === 0 && (
              <EmptyState title="Chưa có đơn nào">
                Đơn nâng cấp của thành viên sẽ xuất hiện ở đây.
              </EmptyState>
            )}

            {rows.length > 0 && (
              <table className="nb-table">
                <thead>
                  <tr>
                    <th className="admin-cell-date">Mã đơn</th>
                    <th>Thành viên</th>
                    <th>Gói</th>
                    <th className="admin-cell-date">Số tiền</th>
                    <th className="admin-cell-date">Trạng thái</th>
                    <th className="admin-cell-date">Thời gian</th>
                    <th className="admin-cell-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((order) => (
                    <tr key={order.orderCode}>
                      <td className="admin-cell-date tabular-num">{order.orderCode}</td>
                      <td className="admin-cell-main">
                        <strong>{order.displayName}</strong>
                        <div className="muted" style={{ fontSize: "0.85rem" }}>
                          @{order.username}
                        </div>
                      </td>
                      <td>
                        {order.planName} · {order.months} tháng
                      </td>
                      <td className="admin-cell-date tabular-num">{formatVnd(order.amountVnd)}</td>
                      <td className="admin-cell-date">
                        <Badge tone={order.status === "PAID" ? "public" : "neutral"}>
                          {order.statusLabel}
                        </Badge>
                      </td>
                      <td className="admin-cell-date muted">
                        {formatDateTime(order.paidAt ?? order.createdAt)}
                      </td>
                      <td className="admin-cell-actions">
                        {order.status === "PENDING" && (
                          <Button
                            size="sm"
                            loading={refreshing === order.orderCode}
                            onClick={() => refreshOrder(order)}
                          >
                            Đối chiếu
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {orders && orders.totalPages > 1 && (
            <div className="admin-panel-foot">
              <Pagination page={orders.page} totalPages={orders.totalPages} onChange={setPage} />
            </div>
          )}
        </section>
      </div>
    </AdminPage>
  );
}
