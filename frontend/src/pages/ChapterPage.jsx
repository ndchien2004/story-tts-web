import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { audioApi, chapterApi, progressApi } from "../api/endpoints";
import useAudioMixer from "../audio/useAudioMixer";
import useBgm from "../audio/useBgm";
import AudioPlayer from "../components/AudioPlayer";
import LockedGate from "../components/LockedGate";
import ReaderSettings from "../components/ReaderSettings";
import { useAuth } from "../context/auth-context";
import useChapterAudio from "../hooks/useChapterAudio";
import useTtsStatus from "../hooks/useTtsStatus";
import { Alert, Button, ChevronIcon, Spinner } from "../components/ui";

const AUTO_CONTINUE_KEY = "storytts.autoContinue";

/** How close to the bottom of the text still counts as having reached the end. */
const END_THRESHOLD_PX = 40;

/** How often the listening position is written back while audio plays. */
const POSITION_SAVE_INTERVAL_MS = 15_000;

/**
 * Reading screen.
 *
 * The chapter text and the listening panel sit side by side and each scrolls on
 * its own, so the navigation stays pinned to the top corners no matter how long
 * the chapter is.
 *
 * <h3>Ai giữ tiếng</h3>
 * Không phải khối này, và cũng không phải trình phát. Tiếng do bộ trộn giữ —
 * một `AudioContext` duy nhất dựng ở đây và sống suốt buổi đọc, kể cả khi người
 * đọc sang chương khác. Nhờ vậy nhạc nền không đứt quãng ở ranh giới hai chương:
 * đổi chương là đổi nguồn tiếng, không phải dựng lại đường tiếng.
 */
export default function ChapterPage() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // One "đã đọc" call per chapter: reaching the end, clicking on and letting the
  // narration run out can all fire, and only the first needs to reach the server.
  const markedRead = useRef(false);
  const contentRef = useRef(null);
  const lastPositionSaveRef = useRef(0);

  const [chapter, setChapter] = useState(null);
  const [lockError, setLockError] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [autoContinue, setAutoContinue] = useState(
    () => localStorage.getItem(AUTO_CONTINUE_KEY) === "true",
  );

  // Set only when the previous chapter finished playing; the mixer reads it to
  // decide whether it may start on its own, then it is cleared.
  const [autoPlayNext, setAutoPlayNext] = useState(false);

  // Audio is only fetched once the chapter itself proved readable, so a locked
  // chapter never triggers a second request that is bound to be refused.
  const audio = useChapterAudio(chapterId, { enabled: Boolean(chapter) });

  // Refreshed after each generation, since producing one spends a daily go.
  const { status: ttsStatus, refresh: refreshTtsStatus } = useTtsStatus();

  // One automatic narration per chapter. Without this the effect below would
  // fire again on every state change and spend the reader's whole allowance on
  // a single chapter.
  const autoNarrationTried = useRef(false);

  // Set on the way out of a finishing chapter and read on the way into the next
  // one. `autoPlayNext` cannot answer "did I arrive by autoplay?" on its own,
  // because pressing the narration button sets it too — and reading it that way
  // would turn a failed press into an automatic retry that costs another go.
  const arrivingByAutoContinue = useRef(false);
  const arrivedByAutoContinue = useRef(false);

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

  /**
   * Writes the listening position back so the track resumes next time (4.4).
   *
   * The database keeps one position per chapter, not per track, which is enough:
   * a chapter has one narration worth following, and a reader who switches
   * between a recording and a generated version wants the same place in the text
   * either way.
   */
  const savePosition = useCallback(
    (seconds) => {
      if (!isAuthenticated) return;
      progressApi
        .save(chapterId, { audioPositionSeconds: Math.floor(seconds) })
        .catch(() => {});
    },
    [chapterId, isAuthenticated],
  );

  /**
   * Throttles the write. The mixer reports the position several times a second,
   * and it only needs to be roughly right — losing the last few seconds of a
   * session matters far less than a request per frame.
   */
  const handlePositionChange = useCallback(
    (seconds) => {
      const now = Date.now();
      if (now - lastPositionSaveRef.current < POSITION_SAVE_INTERVAL_MS) return;
      lastPositionSaveRef.current = now;
      savePosition(seconds);
    },
    [savePosition],
  );

  /** Records the chapter as finished; safe to call more than once. */
  const markRead = useCallback(() => {
    if (markedRead.current || !isAuthenticated) return;
    markedRead.current = true;
    progressApi.markRead(chapterId).catch(() => {});
  }, [chapterId, isAuthenticated]);

  /**
   * Continuous listening: move to the next chapter when playback finishes.
   *
   * Only the navigation happens here. The next page loads its own audio and the
   * access check runs again server-side either way.
   */
  const handleTrackEnded = useCallback(() => {
    markRead();
    savePosition(0);
    if (autoContinue && chapter?.nextChapterId) {
      // The one case where audio may start by itself: the listener is already
      // listening, and asked for the next chapter to follow on.
      arrivingByAutoContinue.current = true;
      setAutoPlayNext(true);
      navigate(`/chuong/${chapter.nextChapterId}`);
    }
  }, [autoContinue, chapter, markRead, navigate, savePosition]);

  /*
   * Một bộ trộn cho cả buổi đọc.
   *
   * Dựng ở đây chứ không trong trình phát, vì nó phải sống lâu hơn bất kỳ khối
   * giao diện nào: đổi chương là đổi nguồn tiếng, không phải dựng lại đường
   * tiếng — nhạc nền đang chạy thì cứ chạy tiếp qua ranh giới hai chương.
   */
  const { engine, state: mixerState } = useAudioMixer({
    onEnded: handleTrackEnded,
    onTimeUpdate: handlePositionChange,
    onPause: savePosition,
  });

  const bgm = useBgm(engine);

  const activeTrack = audio.activeTrack;
  const streamUrl = useMemo(
    () => (activeTrack?.streamUrl ? audioApi.streamUrl(activeTrack.streamUrl) : null),
    [activeTrack],
  );

  /*
   * Nạp bản audio đang chọn vào bộ trộn.
   *
   * `autoPlayNext` nằm trong danh sách phụ thuộc chứ không chỉ được đọc một lần:
   * lời mời tự phát có thể tới sau lúc bản audio đã nạp xong — người đọc bấm
   * "Nghe bằng AI" thì bản mới được nhận vào và cờ tự phát được bật gần như cùng
   * lúc, và thứ tự giữa hai việc ấy không có gì bảo đảm.
   */
  // Nhớ cả bộ trộn chứ không chỉ đường dẫn: một bộ trộn khác là một đồ thị âm
  // thanh khác, và bản audio phải được nạp lại vào nó dù đường dẫn không đổi.
  const loaded = useRef({ engine: null, src: null });

  useEffect(() => {
    const alreadyLoaded = loaded.current.engine === engine && loaded.current.src === streamUrl;

    if (alreadyLoaded) {
      if (streamUrl && autoPlayNext) {
        setAutoPlayNext(false);
        void engine.play();
      }
      return;
    }

    loaded.current = { engine, src: streamUrl };
    void engine.loadNarration(streamUrl, {
      startAt: chapter?.audioPositionSeconds ?? 0,
      autoPlay: autoPlayNext,
    });
    if (autoPlayNext) setAutoPlayNext(false);
  }, [autoPlayNext, chapter, engine, streamUrl]);

  /**
   * Giữ lại chỗ nghe dở khi rời trang.
   *
   * Đóng thẳng tab hay bấm quay lại đều không sinh ra sự kiện tạm dừng nào, nên
   * không có chỗ này thì đoạn từ lần ghi định kỳ gần nhất tới lúc rời đi bị mất
   * — mà đó đúng là đoạn người vừa rời đi quan tâm.
   */
  useEffect(() => () => {
    const seconds = engine.exactPosition();
    if (seconds > 0) savePosition(seconds);
  }, [engine, savePosition]);

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
   * Continuous listening across a chapter nobody has narrated yet.
   *
   * Arriving here by autoplay means someone is listening with their hands full,
   * so a chapter without a track would end the session in silence. Producing one
   * is the only way to carry on — and it is bounded: the attempt is made once per
   * chapter, only for a listener who arrived automatically, and only while goes
   * remain. Opening a chapter by hand never triggers it, or browsing chapters
   * would quietly drain the day's allowance.
   */
  useEffect(() => {
    autoNarrationTried.current = false;
    arrivedByAutoContinue.current = arrivingByAutoContinue.current;
    arrivingByAutoContinue.current = false;
  }, [chapterId]);

  useEffect(() => {
    if (!arrivedByAutoContinue.current || !autoContinue) return;
    if (audio.loading || audio.hasAudio || audio.generating) return;
    if (autoNarrationTried.current) return;
    if (!isAuthenticated || !ttsStatus?.enabled) return;
    // Out of goes, or the chapter is too long to narrate: the chain stops here
    // rather than asking for something the server will refuse.
    if (ttsStatus.remainingToday === 0) return;
    if (ttsStatus.maxChars > 0 && (chapter?.content?.length ?? 0) > ttsStatus.maxChars) return;

    autoNarrationTried.current = true;
    audio.requestTts().then(() => refreshTtsStatus());
  }, [audio, autoContinue, chapter, isAuthenticated, refreshTtsStatus, ttsStatus]);

  /** A track the reader asked for may start on its own; that press was the consent. */
  useEffect(() => {
    if (audio.playWhenReady) {
      setAutoPlayNext(true);
      audio.clearPlayWhenReady();
      refreshTtsStatus();
    }
  }, [audio, refreshTtsStatus]);

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
              ttsStatus={ttsStatus}
              isAuthenticated={isAuthenticated}
              chapterLength={chapter.content?.length ?? 0}
              autoContinue={autoContinue}
              onToggleAutoContinue={() => setAutoContinue((value) => !value)}
              hasNextChapter={Boolean(chapter.nextChapterId)}
              engine={engine}
              mixerState={mixerState}
              bgm={bgm}
            />
          </div>
        </aside>
      </div>
    </div>
  );
}
