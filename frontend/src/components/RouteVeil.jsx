import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { onInFlightChange } from "../api/client";
import { LOGO_MARK } from "../brand";

/**
 * The shortest the mark is ever on screen.
 *
 * Long enough to actually watch: the band has to draw itself on, the earcups
 * arrive after it and a ring of sound has to make most of one journey out. At
 * the 300ms this started on, all three were still mid-move when the fade began
 * and the whole thing registered as a flicker.
 */
const MIN_HOLD_MS = 900;

/** The fade itself. */
const FADE_MS = 420;

/**
 * When a wait stops being a page load and starts needing an explanation.
 *
 * The backend sleeps after a spell of no traffic and takes around a minute to
 * come back. Under four seconds is just a slow page and saying anything about
 * it would be noise; past four, silence is what makes people think the site is
 * broken and reach for the reload button — which starts the wait over.
 */
const EXPLAIN_AFTER_MS = 4000;

/**
 * The point at which the veil gives up regardless.
 *
 * A loading screen that can outlast the thing it is waiting for is a trap. If
 * the server has not answered by now it is not going to on this attempt, and
 * the page underneath — which has its own error handling — deserves the screen
 * back. Set beyond a cold start's worst case so it only ever fires on a genuine
 * failure.
 */
const GIVE_UP_MS = 90_000;

/**
 * The screen between pages.
 *
 * A pair of headphones with the brand mark inside them and sound rippling out —
 * the site is about listening, and the one moment every visitor sees on every
 * page is worth spending on saying so.
 *
 * It is also the loading screen. The host puts the backend to sleep after a
 * spell without traffic and takes about a minute to bring it back, so the veil
 * leaves on whichever comes later: the animation finishing, or the API going
 * quiet. Holding for a fixed beat and then revealing an empty page is what
 * makes a cold start look like a fault — and a reader who thinks it faulted
 * reloads, which throws away the wait and starts another one.
 *
 * Three things keep it from becoming a tax on using the site:
 *
 * - On a warm server it is over in 1.3s. Page requests finish well inside the
 *   minimum hold, so waiting on them costs nothing.
 * - It never captures the pointer. `pointer-events: none` means a click during
 *   the fade reaches the page as normal, and — more to the point — a veil that
 *   somehow failed to clear could never lock the reader out of the site.
 * - It cannot outlast what it is waiting for. Every timer is cleaned up, a
 *   navigation landing mid-fade restarts the sequence rather than stacking a
 *   second one, and {@link GIVE_UP_MS} caps the whole thing.
 *
 * Mounted beside `<Routes>` rather than inside a layout: there are four layouts
 * and a reader moving from a story to a chapter crosses between two of them.
 */
export default function RouteVeil() {
  const { pathname } = useLocation();

  // `hold` on the very first render too, so the first paint of the site gets
  // the same treatment — it doubles as the loading screen.
  const [phase, setPhase] = useState("hold");

  // Whether the wait has gone on long enough to be worth explaining.
  const [explaining, setExplaining] = useState(false);

  useEffect(() => {
    setPhase("hold");
    setExplaining(false);

    /*
     * Two conditions, both of which must be met before the veil clears: the
     * minimum hold has elapsed, and nothing is outstanding with the API.
     *
     * Tracked in plain variables rather than state because the subscription
     * below fires outside React's render cycle and this effect must read the
     * newest value of each without re-subscribing.
     */
    let held = false;
    let outstanding = 0;
    let left = false;

    const timers = [];

    const leave = () => {
      if (left) return;
      left = true;
      setPhase("fade");
      timers.push(setTimeout(() => setPhase("idle"), FADE_MS));
    };

    const leaveIfReady = () => {
      if (held && outstanding === 0) leave();
    };

    timers.push(
      setTimeout(() => {
        held = true;
        leaveIfReady();
      }, MIN_HOLD_MS),
    );

    timers.push(setTimeout(() => setExplaining(true), EXPLAIN_AFTER_MS));
    timers.push(setTimeout(leave, GIVE_UP_MS));

    /*
     * Fires immediately with the current count, which is all but always zero at
     * this instant: the page for the new route has rendered but its effects
     * have not run yet, so its requests are still a tick away. That is why the
     * minimum hold has to pass before an empty count means anything.
     */
    const unsubscribe = onInFlightChange((count) => {
      outstanding = count;
      leaveIfReady();
    });

    return () => {
      left = true;
      timers.forEach(clearTimeout);
      unsubscribe();
    };
  }, [pathname]);

  // Unmounted between navigations rather than left sitting at zero opacity: an
  // invisible full-screen layer is the kind of thing that comes back to haunt a
  // stacking context later.
  if (phase === "idle") return null;

  return (
    /*
     * Hidden from assistive tech while it is only decoration, but announced
     * once it is carrying information a sighted reader is getting — a screen
     * reader user waiting a silent minute needs the explanation more than
     * anyone.
     */
    <div
      className={`veil ${phase === "fade" ? "is-leaving" : ""}`}
      aria-hidden={explaining ? undefined : "true"}
      role={explaining ? "status" : undefined}
    >
      <div className="veil-mark">
        {/*
          Three bands of geometry, each with clear air around it. The first
          attempt had all three sharing the same space — the logo sat across
          both earcups and the headband cut through it — and it read as a
          smudge rather than as a pair of headphones.

            logo    reaches 24 out from the centre
            rig     nearest point 33 (the inner edge of an earcup), band 36
            waves   start at 62, clearing the far corner of a cup at 55.8

          All of it is in viewBox units on a 160 square, so the centre is
          (80, 80) and those numbers compare directly.
        */}
        <svg className="veil-rig" viewBox="0 0 160 160">
          {/*
            Sound leaving the headphones. Three rings on the same path, started a
            third of a second apart, so the ripple reads as continuous over a
            window shorter than one ring's own journey.
          */}
          <circle className="veil-wave" cx="80" cy="80" r="62" />
          <circle className="veil-wave" cx="80" cy="80" r="62" />
          <circle className="veil-wave" cx="80" cy="80" r="62" />

          {/* The headband, drawn on rather than simply faded in. A half circle
              of radius 40, so its length is pi x 40 = 125.7. */}
          <path className="veil-band" d="M40 84a40 40 0 0 1 80 0" />

          <rect className="veil-cup" x="33" y="80" width="14" height="30" rx="7" />
          <rect className="veil-cup" x="113" y="80" width="14" height="30" rx="7" />
        </svg>

        <img className="veil-logo" src={LOGO_MARK} alt="" width="64" height="64" />
      </div>

      <p className="veil-word">Truyện Nghe</p>

      {/*
        Only once the wait has run past the point where silence starts to look
        like a fault. Says what is happening and roughly how long it takes, so
        the honest response is to wait rather than to reload — a reload throws
        away the minute already spent and buys another one.
      */}
      {explaining && (
        <div className="veil-note">
          <span className="veil-note-bar" aria-hidden="true" />
          <p>
            Máy chủ đang khởi động lại sau một thời gian không có ai truy cập. Lần tải đầu tiên có
            thể mất tới một phút — bạn cứ chờ, đừng tải lại trang.
          </p>
        </div>
      )}
    </div>
  );
}
