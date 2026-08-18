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
 *
 * <h3>Everything here is scoped to one version of the text</h3>
 * `contentVersion` is part of the identity of what this hook holds, not an
 * extra detail about it: a track narrating the previous wording is not a track
 * of this chapter as far as the reading page is concerned. So it sits in the
 * dependency list beside `chapterId`, and moving to a new version clears the
 * tracks exactly the way moving to a new chapter does.
 *
 * That is also what makes a late response harmless. Reloading after an edit
 * leaves the previous request in flight; when it lands, its effect has already
 * been torn down and its `cancelled` flag is set, so it cannot write over the
 * newer state. The arriving tracks are checked against the version as well —
 * the server has already filtered them, and this second look is what turns that
 * promise into something the client can actually observe.
 *
 * @param contentVersion version of the text currently on screen; tracks
 *                       belonging to any other version are not this chapter's
 */
export default function useChapterAudio(chapterId, { enabled = true, contentVersion } = {}) {
  const [tracks, setTracks] = useState([]);
  const [activeTrack, setActiveTrack] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);

  // Set when a track arrives because the reader asked for it, so the player may
  // start on its own — they pressed a button that means "play this".
  const [playWhenReady, setPlayWhenReady] = useState(false);

  // Guards the poll loop: leaving the chapter — or the chapter moving to a new
  // version under the reader — must stop it.
  const cancelledRef = useRef(false);

  /**
   * Whether a track narrates the text currently on screen.
   *
   * A track with no version at all is one made before versions existed; the
   * server does not serve those as current, and neither does this.
   */
  const belongsHere = useCallback(
    (track) =>
      contentVersion == null ||
      (track?.contentVersion != null && track.contentVersion === contentVersion),
    [contentVersion],
  );

  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, [chapterId, contentVersion]);

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
        const ready = list.filter((track) => track.status === "READY" && belongsHere(track));
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
    // contentVersion is in here on purpose: a new version is a new set of
    // tracks, so this refetches and the in-flight response from the previous
    // one lands with `cancelled` already set.
  }, [belongsHere, chapterId, contentVersion, enabled]);

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
        // STALE lands here too: the chapter moved on while this was being made,
        // so the track is finished but belongs to text nobody is reading any
        // more. Saying so plainly beats a generic failure, because nothing went
        // wrong and the next press will succeed.
        if (finished?.status === "STALE") {
          setError("Chương vừa được cập nhật trong lúc tạo audio, nên bản vừa tạo đọc theo "
            + "nội dung cũ. Mời bạn tải nội dung mới rồi tạo lại.");
          return false;
        }
        setError(finished?.errorMessage ?? "Không tạo được audio cho chương này.");
        return false;
      }

      // Ready, but for which text? A generation that started before an edit can
      // still come back READY for the older version — adopting it would put the
      // reader back in exactly the state all of this exists to prevent.
      if (!belongsHere(finished)) {
        setError("Chương vừa được cập nhật. Mời bạn tải nội dung mới rồi tạo lại audio.");
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
  }, [adopt, belongsHere, chapterId]);

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
