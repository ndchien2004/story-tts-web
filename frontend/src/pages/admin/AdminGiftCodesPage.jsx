import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { XU_TABS } from "./AdminCoinPackagesPage";
import { useAdminToast } from "../../context/admin-toast-context";
import ConfirmDialog from "../../components/ConfirmDialog";
import Modal from "../../components/Modal";
import Pagination from "../../components/Pagination";
import GiftCodeForm from "../../components/admin/GiftCodeForm";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { formatCoins, formatDateTime } from "../../utils/format";
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  FilterChips,
  SearchInput,
  Spinner,
} from "../../components/ui";

const PAGE_SIZE = 15;
const REDEMPTION_PAGE_SIZE = 10;

/**
 * Nhãn màu cho từng tình trạng.
 *
 * <p>Bốn tình trạng "không đổi được" cố ý không dùng chung một màu: chúng khác
 * nhau ở việc cần làm gì tiếp. Hết hạn và hết lượt là chuyện đã xong; chờ giờ là
 * chuyện sắp tới; đã tắt là một quyết định có thể rút lại bằng một cú bấm.
 */
const STATUS_TONE = {
  ACTIVE: "public",
  SCHEDULED: "info",
  EXPIRED: "neutral",
  DISABLED: "member",
  EXHAUSTED: "warning",
};

const STATUS_FILTERS = [
  { value: "", label: "Tất cả" },
  { value: "ACTIVE", label: "Đang phát" },
  { value: "SCHEDULED", label: "Chờ tới giờ" },
  { value: "EXPIRED", label: "Hết hạn" },
  { value: "EXHAUSTED", label: "Hết lượt" },
  { value: "DISABLED", label: "Đã tắt" },
];

/**
 * Quản lý gift code.
 *
 * <h3>Vì sao là bảng chứ không phải lưới thẻ như bảng giá gói nạp</h3>
 * Một gói nạp là một cái giá kèm vài chi tiết — đọc lướt là xong, nên nó hợp với
 * một tấm thẻ. Một gift code thì mang bảy con số phải <i>so với nhau</i>: đã đổi
 * bao nhiêu trên tối đa bao nhiêu, bắt đầu lúc nào so với hết hạn lúc nào. So
 * sánh theo cột là việc của bảng; rải chúng ra thành thẻ thì mỗi lần muốn biết
 * mã nào sắp hết lượt phải đọc từng cái một.
 *
 * <h3>Tình trạng do máy chủ nói</h3>
 * Trang này không tự suy ra ACTIVE hay EXPIRED từ hai mốc thời gian. Suy ở đây
 * nghĩa là dùng đồng hồ của máy người xem, và một chiếc máy đặt sai giờ sẽ hiện
 * "Đang phát" cho đúng cái mã mà máy chủ từ chối.
 */
export default function AdminGiftCodesPage() {
  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);

  // Lọc theo *ngày tạo*, không theo hạn dùng: câu hỏi ở hai ô này là "những mã
  // tôi phát ra tuần trước", không phải "những mã hết hạn tuần trước".
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const [sort, setSort] = useState({ column: "createdAt", direction: "desc" });

  const [result, setResult] = useState(null);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const notify = useAdminToast();

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .giftCodes({
        keyword: keyword || undefined,
        status: status || undefined,
        // Ô ngày cho một ngày trần; đầu dưới lấy từ 00:00 và đầu trên tới
        // 23:59:59, nếu không thì chọn "đến hôm nay" sẽ bỏ mất mọi mã tạo
        // trong hôm nay.
        from: dayStart(from),
        to: dayEnd(to),
        sort: sort.column,
        direction: sort.direction,
        page,
        size: PAGE_SIZE,
      })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [keyword, status, from, to, sort, page]);

  const loadStats = useCallback(() => {
    adminApi.giftCodeStats().then(setStats).catch(() => setStats(null));
  }, []);

  useEffect(load, [load]);
  useEffect(loadStats, [loadStats]);

  // Đổi bộ lọc hay đổi cách sắp thì trang 3 của kết quả cũ không còn nghĩa gì.
  useEffect(() => setPage(0), [keyword, status, from, to, sort]);

  /** Bấm lại đúng cột đang sắp thì đảo chiều; cột khác thì bắt đầu từ giảm dần. */
  function toggleSort(column) {
    setSort((current) =>
      current.column === column
        ? { column, direction: current.direction === "asc" ? "desc" : "asc" }
        : { column, direction: "desc" },
    );
  }

  function closeDialog() {
    setCreating(false);
    setEditing(null);
  }

  function handleDone(message) {
    notify(message);
    setError(null);
    closeDialog();
    load();
    loadStats();
  }

  async function toggleEnabled(code) {
    setBusyId(code.id);
    setError(null);
    try {
      await adminApi.setGiftCodeEnabled(code.id, !code.enabled);
      notify(
        code.enabled ? `Đã tắt gift code “${code.code}”.` : `Đã bật lại gift code “${code.code}”.`,
      );
      load();
      loadStats();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  async function remove(code) {
    setBusyId(code.id);
    setError(null);
    try {
      await adminApi.deleteGiftCode(code.id);
      notify(`Đã xóa gift code “${code.code}”.`);
      load();
      loadStats();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
      setPendingDelete(null);
    }
  }

  const codes = result?.content ?? [];
  const dialogOpen = creating || editing !== null;

  return (
    <AdminPage
      crumbs={[{ to: "/admin/xu/goi", label: "Gói nạp Xu" }, { label: "Gift code" }]}
      title="Gift code"
      tabs={XU_TABS}
      actions={
        <Button variant="primary" size="sm" onClick={() => setCreating(true)}>
          Tạo gift code
        </Button>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Danh sách mã</span>

          {/* Bốn con số của cả hệ thống, không phải của trang đang xem: một bộ
              lọc đang bật không được làm "tổng Xu đã phát" nhỏ đi. */}
          {stats && (
            <>
              <span className="admin-stat">
                <b>{stats.totalCodes}</b>
                <span>mã</span>
              </span>
              <span className="admin-stat">
                <b>{stats.activeCodes}</b>
                <span>đang phát</span>
              </span>
              <span className="admin-stat">
                <b>{stats.totalRedemptions}</b>
                <span>lượt đổi</span>
              </span>
              <span className="admin-stat">
                <b>{stats.totalCoins.toLocaleString("vi-VN")}</b>
                <span>Xu đã phát</span>
              </span>
            </>
          )}

          <SearchInput
            className="admin-search"
            aria-label="Tìm gift code"
            placeholder="Tìm theo mã hoặc ghi chú…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>

        <div className="admin-panel-head admin-panel-head-wrap">
          <FilterChips
            label="Tình trạng"
            options={STATUS_FILTERS}
            value={status}
            onChange={setStatus}
          />

          {/* Khoảng ngày tạo. Hai ô ngày trần chứ không phải ô ngày-giờ: quản
              trị viên lọc theo ngày, và bắt họ chọn cả phút chỉ để xem "tuần
              trước" là bắt gõ thêm bốn chữ số không dùng tới. */}
          <label className="admin-daterange">
            <span className="muted">Tạo từ</span>
            <input
              type="date"
              className="nb-input admin-date-input"
              value={from}
              max={to || undefined}
              onChange={(event) => setFrom(event.target.value)}
            />
            <span className="muted">đến</span>
            <input
              type="date"
              className="nb-input admin-date-input"
              value={to}
              min={from || undefined}
              onChange={(event) => setTo(event.target.value)}
            />
            {(from || to) && (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setFrom("");
                  setTo("");
                }}
              >
                Xóa lọc
              </Button>
            )}
          </label>
        </div>

        <div className="admin-panel-body scroll-area">
          {loading && <Spinner />}

          {!loading && codes.length === 0 && (
            <EmptyState title="Không có gift code nào">
              Bấm “Tạo gift code” ở góc trên. Mã có thể hẹn giờ: đặt mốc bắt đầu ở tương lai thì nó
              nằm sẵn trong hệ thống và tự mở đúng giờ.
            </EmptyState>
          )}

          {!loading && codes.length > 0 && (
            <table className="nb-table">
              <thead>
                <tr>
                  <SortHeader column="code" sort={sort} onSort={toggleSort}>
                    Mã
                  </SortHeader>
                  <SortHeader column="coinAmount" sort={sort} onSort={toggleSort}>
                    Xu
                  </SortHeader>
                  <SortHeader column="startAt" sort={sort} onSort={toggleSort}>
                    Bắt đầu
                  </SortHeader>
                  <SortHeader column="endAt" sort={sort} onSort={toggleSort}>
                    Kết thúc
                  </SortHeader>
                  <SortHeader column="usedCount" sort={sort} onSort={toggleSort}>
                    Đã đổi
                  </SortHeader>
                  {/* Tình trạng không phải một cột trong bảng — nó được suy ra —
                      nên nó lọc được nhưng không sắp được. */}
                  <th>Tình trạng</th>
                  <SortHeader column="createdAt" sort={sort} onSort={toggleSort}>
                    Tạo lúc
                  </SortHeader>
                  <th className="admin-cell-actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {codes.map((code) => (
                  <tr key={code.id}>
                    <td className="admin-cell-main">
                      <strong className="gift-code-cell">{code.code}</strong>
                      {code.description && (
                        <>
                          <br />
                          <span className="muted">{code.description}</span>
                        </>
                      )}
                    </td>
                    <td className="tabular-num">{formatCoins(code.coinAmount)}</td>
                    <td className="admin-cell-date">
                      {code.startAt ? formatDateTime(code.startAt) : <span className="muted">Ngay</span>}
                    </td>
                    <td className="admin-cell-date">
                      {code.endAt ? formatDateTime(code.endAt) : <span className="muted">Không hạn</span>}
                    </td>
                    <td className="tabular-num">
                      {code.usedCount}
                      {code.maxUses == null ? (
                        <span className="muted"> / ∞</span>
                      ) : (
                        <span className="muted"> / {code.maxUses}</span>
                      )}
                    </td>
                    <td>
                      <Badge tone={STATUS_TONE[code.status] ?? "neutral"}>{code.statusLabel}</Badge>
                    </td>
                    <td className="admin-cell-date">{formatDateTime(code.createdAt)}</td>
                    <td className="admin-cell-actions">
                      <div className="admin-row-actions">
                        <Button
                          size="sm"
                          disabled={code.usedCount === 0}
                          title={
                            code.usedCount === 0
                              ? "Chưa ai đổi mã này"
                              : "Xem danh sách tài khoản đã đổi"
                          }
                          onClick={() => setViewing(code)}
                        >
                          Lượt đổi
                        </Button>
                        <Button size="sm" variant="primary" onClick={() => setEditing(code)}>
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          loading={busyId === code.id}
                          onClick={() => toggleEnabled(code)}
                        >
                          {code.enabled ? "Tắt" : "Bật"}
                        </Button>
                        {/* Chỉ mã chưa ai đổi mới xóa được — máy chủ cũng từ
                            chối, và lý do nằm ở nút bị vô hiệu này. */}
                        <Button
                          size="sm"
                          variant="danger"
                          disabled={code.usedCount > 0}
                          title={
                            code.usedCount > 0
                              ? "Đã có người đổi — lịch sử Xu của họ trỏ về mã này. Hãy tắt thay vì xóa."
                              : "Xóa mã"
                          }
                          onClick={() => setPendingDelete(code)}
                        >
                          Xóa
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
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

      <Modal
        open={dialogOpen}
        title={editing ? `Sửa gift code “${editing.code}”` : "Tạo gift code"}
        onClose={closeDialog}
      >
        <GiftCodeForm
          // Một biểu mẫu mới cho mỗi mã, để mở lại không bao giờ hiện cấu hình
          // của mã mở lần trước.
          key={editing?.id ?? "new"}
          giftCode={editing}
          onDone={handleDone}
          onError={setError}
          onCancel={closeDialog}
        />
      </Modal>

      <RedemptionsDialog code={viewing} onClose={() => setViewing(null)} />

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa gift code?"
        message={`Mã “${pendingDelete?.code}” sẽ biến mất khỏi hệ thống.`}
        detail="Chỉ xóa được mã chưa ai đổi. Mã đã phát Xu thì hãy tắt để giữ lại lịch sử."
        confirmLabel="Xóa mã"
        busy={busyId === pendingDelete?.id}
        onConfirm={() => remove(pendingDelete)}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}

/**
 * Một ô tiêu đề bấm được để đổi cách sắp.
 *
 * <p>Cả cột đang sắp và chiều sắp đều nói ra hai lần: bằng mũi tên cho người
 * nhìn thấy, và bằng {@code aria-sort} cho người dùng trình đọc màn hình. Chỉ
 * một trong hai là bỏ sót một nửa số người đọc cái bảng này.
 *
 * <p>Tên cột gửi lên máy chủ đi qua một danh sách trắng ở
 * {@code AdminGiftCodeController} — nó rơi thẳng vào mệnh đề ORDER BY, nên một
 * chuỗi tùy ý ở đây là một lỗi 500 cho mọi giá trị không phải tên thuộc tính.
 */
function SortHeader({ column, sort, onSort, children }) {
  const active = sort.column === column;
  return (
    <th
      aria-sort={active ? (sort.direction === "asc" ? "ascending" : "descending") : "none"}
    >
      <button type="button" className="admin-sort-btn" onClick={() => onSort(column)}>
        {children}
        <span className="admin-sort-arrow" aria-hidden="true">
          {active ? (sort.direction === "asc" ? "▲" : "▼") : "↕"}
        </span>
      </button>
    </th>
  );
}

/**
 * Ô ngày (`YYYY-MM-DD`) → mốc ISO đầu ngày theo giờ địa phương, hoặc undefined.
 *
 * <p>`new Date("2026-08-20")` được đọc là nửa đêm **UTC**, không phải nửa đêm ở
 * chỗ người dùng đang ngồi — chênh 7 tiếng với giờ Việt Nam, đủ để một mã tạo
 * lúc 6 giờ sáng rơi ra ngoài bộ lọc "từ hôm nay". Dựng ngày bằng ba con số rời
 * thì nó là nửa đêm địa phương, đúng thứ người dùng nghĩ mình vừa chọn.
 */
function dayStart(value) {
  if (!value) return undefined;
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, m - 1, d, 0, 0, 0, 0).toISOString();
}

/** Cùng phép biến đổi, nhưng tới hết ngày — xem {@link dayStart}. */
function dayEnd(value) {
  if (!value) return undefined;
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, m - 1, d, 23, 59, 59, 999).toISOString();
}

/**
 * Danh sách tài khoản đã đổi một mã.
 *
 * <p>Có phân trang, và cố ý bắt buộc: một mã phát trong sự kiện có thể có hàng
 * nghìn lượt, và nạp hết về trình duyệt để hiện mười dòng đầu là trả tiền cho
 * một thứ không ai nhìn.
 *
 * <p>Con số ở đầu hộp không lấy từ dòng của bảng ngoài mà hỏi lại máy chủ: bảng
 * ngoài mang {@code usedCount} (cột đếm), còn ở đây là {@code redemptionCount}
 * (đếm dòng thật) và {@code totalCoins} (cộng từ sổ). Chúng phải bằng nhau, và
 * chỗ này là nơi sớm nhất nhìn thấy nếu không.
 */
function RedemptionsDialog({ code, onClose }) {
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState(null);
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => setPage(0), [code?.id]);

  useEffect(() => {
    if (!code) {
      setDetail(null);
      setRows(null);
      return;
    }
    let cancelled = false;
    Promise.all([
      adminApi.giftCode(code.id),
      adminApi.giftCodeRedemptions(code.id, { page, size: REDEMPTION_PAGE_SIZE }),
    ])
      .then(([info, list]) => {
        if (cancelled) return;
        setDetail(info);
        setRows(list);
        setError(null);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [code, page]);

  return (
    <Modal open={Boolean(code)} title={`Lượt đổi — ${code?.code ?? ""}`} onClose={onClose}>
      {error && <Alert tone="error">{error}</Alert>}

      {detail && (
        <>
          <dl className="admin-meta-list">
            <div className="admin-meta-row">
              <dt className="admin-meta-key">Xu mỗi lượt</dt>
              <dd className="admin-meta-value">{formatCoins(detail.giftCode.coinAmount)}</dd>
            </div>
            <div className="admin-meta-row">
              <dt className="admin-meta-key">Đã đổi</dt>
              <dd className="admin-meta-value">
                {detail.giftCode.usedCount}
                {detail.giftCode.maxUses == null
                  ? " / không giới hạn"
                  : ` / ${detail.giftCode.maxUses}`}
              </dd>
            </div>
            <div className="admin-meta-row">
              <dt className="admin-meta-key">Còn lại</dt>
              <dd className="admin-meta-value">
                {detail.giftCode.remainingUses == null
                  ? "Không giới hạn"
                  : detail.giftCode.remainingUses}
              </dd>
            </div>
            <div className="admin-meta-row">
              <dt className="admin-meta-key">Tổng Xu đã phát</dt>
              <dd className="admin-meta-value">{formatCoins(detail.totalCoins)}</dd>
            </div>
            <div className="admin-meta-row">
              <dt className="admin-meta-key">Tình trạng</dt>
              <dd className="admin-meta-value">
                <Badge tone={STATUS_TONE[detail.giftCode.status] ?? "neutral"}>
                  {detail.giftCode.statusLabel}
                </Badge>
              </dd>
            </div>
          </dl>

          {/* Cột đếm và số dòng thật phải bằng nhau. Chúng chỉ lệch được nếu có
              ai đó sửa tay vào cơ sở dữ liệu, và im lặng về chuyện đó thì mọi
              con số phía trên đều mất nghĩa. */}
          {!detail.consistent && (
            <Alert tone="error" title="Số liệu không khớp">
              Cột đếm nói {detail.giftCode.usedCount} lượt nhưng sổ đổi mã có{" "}
              {detail.redemptionCount} dòng. Hãy kiểm tra cơ sở dữ liệu.
            </Alert>
          )}
        </>
      )}

      {!rows && !error && <Spinner label="Đang tải lượt đổi…" />}

      {rows?.content.length === 0 && (
        <EmptyState title="Chưa ai đổi mã này">
          Danh sách sẽ hiện ngay khi có tài khoản đầu tiên đổi thành công.
        </EmptyState>
      )}

      {rows && rows.content.length > 0 && (
        <>
          <table className="nb-table">
            <thead>
              <tr>
                <th>Tài khoản</th>
                <th>Xu nhận</th>
                <th>Lúc đổi</th>
              </tr>
            </thead>
            <tbody>
              {rows.content.map((row) => (
                <tr key={row.id}>
                  <td className="admin-cell-main">
                    <strong>{row.username}</strong>
                    {row.displayName && row.displayName !== row.username && (
                      <span className="muted"> ({row.displayName})</span>
                    )}
                  </td>
                  <td className="tabular-num">{formatCoins(row.coinAmount)}</td>
                  <td className="admin-cell-date">{formatDateTime(row.redeemedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {rows.totalPages > 1 && (
            <div className="admin-panel-foot">
              <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
                Trang {rows.page + 1}/{rows.totalPages}
              </span>
              <Pagination page={rows.page} totalPages={rows.totalPages} onChange={setPage} />
            </div>
          )}
        </>
      )}
    </Modal>
  );
}
