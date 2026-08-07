import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { audioApi, chapterApi } from "../api/endpoints";
import AudioPlayer from "../components/AudioPlayer";
import LockedGate from "../components/LockedGate";
import ReaderSettings from "../components/ReaderSettings";
import useChapterAudio from "../hooks/useChapterAudio";
import { AccessBadge, Alert, Button, Spinner } from "../components/ui";

const AUTO_CONTINUE_KEY = "storytts.autoContinue";

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
   * Continuous listening: move to the next chapter when playback finishes.
   *
   * Only the navigation happens here. The next page loads its own audio and the
   * access check runs again server-side either way.
   */
  const handleTrackEnded = useCallback(() => {
    if (autoContinue && chapter?.nextChapterId) {
      navigate(`/chuong/${chapter.nextChapterId}`);
    }
  }, [autoContinue, chapter, navigate]);

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
          disabled={!chapter.previousChapterId}
          onClick={() => navigate(`/chuong/${chapter.previousChapterId}`)}
        >
          Chương trước
        </Button>

        <div className="reader-bar-title">
          <strong>{chapter.title}</strong>
          <AccessBadge level={chapter.accessLevel} label={chapter.requirementLabel} />
        </div>

        <Button
          variant="primary"
          disabled={!chapter.nextChapterId}
          onClick={() => navigate(`/chuong/${chapter.nextChapterId}`)}
        >
          Chương sau
        </Button>
      </div>

      <div className="reader-grid">
        <section className="reader-pane">
          <header className="reader-pane-header">
            <h2>Nội dung chương</h2>
            <Link to={`/truyen/${chapter.storyId}`} className="muted" style={{ fontWeight: 700 }}>
              {chapter.storyTitle}
            </Link>
          </header>

          <div className="reader-pane-body scroll-area">
            <div className="reader-content">{chapter.content}</div>
          </div>
        </section>

        <aside className="reader-pane">
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

            <div className="reader-side-section">
              <ReaderSettings />
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
