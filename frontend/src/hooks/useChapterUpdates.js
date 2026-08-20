import { useCallback, useEffect, useState } from "react";
import { chapterApi } from "../api/endpoints";

/**
 * Watches a chapter for the two things an admin can do to it underneath a
 * reader: rewrite it, or remove it.
 *
 * A reader can sit on one chapter for twenty minutes without touching anything
 * — that is what listening looks like. Polling would mean a request every few
 * seconds for an event that fires once a month, so the server pushes instead,
 * over `EventSource`. Reconnection after a dropped link is the browser's job,
 * not ours.
 *
 * <h3>One stream, two events, two very different answers</h3>
 * Both arrive on the same connection and both are announced only after the
 * server's transaction commits. What they ask of the reader is opposite, and
 * the hook keeps them apart rather than folding them into one "something
 * changed" flag:
 *
 * - `staleVersion` — the text was rewritten. There is a newer version to read
 *   *when the reader feels like it*. Nothing is taken away from them.
 * - `deletion` — the chapter (or its whole story) is gone. There is nothing
 *   left to read, so the page has to stop rather than offer.
 *
 * A single flag would force every caller to branch on a field to find out which
 * of those two it was told, and a caller that forgot would invite someone to
 * "load the new version" of a chapter that no longer exists.
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
 * @returns {{staleVersion: number|null, dismiss: () => void,
 *            deletion: {wholeStory: boolean, storyId: number|null,
 *                       refunded: boolean}|null}}
 *          `staleVersion` is null while the page is up to date; `deletion` is
 *          null until the content is removed, and never goes back to null —
 *          deletion is not something that gets undone
 */
export default function useChapterUpdates(chapterId, currentVersion) {
  // Holds what the server last announced, not "is there an update" — the
  // comparison against currentVersion happens at read time, so a reader who
  // reloads by some other route stops seeing the banner without us having to
  // notice that they did.
  const [announcedVersion, setAnnouncedVersion] = useState(null);

  // Deliberately has no dismiss. A stale version is information the reader may
  // act on later; a deleted chapter is a dead end, and letting them wave it
  // away would put them back on a reader with no content behind it.
  const [deletion, setDeletion] = useState(null);

  useEffect(() => {
    setAnnouncedVersion(null);
    setDeletion(null);
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

    const onDeleted = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (Number(payload.chapterId) !== Number(chapterId)) return;

        setDeletion({
          wholeStory: payload.type === "STORY_DELETED",
          // Only worth keeping when the story outlived the chapter: it is what
          // the "back to the chapter list" link is built from.
          storyId: payload.type === "STORY_DELETED" ? null : (payload.storyId ?? null),
          refunded: Boolean(payload.refunded),
        });

        // Close from this side rather than letting the server's `complete()`
        // stand on its own. A cleanly closed EventSource reconnects by design,
        // and reconnecting to a chapter that no longer exists would hold an
        // open request for something that can never have news again.
        source.close();
      } catch {
        // A frame we cannot read is not worth breaking the page over.
      }
    };

    source.addEventListener("chapter-updated", onUpdate);
    source.addEventListener("content-deleted", onDeleted);

    // Deliberately no error handler beyond closing nothing: EventSource
    // reconnects on its own, and surfacing a transient network blip as an error
    // in the reading UI would be noise about a feature the reader never asked
    // for.
    return () => {
      source.removeEventListener("chapter-updated", onUpdate);
      source.removeEventListener("content-deleted", onDeleted);
      source.close();
    };
  }, [chapterId]);

  const dismiss = useCallback(() => setAnnouncedVersion(null), []);

  const staleVersion =
    announcedVersion !== null &&
    currentVersion != null &&
    announcedVersion > currentVersion &&
    // A chapter that has been removed cannot also be offering a newer version.
    // Both banners on screen at once would be the page arguing with itself.
    deletion === null
      ? announcedVersion
      : null;

  return { staleVersion, dismiss, deletion };
}
