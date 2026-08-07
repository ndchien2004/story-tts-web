import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import Pagination from "../../components/Pagination";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { useAuth } from "../../context/auth-context";
import { Alert, Badge, Button, EmptyState, Field, Spinner, TextInput } from "../../components/ui";

const PAGE_SIZE = 20;

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();

  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [savingId, setSavingId] = useState(null);

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
      const updated = await adminApi.setVip(user.id, !user.vip);
      replaceRow(updated);
      setNotice(
        updated.vip
          ? `Đã cấp quyền VIP cho "${updated.username}".`
          : `Đã thu hồi quyền VIP của "${updated.username}".`,
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  async function toggleEnabled(user) {
    setSavingId(user.id);
    setError(null);
    try {
      const updated = await adminApi.setEnabled(user.id, !user.enabled);
      replaceRow(updated);
      setNotice(
        updated.enabled
          ? `Đã mở khóa tài khoản "${updated.username}".`
          : `Đã khóa tài khoản "${updated.username}".`,
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="stack" style={{ gap: "1.5rem" }}>
      <div className="nb-section-title">
        <h1>Quản lý thành viên</h1>
      </div>

      <Alert tone="info">
        Quyền VIP được cấp thủ công tại đây. Thu hồi có hiệu lực ngay, kể cả khi thành viên đang
        đăng nhập.
      </Alert>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="nb-card">
        <Field label="Tìm thành viên" htmlFor="user-search">
          <TextInput
            id="user-search"
            type="search"
            placeholder="Username hoặc email…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </Field>
      </div>

      {loading && <Spinner />}

      {!loading && result && result.content.length === 0 && (
        <EmptyState title="Không tìm thấy thành viên nào" />
      )}

      {!loading && result && result.content.length > 0 && (
        <>
          <div className="nb-table-wrap">
            <table className="nb-table">
              <thead>
                <tr>
                  <th>Tài khoản</th>
                  <th>Email</th>
                  <th>Vai trò</th>
                  <th>VIP</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((user) => {
                  const isSelf = user.id === currentUser?.id;
                  const isAdminRow = user.role === "ADMIN";

                  return (
                    <tr key={user.id}>
                      <td>
                        <strong>{user.username}</strong>
                        {user.displayName && user.displayName !== user.username && (
                          <span className="muted"> ({user.displayName})</span>
                        )}
                      </td>
                      <td>{user.email}</td>
                      <td>
                        <Badge tone={isAdminRow ? "info" : "neutral"}>{user.role}</Badge>
                      </td>
                      <td>
                        {user.vip ? (
                          <Badge tone="vip">👑 VIP</Badge>
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                      <td>
                        {user.enabled ? (
                          <Badge tone="public">Hoạt động</Badge>
                        ) : (
                          <Badge tone="member">Bị khóa</Badge>
                        )}
                      </td>
                      <td>
                        <div className="row" style={{ gap: "0.35rem", flexWrap: "nowrap" }}>
                          <Button
                            size="sm"
                            variant={user.vip ? "danger" : "success"}
                            disabled={isAdminRow || savingId === user.id}
                            title={isAdminRow ? "Admin đã có toàn quyền" : undefined}
                            onClick={() => toggleVip(user)}
                          >
                            {user.vip ? "Thu hồi VIP" : "Cấp VIP"}
                          </Button>
                          <Button
                            size="sm"
                            disabled={isSelf || savingId === user.id}
                            title={isSelf ? "Không thể tự khóa tài khoản của mình" : undefined}
                            onClick={() => toggleEnabled(user)}
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
          </div>

          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
