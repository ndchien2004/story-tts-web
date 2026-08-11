import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { commentApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import Avatar from "./Avatar";
import ConfirmDialog from "./ConfirmDialog";
import StarRating from "./StarRating";
import { Alert, AnimatedNumber, Button, ChevronIcon, EmptyState, Spinner, TextArea } from "./ui";

const PAGE_SIZE = 10;

/** Matches the `comment-leaving` animation in components.css. */
const EXIT_MS = 280;

/** First line of a comment, for the confirmation dialog to quote back. */
function excerpt(entry) {
  if (!entry?.comment) return null;
  const text = entry.comment.trim();
  return text.length > 90 ? `“${text.slice(0, 90)}…”` : `“${text}”`;
}

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
 * Reviews and comments under a story (brief §4.6).
 *
 * Reading them needs no account — that is the point of a public discussion —
 * while posting does; the API enforces the same split, this only decides what
 * to draw.
 *
 * Laid out in two columns: the score and the compose box on the left, the
 * conversation on the right. In one column a story with a single comment left
 * a lone card adrift under a wide empty form.
 *
 * @param summary  rating average and count, shown above the compose box
 * @param onPosted lets the page refresh the average score shown elsewhere
 */
export default function CommentSection({ storyId, summary, onPosted }) {
  const { isAuthenticated } = useAuth();

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [leavingId, setLeavingId] = useState(null);
  const exitTimer = useRef(0);

  useEffect(() => () => clearTimeout(exitTimer.current), []);

  /** `silent` refetches without blanking the list behind a spinner. */
  const fetchComments = useCallback(
    async ({ silent = false } = {}) => {
      if (!silent) setLoading(true);
      try {
        const data = await commentApi.list(storyId, { page, size: PAGE_SIZE });
        setResult(data);
        setError(null);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    },
    [storyId, page],
  );

  useEffect(() => {
    fetchComments();
  }, [fetchComments]);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      await commentApi.create(storyId, {
        rating: rating || undefined,
        comment: comment.trim() || undefined,
      });
      setRating(0);
      setComment("");
      // Back to the first page, where the new entry now sits.
      if (page === 0) await fetchComments();
      else setPage(0);
      onPosted?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  /**
   * Deletes the comment the dialog is asking about.
   *
   * The card collapses first and only then do the totals move. Updating the
   * average and the count in the same frame as the click made the deletion feel
   * staged — the numbers arrived before the thing that caused them was gone.
   */
  async function confirmDelete() {
    const entry = pendingDelete;
    if (!entry) return;

    setDeleting(true);
    setError(null);

    try {
      await commentApi.remove(entry.id);
      setPendingDelete(null);

      // The collapse animates from a height CSS cannot know; hand it over
      // before the class lands. A missing node just means no collapse.
      const card = document.getElementById(`comment-${entry.id}`);
      card?.style.setProperty("--exit-height", `${card.offsetHeight}px`);
      setLeavingId(entry.id);

      exitTimer.current = setTimeout(() => {
        setLeavingId(null);
        // Drop it locally so the list never flashes the row back while the
        // refetch is in flight; the server then has the last word.
        setResult((current) =>
          current
            ? {
                ...current,
                content: current.content.filter((item) => item.id !== entry.id),
                totalElements: Math.max(0, current.totalElements - 1),
              }
            : current,
        );
        fetchComments({ silent: true });
        onPosted?.();
      }, EXIT_MS);
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const comments = result?.content ?? [];
  const canSubmit = rating > 0 || comment.trim().length > 0;

  return (
    <div className="comment-layout">
      <aside className="comment-aside">
        <div className="comment-score">
          {summary?.count > 0 ? (
            <>
              <strong className="comment-score-value">
                <AnimatedNumber value={summary.average} decimals={1} />
              </strong>
              <StarRating value={summary.average} />
              <span className="muted">
                <AnimatedNumber value={summary.count} /> lượt đánh giá
              </span>
            </>
          ) : (
            <>
              <strong className="comment-score-value">—</strong>
              <StarRating value={0} />
              <span className="muted">Chưa có đánh giá</span>
            </>
          )}
        </div>

        {isAuthenticated ? (
          <form className="comment-form" onSubmit={handleSubmit}>
            <div className="comment-form-head">
              <span className="nb-label">Chấm sao</span>
              <StarRating value={rating} onChange={setRating} />
              {rating > 0 && (
                <Button size="sm" variant="ghost" onClick={() => setRating(0)}>
                  Bỏ chọn
                </Button>
              )}
            </div>

            <TextArea
              aria-label="Bình luận"
              placeholder="Chia sẻ cảm nhận của bạn về truyện này…"
              style={{ minHeight: "6.5rem", fontFamily: "var(--font-sans)" }}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
            />

            <span className="muted" style={{ fontSize: "0.82rem" }}>
              Có thể chỉ chấm sao, chỉ bình luận, hoặc cả hai.
            </span>

            <Button type="submit" variant="primary" block loading={submitting} disabled={!canSubmit}>
              Gửi đánh giá
            </Button>
          </form>
        ) : (
          <Alert tone="info">
            <Link to="/dang-nhap">Đăng nhập</Link> để chấm sao và bình luận cho truyện này.
          </Alert>
        )}
      </aside>

      <div className="comment-main">
        {error && <Alert tone="error">{error}</Alert>}

        {/* Only the conversation scrolls. The score and the compose box beside
            it stay put, and the pager below stays reachable without hunting for
            the bottom of a long thread. */}
        <div className="comment-scroll scroll-area">
          {loading && <Spinner label="Đang tải bình luận…" />}

          {!loading && comments.length === 0 && (
            <EmptyState title="Chưa có bình luận nào">
              Hãy là người đầu tiên nêu cảm nhận về truyện này.
            </EmptyState>
          )}

          {!loading && comments.length > 0 && (
            <ul className="comment-list">
              {comments.map((entry) => (
                <li
                  key={entry.id}
                  id={`comment-${entry.id}`}
                  className={`comment-item ${leavingId === entry.id ? "comment-leaving" : ""}`}
                >
                  <Avatar src={entry.avatarUrl} name={entry.displayName} />

                  <div className="comment-body">
                    <div className="comment-meta">
                      <strong>{entry.displayName}</strong>
                      {entry.rating && <StarRating value={entry.rating} size="sm" />}
                      <span className="muted">{formatDate(entry.createdAt)}</span>

                      {entry.mine && (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="comment-delete"
                          aria-label="Xóa bình luận này"
                          onClick={() => setPendingDelete(entry)}
                        >
                          Xóa
                        </Button>
                      )}
                    </div>

                    {entry.comment && <p className="comment-text">{entry.comment}</p>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {result && result.totalPages > 1 && (
          <div className="comment-pager">
            <Button
              size="sm"
              className="nb-icon-btn"
              disabled={result.first}
              aria-label="Trang trước"
              title="Trang trước"
              onClick={() => setPage(page - 1)}
            >
              <ChevronIcon />
            </Button>

            <span className="muted tabular-num" style={{ fontWeight: 500 }}>
              {result.page + 1}/{result.totalPages}
            </span>

            <Button
              size="sm"
              className="nb-icon-btn"
              disabled={result.last}
              aria-label="Trang sau"
              title="Trang sau"
              onClick={() => setPage(page + 1)}
            >
              <ChevronIcon right />
            </Button>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa bình luận?"
        message={
          pendingDelete?.rating
            ? "Bình luận và số sao bạn đã chấm sẽ bị xóa, điểm trung bình của truyện sẽ được tính lại."
            : "Bình luận này sẽ bị xóa khỏi trang truyện."
        }
        detail={excerpt(pendingDelete) ?? "Thao tác này không thể hoàn tác."}
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
