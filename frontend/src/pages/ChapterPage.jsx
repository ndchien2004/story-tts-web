import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { audioApi, chapterApi, progressApi } from "../api/endpoints";
import AudioPlayer from "../components/AudioPlayer";
import LockedGate from "../components/LockedGate";
import ReaderSettings from "../components/ReaderSettings";
import { useAuth } from "../context/auth-context";
import useChapterAudio from "../hooks/useChapterAudio";
import { Alert, Button, ChevronIcon, Spinner } from "../components/ui";

const AUTO_CONTINUE_KEY = "storytts.autoContinue";

/** How close to the bottom of the text still counts as having reached the end. */
const END_THRESHOLD_PX = 40;

/**
 * Reading screen.
 *
 * The chapter text and the listening panel sit side by side and each scrolls on
 * its own, so the navigation stays pinned to the top corners no matter how long
 * the chapter is.
 */
export default function ChapterPage() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // One "đã đọc" call per chapter: reaching the end, clicking on and letting the
  // narration run out can all fire, and only the first needs to reach the server.
  const markedRead = useRef(false);
  const contentRef = useRef(null);

  const [chapter, setChapter] = useState(null);
  const [lockError, setLockError] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [voices, setVoices] = useState([]);
  const [autoContinue, setAutoContinue] = useState(
    () => localStorage.getItem(AUTO_CONTINUE_KEY) === "true",
  );

  // Audio is only fetched once the chapter itself proved readable, so a locked
  // chapter never triggers a second request that is bound to be refused.
  const audio = useChapterAudio(chapterId, { enabled: Boolean(chapter) });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setChapter(null);
    setLockError(null);
    setError(null);

    chapterApi
      .detail(chapterId)
      .then((data) => {
        if (!cancelled) setChapter(data);
      })
      .catch((err) => {
        if (cancelled) return;
        // A locked chapter is an expected outcome, not a failure to report.
        if (err.isLocked) {
          setLockError(err);
        } else {
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [chapterId]);

  useEffect(() => {
    if (!chapter) return;
    audioApi
      .voices(chapterId)
      .then(setVoices)
      .catch(() => setVoices([]));
  }, [chapter, chapterId]);

  useEffect(() => {
    localStorage.setItem(AUTO_CONTINUE_KEY, String(autoContinue));
  }, [autoContinue]);

  /**
   * Open the chapter's progress record, so "Đọc tiếp" on the story page knows
   * where the reader stopped even if they never reach the end.
   */
  useEffect(() => {
    markedRead.current = false;
    if (!chapter || !isAuthenticated) return;

    // Progress is a convenience, never something worth interrupting reading for.
    progressApi.save(chapterId, { lastPosition: 0 }).catch(() => {});
  }, [chapter, chapterId, isAuthenticated]);

  /** Records the chapter as finished; safe to call more than once. */
  const markRead = useCallback(() => {
    if (markedRead.current || !isAuthenticated) return;
    markedRead.current = true;
    progressApi.markRead(chapterId).catch(() => {});
  }, [chapterId, isAuthenticated]);

  /**
   * Reaching the bottom of the text counts as having read the chapter — that
   * also covers the last chapter of a story, where there is no "next" to click.
   */
  const handleScroll = useCallback(
    (event) => {
      const { scrollTop, clientHeight, scrollHeight } = event.currentTarget;
      if (scrollTop + clientHeight >= scrollHeight - END_THRESHOLD_PX) markRead();
    },
    [markRead],
  );

  /**
   * A chapter short enough to fit the pane never fires a scroll event, so it
   * would never reach the check above. Nothing is left to read in that case,
   * so opening it is finishing it.
   */
  useEffect(() => {
    const pane = contentRef.current;
    if (!chapter || !pane) return;
    if (pane.scrollHeight <= pane.clientHeight + END_THRESHOLD_PX) markRead();
  }, [chapter, markRead]);

  function goToChapter(id) {
    markRead();
    navigate(`/chuong/${id}`);
  }

  /**
   * Continuous listening: move to the next chapter when playback finishes.
   *
   * Only the navigation happens here. The next page loads its own audio and the
   * access check runs again server-side either way.
   */
  const handleTrackEnded = useCallback(() => {
    markRead();
    if (autoContinue && chapter?.nextChapterId) {
      navigate(`/chuong/${chapter.nextChapterId}`);
    }
  }, [autoContinue, chapter, markRead, navigate]);

  if (loading) {
    return (
      <div className="page">
        <Spinner label="Đang tải chương…" />
      </div>
    );
  }

  if (lockError) {
    return (
      <div className="container-narrow page">
        <LockedGate
          requiredAccessLevel={lockError.requiredAccessLevel}
          message={lockError.message}
        />
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <Alert tone="error">{error}</Alert>
      </div>
    );
  }

  if (!chapter) return null;

  return (
    <div className="reader">
      <div className="reader-bar">
        <Button
          className="reader-nav-btn"
          disabled={!chapter.previousChapterId}
          onClick={() => goToChapter(chapter.previousChapterId)}
        >
          <ChevronIcon />
          Chương trước
        </Button>

        {/* The story it belongs to sits above the chapter's own name, so the
            bar answers "where am I" without a second header below it. */}
        <div className="reader-bar-title">
          <Link to={`/truyen/${chapter.storyId}`} className="reader-bar-story">
            {chapter.storyTitle}
          </Link>
          <strong>{chapter.title}</strong>
        </div>

        <Button
          className="reader-nav-btn"
          variant="primary"
          disabled={!chapter.nextChapterId}
          onClick={() => goToChapter(chapter.nextChapterId)}
        >
          Chương sau
          <ChevronIcon right />
        </Button>
      </div>

      <div className="reader-grid">
        <section className="reader-pane">
          {/* The size control sits in the corner of the text it changes, so
              the result of a click is in view when you make it. */}
          <header className="reader-pane-header">
            <h2>Nội dung</h2>
            <ReaderSettings />
          </header>

          <div className="reader-pane-body scroll-area" ref={contentRef} onScroll={handleScroll}>
            <div className="reader-content">{chapter.content}</div>
          </div>
        </section>

        <aside className="reader-pane reader-aside">
          <header className="reader-pane-header">
            <h2>Nghe chương này</h2>
          </header>

          <div className="reader-pane-body scroll-area">
            <AudioPlayer
              audio={audio}
              voices={voices}
              autoContinue={autoContinue}
              onToggleAutoContinue={() => setAutoContinue((value) => !value)}
              onTrackEnded={handleTrackEnded}
              hasNextChapter={Boolean(chapter.nextChapterId)}
            />
          </div>
        </aside>
      </div>
    </div>
  );
}
