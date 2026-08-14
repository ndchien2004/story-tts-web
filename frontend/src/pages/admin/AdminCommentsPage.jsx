import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, catalogApi, commentApi, storyApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import Avatar from "../../components/Avatar";
import ConfirmDialog from "../../components/ConfirmDialog";
import Modal from "../../components/Modal";
import Pagination from "../../components/Pagination";
import StarRating from "../../components/StarRating";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { Alert, Button, EmptyState, SearchInput, Select, Spinner } from "../../components/ui";

const PAGE_SIZE = 20;

/** Enough stories to pick from without paging the filter itself. */
const STORY_CHOICES = 200;

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Moderation: every rating and comment on the site, newest first.
 *
 * The permission to remove anyone's comment already existed in the service —
 * what was missing was any way to *find* one. Reaching a comment meant knowing
 * which story it was under and paging through that story's thread.
 *
 * The list narrows the way a moderator thinks: genre, then story, then words.
 * Each row is a single line — who, how many stars, the opening of what they
 * wrote — and the whole record opens in a dialog, because a comment is prose
 * and prose in a table cell either wraps rows to different heights or gets cut
 * off at a width nobody chose.
 *
 * Deletion goes through the same endpoint the reader's own delete button uses,
 * because the server already decides between "mine" and "admin" there. A second
 * endpoint would be a second place for that rule to drift.
 */
export default function AdminCommentsPage() {
  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);

  const [genres, setGenres] = useState([]);
  const [genreId, setGenreId] = useState("");
  const [stories, setStories] = useState([]);
  const [storyId, setStoryId] = useState("");

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const notify = useAdminToast();

  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    catalogApi
      .genres()
      .then(setGenres)
      .catch(() => setGenres([]));
  }, []);

  /**
   * The story choices follow the chosen genre.
   *
   * The comments endpoint filters by story alone, so the genre narrows this
   * list rather than the query — which is also the more useful behaviour: it
   * turns "which story was that in" into a short list instead of a long one.
   */
  useEffect(() => {
    let cancelled = false;

    storyApi
      .list({ genreId: genreId || undefined, sort: "newest", page: 0, size: STORY_CHOICES })
      .then((data) => {
        if (!cancelled) setStories(data.content ?? []);
      })
      .catch(() => {
        if (!cancelled) setStories([]);
      });

    return () => {
      cancelled = true;
    };
  }, [genreId]);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .listComments({
        keyword: keyword || undefined,
        storyId: storyId || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [keyword, storyId, page]);

  useEffect(load, [load]);

  useEffect(() => setPage(0), [keyword, storyId]);

  /** Changing genre drops a story choice that no longer belongs to it. */
  function handleGenreChange(value) {
    setGenreId(value);
    setStoryId("");
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await commentApi.remove(pendingDelete.id);
      notify(`Đã xóa bình luận của ${pendingDelete.displayName}.`);
      setPendingDelete(null);
      setViewing(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const comments = result?.content ?? [];
  const filtered = Boolean(keyword || storyId);

  return (
    <AdminPage
      title="Kiểm duyệt bình luận"
    >
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Bình luận & đánh giá</span>
          {result && (
            <span className="admin-stat">
              <b>{result.totalElements}</b>
              <span>bình luận</span>
            </span>
          )}
        </div>

        {/* Narrowing happens under the panel head: three controls would have
            pushed the title off that bar on anything but a wide screen. The
            search takes the first line on its own — it is the control reached
            for first, and the two lists only ever narrow what it found. */}
        <div className="admin-toolbar">
          <SearchInput
            className="admin-search"
            aria-label="Tìm bình luận"
            placeholder="Tìm theo nội dung hoặc tên người viết…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />

          <div className="admin-toolbar-row">
            <Select
              aria-label="Lọc theo thể loại"
              value={genreId}
              onChange={(event) => handleGenreChange(event.target.value)}
            >
              <option value="">Mọi thể loại</option>
              {genres.map((genre) => (
                <option key={genre.id} value={genre.id}>
                  {genre.name}
                </option>
              ))}
            </Select>

            <Select
              aria-label="Lọc theo truyện"
              value={storyId}
              onChange={(event) => setStoryId(event.target.value)}
            >
              <option value="">Mọi truyện{genreId ? " trong thể loại này" : ""}</option>
              {stories.map((story) => (
                <option key={story.id} value={story.id}>
                  {story.title}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="admin-panel-body scroll-area">
          {loading && <Spinner />}

          {!loading && comments.length === 0 && (
            <EmptyState title={filtered ? "Không tìm thấy bình luận nào" : "Chưa có bình luận nào"}>
              {filtered
                ? "Thử bỏ bớt bộ lọc hoặc đổi từ khóa."
                : "Bình luận của độc giả sẽ xuất hiện ở đây."}
            </EmptyState>
          )}

          {!loading && comments.length > 0 && (
            <table className="nb-table">
              <thead>
                <tr>
                  <th>Người viết</th>
                  <th className="admin-cell-rating">Đánh giá</th>
                  <th>Nội dung</th>
                  <th className="admin-cell-date">Thời gian</th>
                  <th className="admin-cell-actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {comments.map((entry) => (
                  <tr key={entry.id}>
                    <td>
                      <div className="admin-user-cell">
                        <Avatar src={entry.avatarUrl} name={entry.displayName} size="sm" />
                        <span className="admin-user-name">
                          <strong>{entry.displayName}</strong>
                          <span className="muted">@{entry.username}</span>
                        </span>
                      </div>
                    </td>

                    {/* Its own column, at a fixed width: stars sharing a cell
                        with the text made every row a different shape. */}
                    <td className="admin-cell-rating">
                      {entry.rating ? (
                        <StarRating value={entry.rating} size="sm" />
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>

                    <td className="admin-cell-main">
                      {entry.comment ? (
                        <button
                          type="button"
                          className="admin-cell-link"
                          onClick={() => setViewing(entry)}
                        >
                          {entry.comment}
                        </button>
                      ) : (
                        <span className="muted">(chỉ chấm sao, không có nội dung)</span>
                      )}
                    </td>

                    <td className="admin-cell-date muted">{formatDate(entry.createdAt)}</td>

                    <td className="admin-cell-actions">
                      <div className="admin-row-actions">
                        <Button size="sm" onClick={() => setViewing(entry)}>
                          Xem
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          onClick={() => setPendingDelete(entry)}
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
            <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
          </div>
        )}
      </section>

      {/* The whole record, including the story it belongs to — which is why the
          list no longer needs a column for it. */}
      <Modal
        open={Boolean(viewing)}
        title="Chi tiết bình luận"
        onClose={() => setViewing(null)}
        footer={
          <>
            <Button onClick={() => setViewing(null)}>Đóng</Button>
            <Button variant="danger" onClick={() => setPendingDelete(viewing)}>
              Xóa bình luận
            </Button>
          </>
        }
      >
        {viewing && (
          <div className="stack">
            <div className="admin-user-cell">
              <Avatar src={viewing.avatarUrl} name={viewing.displayName} size="md" />
              <span className="admin-user-name">
                <strong>{viewing.displayName}</strong>
                <span className="muted">@{viewing.username}</span>
              </span>
            </div>

            <dl className="admin-meta-list">
              <div className="admin-meta-row">
                <dt className="admin-meta-key">Truyện</dt>
                <dd className="admin-meta-value">
                  <Link to={`/admin/truyen/${viewing.storyId}/chuong`}>{viewing.storyTitle}</Link>
                </dd>
              </div>
              <div className="admin-meta-row">
                <dt className="admin-meta-key">Chấm sao</dt>
                <dd className="admin-meta-value">
                  {viewing.rating ? (
                    <StarRating value={viewing.rating} size="sm" />
                  ) : (
                    <span className="muted">Không chấm sao</span>
                  )}
                </dd>
              </div>
              <div className="admin-meta-row">
                <dt className="admin-meta-key">Thời gian</dt>
                <dd className="admin-meta-value">{formatDate(viewing.createdAt)}</dd>
              </div>
            </dl>

            {viewing.comment ? (
              <p className="admin-comment-full">{viewing.comment}</p>
            ) : (
              <p className="muted">Người này chỉ chấm sao, không viết nội dung.</p>
            )}
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa bình luận?"
        message={`Bình luận của ${pendingDelete?.displayName} dưới truyện “${pendingDelete?.storyTitle}” sẽ bị xóa.`}
        detail={
          pendingDelete?.rating
            ? "Bình luận này có chấm sao, điểm trung bình của truyện sẽ được tính lại."
            : "Thao tác này không thể hoàn tác."
        }
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}
