import { useCallback, useEffect, useState } from "react";
import {
  TOKEN_STORAGE_KEY,
  consumeLockedNotice,
  onSessionTerminated,
  terminateSession,
} from "../api/client";
import { useAuth } from "../context/auth-context";
import { Alert } from "./ui";

/**
 * What happens when the server ends a session out from under the reader.
 *
 * Detection lives in the API client, which sees every call; this is the single
 * place that reacts to it. Keeping the two apart is what stops the reaction
 * from being duplicated into pages — the client cannot navigate, and a page
 * cannot see calls made by other pages.
 *
 * Rendered outside `<Routes>`, next to `RouteVeil`, for the same reason that one
 * is: it has to survive the navigation it is causing.
 */
export default function SessionGuard() {
  const { logout } = useAuth();

  // Read once on mount and cleared in the same breath, so a later reload does
  // not show yesterday's news.
  const [locked, setLocked] = useState(() => consumeLockedNotice());

  useEffect(() => {
    return onSessionTerminated(() => {
      // Clears the React-side user as well as the token. Mostly belt and braces
      // given the reload below, but it means the state is already correct if
      // that reload is ever slow to take effect.
      logout();

      // A full navigation rather than a router push, and this is the part worth
      // being deliberate about.
      //
      // Signing out has to stop far more than routing does: narration playing
      // through Web Audio, the SSE connection watching the chapter, the poll
      // loop waiting on a synthesis job, the timer that saves listening
      // position. Unmounting is supposed to clean all of that up, and mostly
      // does — but "mostly" is the wrong standard for a locked account, and
      // every future background task would silently join the list of things
      // that have to remember to stop.
      //
      // Replacing the document ends all of it at once, by construction. `replace`
      // rather than `assign` so Back cannot return to the page they were locked
      // out of.
      window.location.replace("/");
    });
  }, [logout]);

  /*
   * Other tabs.
   *
   * The tab that made the call signs itself out; the rest learn about it from
   * `localStorage`, which fires a `storage` event in every *other* tab of the
   * origin when a key changes. That is already how the token is shared between
   * them, so nothing new is needed to keep them in step - no channel, no
   * polling, no second source of truth.
   *
   * A tab sitting on a public page with nobody signed in has nothing to do
   * here, hence the `newValue` check: this fires on every write to the key,
   * including a fresh sign-in.
   */
  useEffect(() => {
    const onStorage = (event) => {
      if (event.key !== TOKEN_STORAGE_KEY || event.newValue !== null) return;
      terminateSession();
    };

    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const dismiss = useCallback(() => setLocked(false), []);

  if (!locked) return null;

  return (
    <div className="container session-notice">
      <Alert tone="error" title="Tài khoản của bạn đã bị khóa">
        Bạn đã được đăng xuất. Vui lòng liên hệ quản trị viên nếu bạn cho rằng đây là nhầm lẫn.
        <button type="button" className="session-notice-close" onClick={dismiss} aria-label="Đóng">
          ×
        </button>
      </Alert>
    </div>
  );
}
