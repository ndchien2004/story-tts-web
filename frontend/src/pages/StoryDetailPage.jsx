import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { favoriteApi, storyApi } from "../api/endpoints";
import ChapterRow from "../components/ChapterRow";
import CommentSection from "../components/CommentSection";
import StarRating from "../components/StarRating";
import { useAuth } from "../context/auth-context";
import {
  Alert,
  AnimatedNumber,
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  Spinner,
} from "../components/ui";

const TABS = [
  { id: "chapters", label: "Danh sách chương" },
  { id: "comments", label: "Đánh giá & bình luận" },
];

/**
 * Story landing page.
 *
 * Sized to the viewport rather than stacked down it: a fixed identity column on
 * the left — cover, the actions and the facts — and a tabbed pane on the right
 * that scrolls inside itself. The chapter list and the comments would each be
 * long enough to bury everything else if they shared one vertical flow.
 */
export default function StoryDetailPage() {
  const { storyId } = useParams();
  const { isAuthenticated } = useAuth();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState("chapters");
  const [savingFavorite, setSavingFavorite] = useState(false);

  const load = useCallback(() => {
    let cancelled = false;
    setLoading(true);

    storyApi
      .detail(storyId)
      .then((detail) => {
        if (!cancelled) {
          setData(detail);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [storyId]);

  useEffect(load, [load]);

  /** Refreshes just the rating summary after the reader posts or deletes one. */
  const refreshRating = useCallback(() => {
    storyApi
      .detail(storyId)
      .then((detail) => setData((current) => (current ? { ...current, rating: detail.rating } : current)))
      .catch(() => {});
  }, [storyId]);

  /**
   * Marks or unmarks the story, flipping the button before the request lands.
   *
   * Waiting for the server meant the button spent a moment disabled: it lost
   * its shadow, dropped a pixel and sprang back once the answer arrived, so a
   * single click made the whole box jump twice. The state is ours to guess —
   * one row in one table — and the server's answer still overwrites it.
   */
  async function toggleFavorite() {
    if (savingFavorite) return;

    const previous = data.favorite;
    const guess = {
      favorite: !previous.favorite,
      count: Math.max(0, previous.count + (previous.favorite ? -1 : 1)),
    };

    setData((current) => (current ? { ...current, favorite: guess } : current));
    setSavingFavorite(true);

    try {
      const status = await favoriteApi.toggle(storyId);
      setData((current) => (current ? { ...current, favorite: status } : current));
    } catch (err) {
      setData((current) => (current ? { ...current, favorite: previous } : current));
      setError(err.message);
    } finally {
      setSavingFavorite(false);
    }
  }

  const readIds = useMemo(() => new Set(data?.readChapterIds ?? []), [data]);

  if (loading) return <Spinner />;
  if (error && !data) return <Alert tone="error">{error}</Alert>;
  if (!data) return null;

  const { story, chapters, rating, favorite, resumeChapterId } = data;

  const firstReadable = chapters.find((chapter) => !chapter.locked) ?? chapters[0];
  const resumeChapter = resumeChapterId
    ? chapters.find((chapter) => chapter.id === resumeChapterId)
    : null;
  const audioCount = chapters.filter((chapter) => chapter.hasAudio).length;

  return (
    <div className="story-detail">
      <aside className="story-detail-aside">
        {story.coverImage ? (
          <img className="story-detail-cover" src={story.coverImage} alt={`Bìa ${story.title}`} />
        ) : (
          <div className="story-detail-cover story-detail-cover-placeholder" aria-hidden="true">
            {story.title.slice(0, 1).toUpperCase()}
          </div>
        )}

        <div className="stack" style={{ gap: "var(--space-2)" }}>
          {/* "Đọc tiếp" replaces "Đọc từ đầu" once there is somewhere to return
              to, so the primary button is always the one that moves the reader
              forward rather than back to chapter one. */}
          {resumeChapter ? (
            <ButtonLink to={`/chuong/${resumeChapter.id}`} variant="primary" size="lg" block>
              Đọc tiếp chương {resumeChapter.chapterNumber}
            </ButtonLink>
          ) : (
            firstReadable && (
              <ButtonLink to={`/chuong/${firstReadable.id}`} variant="primary" size="lg" block>
                Đọc từ đầu
              </ButtonLink>
            )
          )}

          {isAuthenticated && (
            <Button
              block
              className="favorite-btn"
              variant={favorite.favorite ? "danger" : "default"}
              aria-pressed={favorite.favorite}
              aria-busy={savingFavorite || undefined}
              onClick={toggleFavorite}
            >
              {/* The heart sits in a slot of its own width so swapping the
                  outline for the fill cannot nudge the label sideways. */}
              <span className="favorite-heart" aria-hidden="true">
                {favorite.favorite ? "♥" : "♡"}
              </span>
              {favorite.favorite ? "Đã yêu thích" : "Yêu thích"}
            </Button>
          )}
        </div>

        {/* Facts as chips on a couple of rows rather than six stacked rows —
            that list on its own was pushing the page past the viewport. */}
        <div className="story-chips">
          <span className="story-chip">
            <b>{story.author?.name ?? "Chưa rõ"}</b>
            <span>Tác giả</span>
          </span>
          <span className="story-chip">
            <b>{story.genre?.name ?? "—"}</b>
            <span>Thể loại</span>
          </span>
          <span className="story-chip">
            <b>{story.chapterCount}</b>
            <span>Chương</span>
          </span>
          <span className="story-chip">
            <b>
              {audioCount}/{chapters.length}
            </b>
            <span>Có audio</span>
          </span>
          <span className="story-chip">
            <b>{story.viewCount.toLocaleString("vi-VN")}</b>
            <span>Lượt xem</span>
          </span>
          <span className="story-chip">
            <b>
              <AnimatedNumber value={favorite.count} />
            </b>
            <span>Yêu thích</span>
          </span>
        </div>
      </aside>

      <div className="story-detail-main">
        <header className="story-detail-head">
          <div className="row" style={{ gap: "0.5rem" }}>
            <Badge tone={story.status === "COMPLETED" ? "public" : "neutral"}>
              {story.statusLabel}
            </Badge>

            {rating.count > 0 ? (
              <span className="row" style={{ gap: "0.4rem" }}>
                <StarRating value={rating.average} size="sm" />
                <strong>
                  <AnimatedNumber value={rating.average} decimals={1} />
                </strong>
                <span className="muted">
                  (<AnimatedNumber value={rating.count} /> đánh giá)
                </span>
              </span>
            ) : (
              <span className="muted">Chưa có đánh giá</span>
            )}
          </div>

          <h1>{story.title}</h1>

          {story.description ? (
            <p className="story-detail-description">{story.description}</p>
          ) : (
            <p className="muted">Truyện này chưa có phần giới thiệu.</p>
          )}
        </header>

        {error && <Alert tone="error">{error}</Alert>}

        <nav className="story-tabs" aria-label="Nội dung truyện">
          {TABS.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className={`story-tab ${tab === entry.id ? "active" : ""}`}
              aria-selected={tab === entry.id}
              onClick={() => setTab(entry.id)}
            >
              {entry.label}
              {entry.id === "chapters" && <span className="story-tab-count">{chapters.length}</span>}
              {entry.id === "comments" && rating.count > 0 && (
                <span className="story-tab-count">
                  <AnimatedNumber value={rating.count} />
                </span>
              )}
            </button>
          ))}
        </nav>

        {/* The comment tab scrolls its own thread and keeps the score column
            still, so the pane around it must not scroll as well. */}
        <div
          className={`story-tab-panel ${
            tab === "comments" ? "story-tab-panel-static" : "scroll-area"
          }`}
        >
          {tab === "chapters" &&
            (chapters.length === 0 ? (
              <EmptyState title="Truyện chưa có chương nào">
                Quản trị viên sẽ cập nhật nội dung sớm.
              </EmptyState>
            ) : (
              <ul className="chapter-list chapter-list-grid">
                {chapters.map((chapter) => (
                  <ChapterRow key={chapter.id} chapter={chapter} read={readIds.has(chapter.id)} />
                ))}
              </ul>
            ))}

          {tab === "comments" && (
            <CommentSection storyId={storyId} summary={rating} onPosted={refreshRating} />
          )}
        </div>
      </div>
    </div>
  );
}
