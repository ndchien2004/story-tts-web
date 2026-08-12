import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import Pagination from "../../components/Pagination";
import VipPlanForm from "../../components/admin/VipPlanForm";
import { formatDateTime, formatVnd } from "../../utils/format";
import {
  Alert,
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  Select,
  Spinner,
} from "../../components/ui";

const ORDER_FILTERS = [
  { value: "", label: "Mọi trạng thái" },
  { value: "PAID", label: "Đã thanh toán" },
  { value: "PENDING", label: "Chờ thanh toán" },
  { value: "CANCELLED", label: "Đã hủy" },
  { value: "EXPIRED", label: "Quá hạn" },
];

const PAGE_SIZE = 20;

const ORDER_TONE = {
  PAID: "public",
  PENDING: "warning",
};

/**
 * Opening a VIP plan, and the orders those plans produced.
 *
 * Two things, each across the full width. The price list used to be a third
 * thing on this screen, wedged into a column beside the form that writes it —
 * three panels competing for one width meant none of them had room. It now has
 * a page of its own, one button away, which leaves the form free to spread its
 * fields into a row and the orders free to use the whole table.
 *
 * Term and price are data, not constants in the code: opening a six-month plan
 * or running a holiday price is a form, not a deploy.
 */
export default function AdminVipPage() {
  const [planCount, setPlanCount] = useState(null);

  const [orders, setOrders] = useState(null);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [refreshing, setRefreshing] = useState(null);

  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  /** Only the count is needed here; the plans themselves live on their own page. */
  const loadPlanCount = useCallback(() => {
    adminApi
      .vipPlans()
      .then((plans) => setPlanCount(plans.length))
      .catch(() => setPlanCount(null));
  }, []);

  const loadOrders = useCallback(() => {
    adminApi
      .vipOrders({ status: status || undefined, page, size: PAGE_SIZE })
      .then(setOrders)
      .catch((err) => setError(err.message));
  }, [status, page]);

  useEffect(loadPlanCount, [loadPlanCount]);
  useEffect(loadOrders, [loadOrders]);
  useEffect(() => setPage(0), [status]);

  function handlePlanCreated(message) {
    setNotice(message);
    setError(null);
    loadPlanCount();
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

      <div className="admin-vip-stack">
        {/* Full width, so the fields sit in a row instead of a column. */}
        <section className="admin-panel admin-vip-new">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Thêm gói VIP</span>

            {/* The way to everything already on sale. Editing lives over there
                too, next to the plan being changed. */}
            <div className="admin-panel-head-end">
              <ButtonLink to="/admin/vip/goi" size="sm">
                Xem tất cả các gói
                {planCount !== null && <Badge tone="neutral">{planCount}</Badge>}
              </ButtonLink>
            </div>
          </div>

          <div className="admin-panel-body admin-panel-body-pad">
            <VipPlanForm wide onDone={handlePlanCreated} onError={setError} />
          </div>
        </section>

        {/* What those plans actually sold. The one list here that grows without
            end, so the one that scrolls. */}
        <section className="admin-panel admin-vip-orders">
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
                        <Badge tone={ORDER_TONE[order.status] ?? "neutral"}>
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
