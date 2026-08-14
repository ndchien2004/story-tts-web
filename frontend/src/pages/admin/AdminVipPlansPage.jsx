import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import Modal from "../../components/Modal";
import VipPlanForm from "../../components/admin/VipPlanForm";
import { VIP_TABS } from "./AdminVipPage";
import { formatVnd } from "../../utils/format";
import { Alert, Badge, Button, EmptyState, Spinner } from "../../components/ui";

/**
 * The price list, on a page of its own.
 *
 * It used to share the VIP screen with the form that writes plans and the table
 * of orders they produced, which left the prices squeezed into a strip. Three
 * things competing for one screen meant none of them had room, so the one that
 * is browsed rather than worked in moved out here, where a plan can be a card
 * you read at a glance.
 *
 * Editing happens over the card, in a dialog holding the same form the VIP page
 * uses. That keeps the click where the plan is — no jumping back to another
 * screen to change a price — while the page underneath stays a price list.
 *
 * Plans are never deleted: orders point at them and readers may look back at
 * what they bought, so withdrawing one from sale is a switch instead.
 */
export default function AdminVipPlansPage() {
  const [plans, setPlans] = useState(null);
  const [editing, setEditing] = useState(null);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState(null);

  const [error, setError] = useState(null);
  const notify = useAdminToast();

  const load = useCallback(() => {
    adminApi
      .vipPlans()
      .then(setPlans)
      .catch((err) => {
        setPlans([]);
        setError(err.message);
      });
  }, []);

  useEffect(load, [load]);

  function closeDialog() {
    setEditing(null);
    setCreating(false);
  }

  function handleDone(message) {
    notify(message);
    setError(null);
    closeDialog();
    load();
  }

  async function toggleActive(plan) {
    setBusyId(plan.id);
    setError(null);
    try {
      await adminApi.setVipPlanActive(plan.id, !plan.active);
      notify(
        plan.active ? `Đã ngừng bán gói “${plan.name}”.` : `Đã bán lại gói “${plan.name}”.`,
      );
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  const dialogOpen = creating || editing !== null;

  return (
    <AdminPage
      crumbs={[{ to: "/admin/vip", label: "Gói VIP & thanh toán" }, { label: "Tất cả các gói" }]}
      title="Gói VIP"
      tabs={VIP_TABS}
      actions={
        <Button variant="primary" size="sm" onClick={() => setCreating(true)}>
          Thêm gói
        </Button>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Bảng giá</span>
          {plans && (
            <span className="admin-stat">
              <b>{plans.filter((plan) => plan.active).length}</b>
              <span>đang bán</span>
            </span>
          )}
          {plans && plans.length > 0 && (
            <span className="admin-stat">
              <b>{plans.length}</b>
              <span>tổng số gói</span>
            </span>
          )}
        </div>

        <div className="admin-panel-body admin-panel-body-pad">
          {plans === null && <Spinner />}

          {plans?.length === 0 && (
            <EmptyState title="Chưa có gói nào">
              Bấm “Thêm gói” ở góc trên để tạo gói đầu tiên.
            </EmptyState>
          )}

          {plans?.length > 0 && (
            <ul className="vip-plan-grid">
              {plans.map((plan) => (
                <li
                  key={plan.id}
                  className={`vip-plan-card ${plan.active ? "" : "vip-plan-card-off"}`}
                >
                  <div className="vip-plan-card-head">
                    <strong>{plan.name}</strong>
                    {plan.active ? (
                      <Badge tone="public">Đang bán</Badge>
                    ) : (
                      <Badge tone="neutral">Đã tắt</Badge>
                    )}
                  </div>

                  {/* The price is what this card is for, so it gets the size. */}
                  <div className="vip-plan-price tabular-num">{formatVnd(plan.priceVnd)}</div>

                  <div className="muted vip-plan-meta">
                    {plan.months} tháng · {formatVnd(plan.pricePerMonth)}/tháng
                  </div>

                  {plan.description && <p className="vip-plan-desc">{plan.description}</p>}

                  <div className="muted vip-plan-meta">Thứ tự hiển thị: {plan.sortOrder}</div>

                  <div className="vip-plan-card-actions">
                    <Button size="sm" variant="primary" onClick={() => setEditing(plan)}>
                      Sửa
                    </Button>
                    <Button
                      size="sm"
                      loading={busyId === plan.id}
                      onClick={() => toggleActive(plan)}
                    >
                      {plan.active ? "Ngừng bán" : "Bán lại"}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <Modal
        open={dialogOpen}
        title={editing ? `Sửa gói “${editing.name}”` : "Thêm gói VIP"}
        onClose={closeDialog}
      >
        <VipPlanForm
          // A fresh form per plan, so reopening never shows the last one's values
          // for the instant before the effect inside resets them.
          key={editing?.id ?? "new"}
          plan={editing}
          onDone={handleDone}
          onError={setError}
          onCancel={closeDialog}
        />
      </Modal>
    </AdminPage>
  );
}
