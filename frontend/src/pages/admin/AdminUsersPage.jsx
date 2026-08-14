import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import ConfirmDialog from "../../components/ConfirmDialog";
import Pagination from "../../components/Pagination";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { useAuth } from "../../context/auth-context";
import { formatDate } from "../../utils/format";
import { Alert, Badge, Button, EmptyState, SearchInput, Spinner } from "../../components/ui";

const PAGE_SIZE = 15;

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();

  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const notify = useAdminToast();
  const [savingId, setSavingId] = useState(null);

  // Locking someone out is the only irreversible-feeling action here, so it is
  // the only one that asks first.
  const [pendingLock, setPendingLock] = useState(null);

  // Handing out the console keys deserves the same pause.
  const [pendingRole, setPendingRole] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .listUsers({ keyword: keyword || undefined, page, size: PAGE_SIZE })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [keyword, page]);

  useEffect(load, [load]);
  useEffect(() => setPage(0), [keyword]);

  /** Replaces just the changed row so the table does not flash on every toggle. */
  function replaceRow(updated) {
    setResult((current) =>
      current
        ? {
            ...current,
            content: current.content.map((row) => (row.id === updated.id ? updated : row)),
          }
        : current,
    );
  }

  async function toggleVip(user) {
    setSavingId(user.id);
    setError(null);
    try {
      const updated = await adminApi.setVip(user.id, !user.vipGranted);
      replaceRow(updated);
      notify(
        updated.vipGranted
          ? `Đã cấp quyền VIP vĩnh viễn cho “${updated.username}”.`
          : updated.vip
            ? `Đã thu hồi VIP vĩnh viễn của “${updated.username}”; gói đã mua vẫn còn hạn.`
            : `Đã thu hồi quyền VIP của “${updated.username}”.`,
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  async function setEnabled(user, value) {
    setSavingId(user.id);
    setError(null);
    try {
      const updated = await adminApi.setEnabled(user.id, value);
      replaceRow(updated);
      notify(
        updated.enabled
          ? `Đã mở khóa tài khoản “${updated.username}”.`
          : `Đã khóa tài khoản “${updated.username}”.`,
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
      setPendingLock(null);
    }
  }

  /**
   * Promotes to ADMIN or drops back to MEMBER.
   *
   * The server refuses to let an admin change their own role or to demote the
   * last one; both would lock the console from the inside. The button is
   * disabled for the first case, and the message covers the second.
   */
  async function changeRole(user, role) {
    setSavingId(user.id);
    setError(null);
    try {
      const updated = await adminApi.setRole(user.id, role);
      replaceRow(updated);
      notify(
        role === "ADMIN"
          ? `Đã cấp quyền quản trị cho “${updated.username}”.`
          : `Đã hạ “${updated.username}” về thành viên thường.`,
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
      setPendingRole(null);
    }
  }

  const users = result?.content ?? [];

  return (
    <AdminPage
      title="Thành viên"
    >
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Danh sách tài khoản</span>
          {result && (
            <span className="admin-stat">
              <b>{result.totalElements}</b>
              <span>tài khoản</span>
            </span>
          )}

          <SearchInput
            className="admin-search"
            aria-label="Tìm thành viên"
            placeholder="Tìm theo username hoặc email…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>

        <div className="admin-panel-body scroll-area">
          {loading && <Spinner />}

          {!loading && users.length === 0 && (
            <EmptyState title="Không tìm thấy thành viên nào">
              Quyền VIP được cấp thủ công tại đây; thu hồi có hiệu lực ngay cả khi thành viên đang
              đăng nhập.
            </EmptyState>
          )}

          {!loading && users.length > 0 && (
            <table className="nb-table">
              <thead>
                <tr>
                  <th>Tài khoản</th>
                  <th>Email</th>
                  <th>Vai trò</th>
                  <th>VIP</th>
                  <th>Trạng thái</th>
                  <th className="admin-cell-actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const isSelf = user.id === currentUser?.id;
                  const isAdminRow = user.role === "ADMIN";

                  return (
                    <tr key={user.id}>
                      <td className="admin-cell-main">
                        <strong>{user.username}</strong>
                        {user.displayName && user.displayName !== user.username && (
                          <span className="muted"> ({user.displayName})</span>
                        )}
                        {isSelf && (
                          <>
                            {" "}
                            <Badge tone="info">Bạn</Badge>
                          </>
                        )}
                      </td>
                      <td>{user.email}</td>
                      <td>
                        <Badge tone={isAdminRow ? "info" : "neutral"}>{user.role}</Badge>
                      </td>
                      {/* Two sources of VIP, and the difference matters: the
                          button below only touches the manual grant, so a paid
                          subscription has to be visible as its own thing. */}
                      <td className="admin-cell-date">
                        {user.vipGranted && <Badge tone="vip">VIP vĩnh viễn</Badge>}
                        {!user.vipGranted && user.vip && (
                          <Badge tone="neutral">Gói đến {formatDate(user.vipUntil)}</Badge>
                        )}
                        {!user.vip && <span className="muted">—</span>}
                      </td>
                      <td>
                        {user.enabled ? (
                          <Badge tone="public">Hoạt động</Badge>
                        ) : (
                          <Badge tone="member">Bị khóa</Badge>
                        )}
                      </td>
                      <td className="admin-cell-actions">
                        <div className="admin-row-actions">
                          <Button
                            size="sm"
                            variant={user.vipGranted ? "danger" : "success"}
                            disabled={isAdminRow || savingId === user.id}
                            title={
                              isAdminRow
                                ? "Admin đã có toàn quyền"
                                : "Cấp VIP vĩnh viễn; không ảnh hưởng tới gói đã mua"
                            }
                            onClick={() => toggleVip(user)}
                          >
                            {user.vipGranted ? "Thu hồi VIP" : "Cấp VIP"}
                          </Button>
                          <Button
                            size="sm"
                            variant={isAdminRow ? "default" : "violet"}
                            disabled={isSelf || savingId === user.id}
                            title={
                              isSelf
                                ? "Không thể tự đổi quyền của chính mình"
                                : isAdminRow
                                  ? "Hạ về thành viên thường"
                                  : "Cấp quyền vào bảng quản trị"
                            }
                            onClick={() =>
                              setPendingRole({ user, role: isAdminRow ? "MEMBER" : "ADMIN" })
                            }
                          >
                            {isAdminRow ? "Hạ quyền" : "Cấp quyền Admin"}
                          </Button>
                          <Button
                            size="sm"
                            disabled={isSelf || savingId === user.id}
                            title={isSelf ? "Không thể tự khóa tài khoản của mình" : undefined}
                            onClick={() =>
                              user.enabled ? setPendingLock(user) : setEnabled(user, true)
                            }
                          >
                            {user.enabled ? "Khóa" : "Mở khóa"}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {result && result.totalPages > 1 && (
          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              Trang {result.page + 1}/{result.totalPages}
            </span>
            <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
          </div>
        )}
      </section>

      <ConfirmDialog
        open={Boolean(pendingLock)}
        title="Khóa tài khoản?"
        message={`Tài khoản “${pendingLock?.username}” sẽ không đăng nhập được nữa.`}
        detail="Bạn có thể mở khóa lại bất cứ lúc nào từ chính bảng này."
        confirmLabel="Khóa tài khoản"
        busy={savingId === pendingLock?.id}
        onConfirm={() => setEnabled(pendingLock, false)}
        onCancel={() => setPendingLock(null)}
      />

      <ConfirmDialog
        open={Boolean(pendingRole)}
        title={pendingRole?.role === "ADMIN" ? "Cấp quyền quản trị?" : "Hạ quyền quản trị?"}
        message={
          pendingRole?.role === "ADMIN"
            ? `“${pendingRole?.user.username}” sẽ vào được bảng quản trị và sửa được mọi nội dung.`
            : `“${pendingRole?.user.username}” sẽ trở lại thành viên thường và không vào bảng quản trị được nữa.`
        }
        detail={
          pendingRole?.role === "ADMIN"
            ? "Quyền quản trị bao gồm cả xóa truyện và khóa tài khoản người khác. Cờ VIP sẽ được gỡ vì Admin vốn đọc được mọi chương."
            : "Máy chủ sẽ từ chối nếu đây là quản trị viên cuối cùng."
        }
        confirmLabel={pendingRole?.role === "ADMIN" ? "Cấp quyền" : "Hạ quyền"}
        busy={savingId === pendingRole?.user.id}
        onConfirm={() => changeRole(pendingRole.user, pendingRole.role)}
        onCancel={() => setPendingRole(null)}
      />
    </AdminPage>
  );
}
