import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { audioApi, chapterApi } from "../api/endpoints";
import AudioPlayer from "../components/AudioPlayer";
import LockedGate from "../components/LockedGate";
import useChapterAudio from "../hooks/useChapterAudio";
import { useTheme } from "../context/theme-context";
import { AccessBadge, Alert, Button, ButtonLink, Spinner } from "../components/ui";

const AUTO_CONTINUE_KEY = "storytts.autoContinue";

export default function ChapterPage() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const { increaseFontSize, decreaseFontSize, canIncrease, canDecrease, isDark, toggleTheme } =
    useTheme();

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

    window.scrollTo({ top: 0 });

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
   * Only the navigation happens here. The next page loads its own audio and, if
   * none exists yet, the reader can generate it there — the access check runs
   * again server-side either way.
   */
  const handleTrackEnded = useCallback(() => {
    if (autoContinue && chapter?.nextChapterId) {
      navigate(`/chuong/${chapter.nextChapterId}`);
    }
  }, [autoContinue, chapter, navigate]);

  if (loading) return <Spinner label="Đang tải chương…" />;

  if (lockError) {
    return (
      <div className="container-narrow">
        <LockedGate
          requiredAccessLevel={lockError.requiredAccessLevel}
          message={lockError.message}
        />
      </div>
    );
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!chapter) return null;

  return (
    <div className="container-narrow stack" style={{ gap: "1.5rem" }}>
      <div className="reader-toolbar">
        <div className="row" style={{ gap: "0.4rem" }}>
          <Button
            size="sm"
            disabled={!chapter.previousChapterId}
            onClick={() => navigate(`/chuong/${chapter.previousChapterId}`)}
          >
            ← Chương trước
          </Button>
          <Button
            size="sm"
            disabled={!chapter.nextChapterId}
            onClick={() => navigate(`/chuong/${chapter.nextChapterId}`)}
          >
            Chương sau →
          </Button>
        </div>

        <div className="row" style={{ gap: "0.4rem" }}>
          <Button
            size="sm"
            onClick={decreaseFontSize}
            disabled={!canDecrease}
            aria-label="Giảm cỡ chữ"
            title="Giảm cỡ chữ"
          >
            A−
          </Button>
          <Button
            size="sm"
            onClick={increaseFontSize}
            disabled={!canIncrease}
            aria-label="Tăng cỡ chữ"
            title="Tăng cỡ chữ"
          >
            A+
          </Button>
          <Button
            size="sm"
            onClick={toggleTheme}
            aria-label={isDark ? "Giao diện sáng" : "Giao diện tối"}
            title={isDark ? "Giao diện sáng" : "Giao diện tối"}
          >
            {isDark ? "☀️" : "🌙"}
          </Button>
        </div>
      </div>

      <AudioPlayer
        audio={audio}
        voices={voices}
        autoContinue={autoContinue}
        onToggleAutoContinue={() => setAutoContinue((value) => !value)}
        onTrackEnded={handleTrackEnded}
        hasNextChapter={Boolean(chapter.nextChapterId)}
      />

      <article className="nb-card stack">
        <div className="row-between">
          <Link to={`/truyen/${chapter.storyId}`} className="muted" style={{ fontWeight: 700 }}>
            ← {chapter.storyTitle}
          </Link>
          <AccessBadge level={chapter.accessLevel} label={chapter.requirementLabel} />
        </div>

        <h1>{chapter.title}</h1>

        <hr style={{ border: "none", borderTop: "3px solid var(--outline)", margin: "0.5rem 0" }} />

        <div className="reader-content">{chapter.content}</div>
      </article>

      <div className="row" style={{ justifyContent: "space-between" }}>
        <Button
          disabled={!chapter.previousChapterId}
          onClick={() => navigate(`/chuong/${chapter.previousChapterId}`)}
        >
          ← Chương trước
        </Button>
        <ButtonLink to={`/truyen/${chapter.storyId}`} variant="ghost">
          Mục lục
        </ButtonLink>
        <Button
          variant="primary"
          disabled={!chapter.nextChapterId}
          onClick={() => navigate(`/chuong/${chapter.nextChapterId}`)}
        >
          Chương sau →
        </Button>
      </div>
    </div>
  );
}
