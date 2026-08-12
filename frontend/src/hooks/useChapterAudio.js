import { useCallback, useEffect, useRef, useState } from "react";
import { audioApi } from "../api/endpoints";
import { pollUntilSettled } from "../utils/poll";

/**
 * A chapter's audio, and the means to have some made.
 *
 * Narration for a chapter nobody has recorded is produced on demand, but the
 * server keeps every finished track: asking again for a chapter that already
 * has one is answered from storage, costs nothing and comes back instantly.
 * Only a genuinely new one counts against the reader's daily allowance, which
 * is why this hook can offer the action without offering a way to run up a bill.
 *
 * `generating` covers the wait — the server queues the work and finishes it on
 * a background thread, so the only way to learn it landed is to keep asking.
 */
export default function useChapterAudio(chapterId, { enabled = true } = {}) {
  const [tracks, setTracks] = useState([]);
  const [activeTrack, setActiveTrack] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);

  // Set when a track arrives because the reader asked for it, so the player may
  // start on its own — they pressed a button that means "play this".
  const [playWhenReady, setPlayWhenReady] = useState(false);

  // Guards the poll loop: leaving the chapter must stop it.
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, [chapterId]);

  useEffect(() => {
    let cancelled = false;

    setTracks([]);
    setActiveTrack(null);
    setGenerating(false);
    setError(null);
    setPlayWhenReady(false);

    if (!enabled || !chapterId) {
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    audioApi
      .list(chapterId)
      .then((list) => {
        if (cancelled) return;
        const ready = list.filter((track) => track.status === "READY");
        setTracks(ready);
        // Prefer a human recording over a generated one when both exist.
        setActiveTrack(ready.find((track) => track.source === "UPLOAD") ?? ready[0] ?? null);
      })
      .catch(() => {
        // A locked chapter answers 403 here; the reader page already shows the
        // gate, so there is nothing extra to report.
        if (!cancelled) setTracks([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [chapterId, enabled]);

  const adopt = useCallback((track) => {
    setTracks((current) =>
      current.some((existing) => existing.id === track.id) ? current : [...current, track],
    );
    setActiveTrack(track);
    setPlayWhenReady(true);
  }, []);

  /**
   * Asks for narration and waits for it.
   *
   * @returns true when a playable track ended up loaded
   */
  const requestTts = useCallback(async () => {
    setError(null);
    setGenerating(true);

    try {
      const queued = await audioApi.requestTts(chapterId);

      // Already on disk: nothing was produced, nothing was charged.
      if (queued.status === "READY") {
        adopt(queued);
        return true;
      }

      let finished = null;
      const outcome = await pollUntilSettled(
        async () => {
          const track = await audioApi.ttsStatus(chapterId, queued.id);
          if (track.status === "PROCESSING") return false;
          finished = track;
          return true;
        },
        { isCancelled: () => cancelledRef.current },
      );

      if (cancelledRef.current || outcome === "cancelled") return false;

      if (outcome === "timeout") {
        setError("Việc tạo audio lâu hơn dự kiến. Bản audio vẫn đang được dựng — "
          + "mời bạn quay lại chương này sau ít phút.");
        return false;
      }

      if (finished?.status !== "READY") {
        setError(finished?.errorMessage ?? "Không tạo được audio cho chương này.");
        return false;
      }

      adopt(finished);
      return true;
    } catch (err) {
      if (!cancelledRef.current) setError(err.message);
      return false;
    } finally {
      if (!cancelledRef.current) setGenerating(false);
    }
  }, [adopt, chapterId]);

  const clearPlayWhenReady = useCallback(() => setPlayWhenReady(false), []);

  return {
    tracks,
    activeTrack,
    setActiveTrack,
    hasAudio: tracks.length > 0,
    loading,
    generating,
    error,
    requestTts,
    playWhenReady,
    clearPlayWhenReady,
  };
}
