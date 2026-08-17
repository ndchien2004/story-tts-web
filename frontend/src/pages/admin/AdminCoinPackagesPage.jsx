import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import Modal from "../../components/Modal";
import CoinPackageForm from "../../components/admin/CoinPackageForm";
import { formatCoins, formatVnd } from "../../utils/format";
import { Alert, Badge, Button, EmptyState, Spinner } from "../../components/ui";

/**
 * Bảng giá gói nạp Xu.
 *
 * <p>Cùng hình dạng với trang gói VIP, và cố ý giống: hai bảng giá làm cùng một
 * việc, nên quản trị viên không phải học hai cách sửa giá.
 *
 * <p>Gói không xóa được. Đơn hàng trỏ tới nó và người mua có quyền xem lại mình
 * đã mua gì, nên rút một gói khỏi bảng giá là một cái công tắc chứ không phải
 * một nút xóa.
 */
export default function AdminCoinPackagesPage() {
  const [packages, setPackages] = useState(null);
  const [editing, setEditing] = useState(null);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState(null);

  const [error, setError] = useState(null);
  const notify = useAdminToast();

  const load = useCallback(() => {
    adminApi
      .coinPackages()
      .then(setPackages)
      .catch((err) => {
        setPackages([]);
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

  async function toggleActive(pack) {
    setBusyId(pack.id);
    setError(null);
    try {
      await adminApi.setCoinPackageActive(pack.id, !pack.active);
      notify(pack.active ? `Đã ngừng bán gói “${pack.name}”.` : `Đã bán lại gói “${pack.name}”.`);
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
      crumbs={[{ label: "Gói nạp Xu" }]}
      title="Gói nạp Xu"
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
          {packages && (
            <span className="admin-stat">
              <b>{packages.filter((pack) => pack.active).length}</b>
              <span>đang bán</span>
            </span>
          )}
          {packages && packages.length > 0 && (
            <span className="admin-stat">
              <b>{packages.length}</b>
              <span>tổng số gói</span>
            </span>
          )}
        </div>

        <div className="admin-panel-body admin-panel-body-pad">
          {packages === null && <Spinner />}

          {packages?.length === 0 && (
            <EmptyState title="Chưa có gói nạp nào">
              Bấm “Thêm gói” ở góc trên để tạo gói đầu tiên. Chưa có gói nào thì trang nạp Xu của
              người đọc sẽ trống.
            </EmptyState>
          )}

          {packages?.length > 0 && (
            <ul className="vip-plan-grid">
              {packages.map((pack) => (
                <li
                  key={pack.id}
                  className={`vip-plan-card ${pack.active ? "" : "vip-plan-card-off"}`}
                >
                  <div className="vip-plan-card-head">
                    <strong>{pack.name}</strong>
                    {pack.active ? (
                      <Badge tone="public">Đang bán</Badge>
                    ) : (
                      <Badge tone="neutral">Đã tắt</Badge>
                    )}
                  </div>

                  {/* Giá là thứ tấm thẻ này nói, nên nó được cỡ chữ lớn nhất. */}
                  <div className="vip-plan-price tabular-num">{formatVnd(pack.priceVnd)}</div>

                  <div className="muted vip-plan-meta">
                    Nhận {formatCoins(pack.totalCoins)}
                    {pack.bonusCoins > 0 && ` · gồm ${pack.bonusCoins} Xu tặng`}
                  </div>

                  {pack.description && <p className="vip-plan-desc">{pack.description}</p>}

                  <div className="muted vip-plan-meta">Thứ tự hiển thị: {pack.sortOrder}</div>

                  <div className="vip-plan-card-actions">
                    <Button size="sm" variant="primary" onClick={() => setEditing(pack)}>
                      Sửa
                    </Button>
                    <Button size="sm" loading={busyId === pack.id} onClick={() => toggleActive(pack)}>
                      {pack.active ? "Ngừng bán" : "Bán lại"}
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
        title={editing ? `Sửa gói “${editing.name}”` : "Thêm gói nạp Xu"}
        onClose={closeDialog}
      >
        <CoinPackageForm
          // Một biểu mẫu mới cho mỗi gói, để mở lại không bao giờ hiện giá trị
          // của gói mở lần trước.
          key={editing?.id ?? "new"}
          pack={editing}
          onDone={handleDone}
          onError={setError}
          onCancel={closeDialog}
        />
      </Modal>
    </AdminPage>
  );
}
