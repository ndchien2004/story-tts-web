import { useCallback, useEffect, useRef, useState } from "react";
import { audioApi } from "../api/endpoints";
import { pollUntilSettled } from "../utils/poll";

/**
 * Thứ tự tìm bản để phát.
 *
 * Khu quản trị đặt gì ở chương này thì cái đó thắng, và bản người thu thắng bản
 * máy đọc. Chỉ khi cả hai đều không có, người đọc mới nghe bản mình tự dựng —
 * và cũng chỉ khi ấy trang mới mời họ bấm nút.
 *
 * Máy chủ đã lọc theo người gọi rồi, nên bản mang nhãn SESSION lọt tới đây chắc
 * chắn là của chính người đang đọc.
 */
function preferredTrack(ready) {
  const fromLibrary = ready.filter((track) => track.owner !== "SESSION");
  return (
    fromLibrary.find((track) => track.source === "UPLOAD") ??
    fromLibrary[0] ??
    ready[0] ??
    null
  );
}

/**
 * A chapter's audio, and the means to have some made.
 *
 * A chapter the console has not recorded can still be listened to: the reader
 * asks, and the server narrates. What comes back is theirs — nobody else is
 * served it, and it is thrown away when their session ends. So the allowance is
 * spent per person rather than once for everyone, and this hook offers the
 * action only where the console has left a gap.
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
        setActiveTrack(preferredTrack(ready));
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
