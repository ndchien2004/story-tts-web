import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronIcon } from "./ui";

/** How many covers stay on screen either side of the middle one. */
const VISIBLE_SIDES = 2;

/** Derives up to two initials to fill a cover the story has no artwork for. */
function initialsOf(title) {
  return title
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

/**
 * Signed distance from `index` to `active` around a ring of `total` items.
 *
 * The shelf wraps, so the last cover is one step before the first rather than
 * `total - 1` steps after it. Without this the jump from the end back to the
 * start would fling every cover across the screen.
 */
function ringOffset(index, active, total) {
  const raw = index - active;
  const half = total / 2;
  if (raw > half) return raw - total;
  if (raw < -half) return raw + total;
  return raw;
}

/**
 * The shelf at the top of the home page.
 *
 * One cover is in focus, sharp and lifted; its neighbours sit behind it, turned
 * away, scaled down and blurred, so depth does the work of saying which one is
 * being offered. The blur is the point: a row of equally crisp covers is a
 * list, and the page already has three of those below.
 *
 * Position lives here rather than in CSS keyframes because the arrows, the
 * dots, a click on a neighbour and the arrow keys all move the same thing, and
 * each cover's transform is derived from its distance to the middle.
 */
export default function FeaturedCarousel({ stories }) {
  const [stored, setStored] = useState(0);
  const total = stories.length;

  /*
   * Clamped as it is read, not corrected afterwards by an effect.
   *
   * A shelf can shrink under the cursor — a shorter response, a filter applied
   * — and an effect only runs after the render that already tried to read
   * `stories[active]`. Clamping here means there is no render in which that
   * lookup is out of bounds.
   */
  const active = total === 0 ? 0 : Math.min(stored, total - 1);
  const current = stories[active];

  // Wraps in both directions: -1 % 6 is -1 in JavaScript, not 5.
  const step = useCallback(
    (delta) => setStored((index) => (index + delta + total) % total),
    [total],
  );

  /**
   * Arrow keys drive the shelf only while it holds focus.
   *
   * Bound to the region rather than the document: left and right are also how
   * you move a text cursor, and a home page that hijacks them everywhere is a
   * home page you cannot type on.
   */
  function handleKeyDown(event) {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      step(-1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      step(1);
    }
  }

  if (total === 0) return null;

  return (
    <section
      className="shelf"
      tabIndex={-1}
      onKeyDown={handleKeyDown}
      aria-roledescription="carousel"
      aria-label="Truyện nổi bật"
    >
      {/*
        The viewport is capped to roughly the width the covers actually occupy,
        which is what lets the two arrows sit at `left: 0` and `right: 0` and
        still land immediately beside the outermost cover rather than out at the
        edge of the page. On a screen too narrow for that gap they overlay those
        outer covers instead — which are blurred and half faded, so there is
        nothing there to read.
      */}
      <div className="shelf-viewport">
        <button
          type="button"
          className="shelf-arrow shelf-arrow-prev"
          onClick={() => step(-1)}
          aria-label="Truyện trước"
        >
          <ChevronIcon />
        </button>

        <div className="shelf-stage">
          {stories.map((story, index) => {
            const offset = ringOffset(index, active, total);
            const distance = Math.abs(offset);
            const isCentre = distance === 0;

            // Beyond the third cover out there is nothing left to see: it would
            // be blurred past recognition and stacked behind two others.
            if (distance > VISIBLE_SIDES) return null;

            const hasCover = Boolean(story.coverImage);

            return (
              <div
                key={story.id}
                className={`shelf-slide ${isCentre ? "is-centre" : ""}`}
                // The three values the stylesheet needs to place this cover.
                // Kept inline because they are per-cover geometry, not styling —
                // a class per possible offset would be five classes saying the
                // same arithmetic.
                style={{
                  "--offset": offset,
                  "--distance": distance,
                  zIndex: VISIBLE_SIDES - distance,
                }}
                aria-hidden={!isCentre}
              >
                {/*
                  A neighbour is a target for one thing only: bringing it
                  forward. Making it a link to the story would mean a click that
                  lands on something half-hidden and blurred takes you off the
                  page.
                */}
                {isCentre ? (
                  <Link to={`/truyen/${story.id}`} className="shelf-cover">
                    {hasCover ? (
                      <img src={story.coverImage} alt="" />
                    ) : (
                      <span className="shelf-initials">{initialsOf(story.title)}</span>
                    )}
                  </Link>
                ) : (
                  <button
                    type="button"
                    className="shelf-cover"
                    tabIndex={-1}
                    onClick={() => setStored(index)}
                    aria-label={`Xem ${story.title}`}
                  >
                    {hasCover ? (
                      <img src={story.coverImage} alt="" />
                    ) : (
                      <span className="shelf-initials">{initialsOf(story.title)}</span>
                    )}
                  </button>
                )}
              </div>
            );
          })}
        </div>

        <button
          type="button"
          className="shelf-arrow shelf-arrow-next"
          onClick={() => step(1)}
          aria-label="Truyện sau"
        >
          <ChevronIcon right />
        </button>
      </div>

      {/* The caption sits under the stage rather than over the artwork: a
          cover is a picture with words already on it. */}
      <div className="shelf-caption">
        <p className="shelf-eyebrow">
          {current.genre?.name ?? "Truyện nổi bật"}
        </p>
        <h2 className="shelf-title">
          <Link to={`/truyen/${current.id}`}>{current.title}</Link>
        </h2>
        <p className="shelf-author">{current.author?.name ?? "Chưa rõ tác giả"}</p>

        {/*
          Rendered even when there is nothing to say. The stylesheet reserves
          two lines here, and dropping the element on a story without a
          description would hand that space back — which is exactly the jolt
          between covers this is meant to remove.
        */}
        <p className="shelf-blurb">{current.description ?? ""}</p>
      </div>

      {/* The arrows moved up beside the covers, so this row is the dots alone. */}
      <div className="shelf-dots" role="tablist" aria-label="Chọn truyện">
        {stories.map((story, index) => (
          <button
            key={story.id}
            type="button"
            role="tab"
            className={`shelf-dot ${index === active ? "is-active" : ""}`}
            aria-selected={index === active}
            aria-label={story.title}
            onClick={() => setStored(index)}
          />
        ))}
      </div>
    </section>
  );
}
