import { useCallback, useEffect, useRef, useState } from "react";
import { AdminToastContext } from "../context/admin-toast-context";

/**
 * How long the bar stays put between arriving and leaving.
 *
 * Long enough to read one sentence twice, short enough that it is gone before
 * it becomes something to wait out. The same figure drives the hairline
 * draining along the bottom edge, which is handed to CSS below rather than
 * written there — one number, one place.
 */
const LIFE_MS = 3000;

/** The slide out. Kept in step with `admin-toast-out` in admin.css. */
const LEAVE_MS = 260;

/** Ids only have to be unique within a session, and a counter is that. */
let sequence = 0;

function CheckIcon() {
  return (
    <svg
      className="admin-toast-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.4}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}

/**
 * The console's confirmations, all of them, in one place at the top right.
 *
 * They used to be an `<Alert>` pushed in above the page: the table jumped down
 * a line to make room, jumped back when the message was dismissed, and on a
 * screen scrolled halfway down the message appeared somewhere off-screen above.
 * A layer over the corner costs the page no room at all and is always where it
 * was last time.
 *
 * The motion runs one way, right to left. The bar comes in from off the right
 * edge, holds for {@link LIFE_MS}, then carries on leftwards as it fades —
 * arriving and leaving read as one continuous movement rather than as a thing
 * that backs out the way it came.
 *
 * It never takes the pointer: `pointer-events: none` in the stylesheet means a
 * button underneath the corner stays clickable while a message is up, and a
 * toast that somehow failed to clear could not lock anything.
 */
export default function AdminToaster({ children }) {
  const [toasts, setToasts] = useState([]);

  // Every timer this component has outstanding, so unmounting mid-flight
  // cannot leave one to fire a setState into nothing.
  const timers = useRef([]);

  useEffect(() => {
    const pending = timers.current;
    return () => pending.forEach(clearTimeout);
  }, []);

  const notify = useCallback((message) => {
    if (!message) return;

    const id = (sequence += 1);
    setToasts((list) => [...list, { id, message, leaving: false }]);

    timers.current.push(
      // Two steps rather than one: the bar has to be told it is going before it
      // goes, or it would vanish rather than leave.
      setTimeout(
        () => setToasts((list) => list.map((t) => (t.id === id ? { ...t, leaving: true } : t))),
        LIFE_MS,
      ),
      setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), LIFE_MS + LEAVE_MS),
    );
  }, []);

  return (
    <AdminToastContext.Provider value={notify}>
      {children}

      {/*
        Announced politely: a confirmation is worth hearing but never worth
        cutting across whatever the screen reader is in the middle of. The
        region stays mounted and empty so the reader is already watching it
        when the first message lands.
      */}
      <div className="admin-toaster" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`admin-toast ${toast.leaving ? "is-leaving" : ""}`}
            style={{ "--admin-toast-life": `${LIFE_MS}ms` }}
          >
            <CheckIcon />
            <span className="admin-toast-text">{toast.message}</span>
            <span className="admin-toast-timer" aria-hidden="true" />
          </div>
        ))}
      </div>
    </AdminToastContext.Provider>
  );
}
