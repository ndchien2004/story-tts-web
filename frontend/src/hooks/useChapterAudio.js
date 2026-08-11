import { useEffect, useState } from "react";
import { audioApi } from "../api/endpoints";

/**
 * Loads whatever audio a chapter already has.
 *
 * Read-only on purpose. Narration is prepared in the admin console, so there is
 * nothing here to start one: a reader either finds a track or is told the
 * chapter has none yet.
 */
export default function useChapterAudio(chapterId, { enabled = true } = {}) {
  const [tracks, setTracks] = useState([]);
  const [activeTrack, setActiveTrack] = useState(null);
  const [loading, setLoading] = useState(enabled);

  useEffect(() => {
    let cancelled = false;

    setTracks([]);
    setActiveTrack(null);

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

  return {
    tracks,
    activeTrack,
    setActiveTrack,
    hasAudio: tracks.length > 0,
    loading,
  };
}
