import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { LOGO_MARK } from "../brand";

/**
 * How long the mark holds at full strength before it starts to clear.
 *
 * Long enough to actually watch: the band has to draw itself on, the earcups
 * arrive after it and a ring of sound has to make most of one journey out. At
 * the 300ms this started on, all three were still mid-move when the fade began
 * and the whole thing registered as a flicker.
 */
const HOLD_MS = 900;

/** The fade itself. Together these two are the whole cost of a navigation. */
const FADE_MS = 420;

/**
 * The screen between pages.
 *
 * A pair of headphones with the brand mark inside them and sound rippling out —
 * the site is about listening, and the one moment every visitor sees on every
 * page is worth spending on saying so.
 *
 * Three things keep it from becoming a tax on using the site:
 *
 * - It is short. 580ms end to end, and the new page is already rendered
 *   underneath the whole time — nothing is being waited for, the veil is only
 *   drawn over the top.
 * - It never captures the pointer. `pointer-events: none` means a click during
 *   the fade reaches the page as normal, and — more to the point — a veil that
 *   somehow failed to clear could never lock the reader out of the site.
 * - Both timers are cleaned up on the way out, and a navigation that lands
 *   mid-fade simply restarts the sequence rather than stacking a second one.
 *
 * Mounted beside `<Routes>` rather than inside a layout: there are four layouts
 * and a reader moving from a story to a chapter crosses between two of them.
 */
export default function RouteVeil() {
  const { pathname } = useLocation();

  // `hold` on the very first render too, so the first paint of the site gets
  // the same treatment — it doubles as the loading screen.
  const [phase, setPhase] = useState("hold");

  useEffect(() => {
    setPhase("hold");

    const fade = setTimeout(() => setPhase("fade"), HOLD_MS);
    const done = setTimeout(() => setPhase("idle"), HOLD_MS + FADE_MS);

    return () => {
      clearTimeout(fade);
      clearTimeout(done);
    };
  }, [pathname]);

  // Unmounted between navigations rather than left sitting at zero opacity: an
  // invisible full-screen layer is the kind of thing that comes back to haunt a
  // stacking context later.
  if (phase === "idle") return null;

  return (
    <div className={`veil ${phase === "fade" ? "is-leaving" : ""}`} aria-hidden="true">
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
    </div>
  );
}
