import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import ConfirmDialog from "../../components/ConfirmDialog";
import Pagination from "../../components/Pagination";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { Alert, Badge, Button, ButtonLink, EmptyState, SearchInput, Spinner } from "../../components/ui";

/** Fills the panel on a typical desktop screen without needing a scroll. */
const PAGE_SIZE = 12;

export default function AdminStoriesPage() {
  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const notify = useAdminToast();

  // The story queued for deletion; also drives the confirmation dialog.
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    storyApi
      .list({ keyword: keyword || undefined, sort: "newest", page, size: PAGE_SIZE })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [keyword, page]);

  useEffect(load, [load]);

  // Reset to the first page whenever the search term changes.
  useEffect(() => setPage(0), [keyword]);

  async function confirmDelete() {
    setDeleting(true);
    try {
      await adminApi.deleteStory(pendingDelete.id);
      notify(`Đã xóa truyện “${pendingDelete.title}”.`);
      setPendingDelete(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const stories = result?.content ?? [];

  return (
    <AdminPage
      title="Truyện, chương & audio"
      actions={
        <ButtonLink to="/admin/truyen/moi" variant="primary">
          + Thêm truyện
        </ButtonLink>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Danh sách truyện</span>
          {result && (
            <span className="admin-stat">
              <b>{result.totalElements}</b>
              <span>truyện</span>
            </span>
          )}

          <SearchInput
            className="admin-search"
            aria-label="Tìm truyện"
            placeholder="Tìm theo tên truyện hoặc tác giả…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>

        <div className="admin-panel-body scroll-area">
          {loading && <Spinner />}

          {!loading && stories.length === 0 && (
            <EmptyState title={keyword ? "Không tìm thấy truyện nào" : "Chưa có truyện nào"}>
              {keyword
                ? "Thử một từ khóa khác, hoặc xóa ô tìm kiếm để xem toàn bộ."
                : "Bấm “Thêm truyện” để tạo truyện đầu tiên."}
            </EmptyState>
          )}

          {!loading && stories.length > 0 && (
            <table className="nb-table">
              <thead>
                <tr>
                  <th>Truyện</th>
                  <th>Tác giả</th>
                  <th>Thể loại</th>
                  <th>Trạng thái</th>
                  <th>Chương</th>
                  <th className="admin-cell-actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {stories.map((story) => (
                  <tr key={story.id}>
                    <td className="admin-cell-main">
                      <div className="admin-title-cell">
                        <span className="admin-thumb" aria-hidden="true">
                          {story.coverImage ? (
                            <img src={story.coverImage} alt="" />
                          ) : (
                            story.title.slice(0, 1).toUpperCase()
                          )}
                        </span>
                        {/* The title leads to this story's chapters, the one
                            place an admin goes next — not out to the public
                            page, which the console has no use for. */}
                        <span>
                          <Link
                            className="admin-title-link"
                            to={`/admin/truyen/${story.id}/chuong`}
                            title="Quản lý chương của truyện này"
                          >
                            {story.title}
                          </Link>
                        </span>
                      </div>
                    </td>
                    <td>{story.author?.name ?? "—"}</td>
                    <td>{story.genre?.name ?? "—"}</td>
                    <td>
                      <Badge tone={story.status === "COMPLETED" ? "public" : "neutral"}>
                        {story.statusLabel}
                      </Badge>
                    </td>
                    <td>{story.chapterCount}</td>
                    <td className="admin-cell-actions">
                      <div className="admin-row-actions">
                        <ButtonLink
                          to={`/admin/truyen/${story.id}/chuong`}
                          size="sm"
                          variant="info"
                        >
                          Quản lý chương
                        </ButtonLink>
                        <ButtonLink to={`/admin/truyen/${story.id}`} size="sm">
                          Sửa
                        </ButtonLink>
                        <Button size="sm" variant="danger" onClick={() => setPendingDelete(story)}>
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

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa truyện?"
        message={`Truyện “${pendingDelete?.title}” sẽ bị xóa khỏi hệ thống.`}
        detail="Toàn bộ chương và file audio thuộc truyện này cũng bị xóa theo, không khôi phục được."
        confirmLabel="Xóa truyện"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}
