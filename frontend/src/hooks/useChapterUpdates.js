import { useCallback, useEffect, useState } from "react";
import { chapterApi } from "../api/endpoints";

/**
 * Watches a chapter for the moment an admin changes its text.
 *
 * A reader can sit on one chapter for twenty minutes without touching anything
 * — that is what listening looks like. Polling would mean a request every few
 * seconds for an event that fires once a month, so the server pushes instead,
 * over `EventSource`. Reconnection after a dropped link is the browser's job,
 * not ours.
 *
 * <h3>Comparing versions rather than counting events</h3>
 * The hook reports `staleVersion` — the version the server says exists — only
 * when it is *newer* than the one the page is showing. Two things fall out of
 * that, and neither needs a rule of its own:
 *
 * - An event for a version the reader already loaded is ignored, so pressing
 *   the button and then receiving the echo of that same edit does not put the
 *   banner straight back up.
 * - A stream that reconnects and replays does no harm, because the comparison
 *   is against state rather than against a count of what has arrived.
 *
 * The stream is a convenience, never the source of truth: it carries no chapter
 * text, and the new content is fetched through the normal, access-checked API
 * when the reader asks for it. A blocked or failed stream costs the reader only
 * the immediacy — the page still notices the change on its next call.
 *
 * @param chapterId      chapter being read, or null to watch nothing
 * @param currentVersion `contentVersion` of the text currently on screen
 * @returns {{staleVersion: number|null, dismiss: () => void}} `staleVersion` is
 *          null while the page is up to date
 */
export default function useChapterUpdates(chapterId, currentVersion) {
  // Holds what the server last announced, not "is there an update" — the
  // comparison against currentVersion happens at read time, so a reader who
  // reloads by some other route stops seeing the banner without us having to
  // notice that they did.
  const [announcedVersion, setAnnouncedVersion] = useState(null);

  useEffect(() => {
    setAnnouncedVersion(null);
  }, [chapterId]);

  useEffect(() => {
    if (!chapterId) return undefined;

    // EventSource is absent in older browsers and in some embedded webviews.
    // Reading is unaffected there; only the instant notice is lost.
    if (typeof window === "undefined" || typeof window.EventSource === "undefined") {
      return undefined;
    }

    let source;
    try {
      source = new EventSource(chapterApi.eventsUrl(chapterId));
    } catch {
      return undefined;
    }

    const onUpdate = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (Number(payload.chapterId) !== Number(chapterId)) return;
        // Keep the highest we have heard: reconnects can replay, and going
        // backwards would hide a banner that is still owed.
        setAnnouncedVersion((seen) =>
          seen === null ? payload.contentVersion : Math.max(seen, payload.contentVersion),
        );
      } catch {
        // A frame we cannot read is not worth breaking the page over.
      }
    };

    source.addEventListener("chapter-updated", onUpdate);

    // Deliberately no error handler beyond closing nothing: EventSource
    // reconnects on its own, and surfacing a transient network blip as an error
    // in the reading UI would be noise about a feature the reader never asked
    // for.
    return () => {
      source.removeEventListener("chapter-updated", onUpdate);
      source.close();
    };
  }, [chapterId]);

  const dismiss = useCallback(() => setAnnouncedVersion(null), []);

  const staleVersion =
    announcedVersion !== null && currentVersion != null && announcedVersion > currentVersion
      ? announcedVersion
      : null;

  return { staleVersion, dismiss };
}
