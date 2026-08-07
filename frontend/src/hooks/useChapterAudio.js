import { useCallback, useEffect, useRef, useState } from "react";
import { audioApi } from "../api/endpoints";

const POLL_INTERVAL_MS = 2500;
const POLL_TIMEOUT_MS = 4 * 60 * 1000;

/**
 * Loads a chapter's audio and drives speech synthesis on demand.
 *
 * Synthesis is asynchronous server-side, so `requestTts` returns as soon as the
 * job is queued and this hook polls until the track is READY or FAILED. The
 * polling timer is cancelled whenever the chapter changes or the component
 * unmounts, so switching chapters mid-generation never leaks a timer.
 */
export default function useChapterAudio(chapterId, { enabled = true } = {}) {
  const [tracks, setTracks] = useState([]);
  const [activeTrack, setActiveTrack] = useState(null);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(enabled);

  const pollTimer = useRef(null);
  const cancelled = useRef(false);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  useEffect(() => {
    cancelled.current = false;
    setTracks([]);
    setActiveTrack(null);
    setError(null);
    setGenerating(false);

    if (!enabled || !chapterId) {
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    audioApi
      .list(chapterId)
      .then((list) => {
        if (cancelled.current) return;
        const ready = list.filter((track) => track.status === "READY");
        setTracks(ready);
        // Prefer a human recording over a generated one when both exist.
        setActiveTrack(ready.find((track) => track.source === "UPLOAD") ?? ready[0] ?? null);
      })
      .catch(() => {
        // A locked chapter answers 403 here; the reader page already shows the
        // gate, so there is nothing extra to report.
        if (!cancelled.current) setTracks([]);
      })
      .finally(() => {
        if (!cancelled.current) setLoading(false);
      });

    return () => {
      cancelled.current = true;
      stopPolling();
    };
  }, [chapterId, enabled, stopPolling]);

  /** Polls one synthesis job until it settles. */
  const pollUntilReady = useCallback(
    (audioId) => {
      const deadline = Date.now() + POLL_TIMEOUT_MS;

      const tick = () => {
        audioApi
          .ttsStatus(chapterId, audioId)
          .then((status) => {
            if (cancelled.current) return;

            if (status.status === "READY") {
              setTracks((current) => [...current.filter((t) => t.id !== status.id), status]);
              setActiveTrack(status);
              setGenerating(false);
              return;
            }

            if (status.status === "FAILED") {
              setError(status.errorMessage ?? "Không tạo được audio. Vui lòng thử lại.");
              setGenerating(false);
              return;
            }

            if (Date.now() > deadline) {
              setError("Quá thời gian chờ tạo audio. Vui lòng thử lại.");
              setGenerating(false);
              return;
            }
            pollTimer.current = setTimeout(tick, POLL_INTERVAL_MS);
          })
          .catch((err) => {
            if (cancelled.current) return;
            setError(err.message);
            setGenerating(false);
          });
      };

      pollTimer.current = setTimeout(tick, POLL_INTERVAL_MS);
    },
    [chapterId],
  );

  /**
   * Requests narration for the chapter.
   *
   * @returns the track when it was already cached, otherwise null while it generates
   */
  const requestTts = useCallback(
    async ({ voice, speed }) => {
      setError(null);
      setGenerating(true);
      stopPolling();

      try {
        const result = await audioApi.requestTts(chapterId, { voice, speed });

        if (result.status === "READY") {
          setTracks((current) => [...current.filter((t) => t.id !== result.id), result]);
          setActiveTrack(result);
          setGenerating(false);
          return result;
        }

        if (result.status === "FAILED") {
          setError(result.errorMessage ?? "Không tạo được audio.");
          setGenerating(false);
          return null;
        }

        pollUntilReady(result.id);
        return null;
      } catch (err) {
        setError(err.message);
        setGenerating(false);
        return null;
      }
    },
    [chapterId, pollUntilReady, stopPolling],
  );

  return {
    tracks,
    activeTrack,
    setActiveTrack,
    hasAudio: tracks.length > 0,
    loading,
    generating,
    error,
    clearError: () => setError(null),
    requestTts,
  };
}
