import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { favoriteApi, storyApi } from "../api/endpoints";
import ChapterRow from "../components/ChapterRow";
import Pagination from "../components/Pagination";
import CommentSection from "../components/CommentSection";
import StarRating from "../components/StarRating";
import useDebouncedValue from "../hooks/useDebouncedValue";
import { useAuth } from "../context/auth-context";
import {
  Alert,
  AnimatedNumber,
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  Spinner,
  TextInput,
} from "../components/ui";

/** Bao nhiêu chương một trang — khớp với mặc định của máy chủ. */
const CHAPTER_PAGE_SIZE = 100;

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

  /* ---------------------------------------------------------------- */
  /* Danh sách chương                                                  */
  /* ---------------------------------------------------------------- */

  /*
   * Một trang chương, không phải cả danh sách.
   *
   * Trang đầu đi kèm trong lời gọi `detail`, nên mở truyện vẫn là một request
   * như trước. `chapterPage` chỉ khác null khi người đọc lật trang, tìm chương,
   * hay đảo thứ tự — và khi ấy nó thay chỗ cho trang đi kèm.
   */
  const [chapterPage, setChapterPage] = useState(null);
  const [chapterQuery, setChapterQuery] = useState("");
  const [chapterAsc, setChapterAsc] = useState(true);
  const [chapterBusy, setChapterBusy] = useState(false);

  // Gõ tới đâu gọi tới đó sẽ là một request cho mỗi phím; nửa giây là quãng đủ
  // để người ta gõ xong "chương 1" mà không thấy giao diện đứng lại.
  const debouncedQuery = useDebouncedValue(chapterQuery, 400);

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

  /** Lấy một trang chương; dùng cho cả lật trang, tìm kiếm và đảo thứ tự. */
  const loadChapters = useCallback(
    (page, query, asc) => {
      setChapterBusy(true);
      storyApi
        .chapters(storyId, {
          page,
          size: CHAPTER_PAGE_SIZE,
          q: query || undefined,
          order: asc ? "asc" : "desc",
        })
        .then(setChapterPage)
        .catch((err) => setError(err.message))
        .finally(() => setChapterBusy(false));
    },
    [storyId],
  );

  /*
   * Chỉ gọi khi có lý do để gọi.
   *
   * Trạng thái mặc định — trang đầu, không tìm gì, thứ tự xuôi — đã có sẵn
   * trong lời gọi `detail`, nên hỏi lại nó ngay lúc mở trang là một request
   * thừa cho mọi lượt truy cập. Quay về đúng trạng thái ấy thì bỏ trang rời và
   * dùng lại trang đi kèm.
   */
  useEffect(() => {
    if (!debouncedQuery && chapterAsc) {
      setChapterPage(null);
      return;
    }
    loadChapters(0, debouncedQuery, chapterAsc);
  }, [debouncedQuery, chapterAsc, loadChapters]);

  const readIds = useMemo(() => new Set(data?.readChapterIds ?? []), [data]);

  if (loading) return <Spinner />;
  if (error && !data) return <Alert tone="error">{error}</Alert>;
  if (!data) return null;

  const { story, rating, favorite, resumeChapterId } = data;

  // `chapters` is one page now, not the whole list. The two buttons below only
  // ever want a chapter from the first page — "read from the start" is the
  // first unlocked one, and "continue" is a chapter the reader already opened,
  // which the server hands back as an id rather than a row.
  const firstPage = data.chapters.content;
  const firstReadable = firstPage.find((chapter) => !chapter.locked) ?? firstPage[0];
  const resumeChapter = resumeChapterId
    ? firstPage.find((chapter) => chapter.id === resumeChapterId)
    : null;

  // Trang đang hiện: trang rời nếu người đọc đã lật/tìm, còn không thì trang đi
  // kèm lời gọi `detail`.
  const page = chapterPage ?? data.chapters;
  const chapters = page.content;
  const totalChapters = data.chapters.totalElements;

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
          {resumeChapterId ? (
            <ButtonLink to={`/chuong/${resumeChapterId}`} variant="primary" size="lg" block>
              {/* Số chương chỉ nói được khi chương ấy nằm trong trang đang tải.
                  Với một truyện nghìn chương thì chỗ đang đọc dở thường ở trang
                  khác, và "Đọc tiếp" vẫn là một cái nút đúng. */}
              {resumeChapter ? `Đọc tiếp chương ${resumeChapter.chapterNumber}` : "Đọc tiếp"}
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
            <b>{totalChapters}</b>
            <span>Chương</span>
          </span>
          <span className="story-chip">
            {/* Đếm trên cả truyện, không trên trang đang mở: "12 trong 100
                chương đầu" không phải câu người đọc muốn nghe. */}
            <b>
              {data.audioChapterCount}/{totalChapters}
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
              {entry.id === "chapters" && (
                <span className="story-tab-count">{totalChapters}</span>
              )}
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
          {tab === "chapters" && (
            <>
              {/* Thanh công cụ chỉ xuất hiện khi truyện đủ dài để cần tới nó.
                  Một truyện bốn chương mà có ô tìm kiếm và nút đảo thứ tự thì
                  hai thứ ấy chỉ là hai thứ để đọc lướt qua. */}
              {totalChapters > CHAPTER_PAGE_SIZE && (
                <div className="chapter-toolbar">
                  <TextInput
                    type="search"
                    value={chapterQuery}
                    placeholder="Tìm theo tên chương, hoặc gõ số chương…"
                    aria-label="Tìm chương"
                    onChange={(event) => setChapterQuery(event.target.value)}
                  />
                  <Button
                    size="sm"
                    aria-pressed={!chapterAsc}
                    title={chapterAsc ? "Đang xếp từ chương 1" : "Đang xếp từ chương mới nhất"}
                    onClick={() => setChapterAsc((current) => !current)}
                  >
                    {chapterAsc ? "Cũ nhất trước" : "Mới nhất trước"}
                  </Button>
                </div>
              )}

              {chapters.length === 0 ? (
                <EmptyState
                  title={
                    debouncedQuery ? "Không có chương nào khớp" : "Truyện chưa có chương nào"
                  }
                >
                  {debouncedQuery
                    ? "Thử tên khác, hoặc gõ đúng số chương."
                    : "Quản trị viên sẽ cập nhật nội dung sớm."}
                </EmptyState>
              ) : (
                <ul
                  className="chapter-list chapter-list-grid"
                  aria-busy={chapterBusy || undefined}
                >
                  {chapters.map((chapter) => (
                    <ChapterRow
                      key={chapter.id}
                      chapter={chapter}
                      read={readIds.has(chapter.id)}
                    />
                  ))}
                </ul>
              )}

              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                onChange={(next) => loadChapters(next, debouncedQuery, chapterAsc)}
              />
            </>
          )}

          {tab === "comments" && (
            <CommentSection storyId={storyId} summary={rating} onPosted={refreshRating} />
          )}
        </div>
      </div>
    </div>
  );
}
