import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { catalogApi } from "../api/endpoints";

/** Long enough to cross the gap between the label and the panel below it. */
const CLOSE_DELAY_MS = 140;

/**
 * Tints cycled across the grid so a genre keeps the same colour every time the
 * menu opens — position in an alphabetical list is stable, and a colour that
 * moved between visits would be worse than no colour at all.
 */
const TINTS = ["accent", "info", "success", "violet", "warning", "danger"];

/**
 * The "Thể loại" navbar item and the panel of genres it opens.
 *
 * Hover opens it, which is what a pointer expects of a menu in a bar like this
 * — but hover is the only thing a mouse can do, so the label is also a real
 * button: click or Enter toggles the same panel, which is what a keyboard and a
 * touchscreen have to rely on. Inside, the arrow keys walk the grid, Home and
 * End jump to its ends, and Escape closes and hands focus back to the label.
 *
 * Leaving is on a short delay. Without it, the sliver of space between the
 * label and the panel counts as "outside" and the menu shuts under the cursor
 * on its way down.
 *
 * The genres are fetched once when the bar mounts rather than on first hover:
 * a panel that opens empty and fills in a moment later is worse than one that
 * is simply ready.
 */
export default function GenreMenu({ onNavigate }) {
  const [genres, setGenres] = useState([]);
  const [open, setOpen] = useState(false);

  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const panelRef = useRef(null);
  const closeTimer = useRef(0);

  // The catalogue lost its own link in the bar, so this item carries the "you
  // are here" mark for every page under it — the listing and the stories on it.
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const onCatalogue = pathname === "/truyen" || pathname.startsWith("/truyen/");
  const currentGenreId = pathname === "/truyen" ? searchParams.get("genreId") : null;

  useEffect(() => {
    let cancelled = false;
    catalogApi
      .genres()
      .then((data) => {
        if (!cancelled) setGenres(data);
      })
      .catch(() => {
        if (!cancelled) setGenres([]);
      });

    return () => {
      cancelled = true;
      clearTimeout(closeTimer.current);
    };
  }, []);

  useEffect(() => {
    if (!open) return undefined;

    const onKeyDown = (event) => {
      if (event.key !== "Escape") return;
      setOpen(false);
      triggerRef.current?.focus();
    };
    const onPointerDown = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    };

    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("mousedown", onPointerDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("mousedown", onPointerDown);
    };
  }, [open]);

  const cancelClose = () => clearTimeout(closeTimer.current);

  const scheduleClose = () => {
    cancelClose();
    closeTimer.current = setTimeout(() => setOpen(false), CLOSE_DELAY_MS);
  };

  const closeNow = () => {
    cancelClose();
    setOpen(false);
    onNavigate?.();
  };

  /** Opens on Down from the label and lands on the first genre, as a menu should. */
  function handleTriggerKeyDown(event) {
    if (event.key !== "ArrowDown") return;
    event.preventDefault();
    setOpen(true);
    // Wait for the panel to exist before reaching into it.
    requestAnimationFrame(() => panelRef.current?.querySelector("a")?.focus());
  }

  /** Roving focus across the grid. The links are in DOM order, so index maths is enough. */
  function handlePanelKeyDown(event) {
    const items = Array.from(panelRef.current?.querySelectorAll(".genre-grid-item") ?? []);
    if (items.length === 0) return;

    const current = items.indexOf(document.activeElement);
    const columns = 2;
    let next = null;

    if (event.key === "ArrowRight") next = current + 1;
    else if (event.key === "ArrowLeft") next = current - 1;
    else if (event.key === "ArrowDown") next = current + columns;
    else if (event.key === "ArrowUp") next = current - columns;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = items.length - 1;
    else return;

    event.preventDefault();
    // Above the first row, go back to the label rather than trapping focus.
    if (next < 0 && (event.key === "ArrowUp" || event.key === "ArrowLeft")) {
      triggerRef.current?.focus();
      return;
    }
    items[Math.min(Math.max(next, 0), items.length - 1)]?.focus();
  }

  return (
    <div
      className="genre-menu"
      ref={containerRef}
      onMouseEnter={() => {
        cancelClose();
        setOpen(true);
      }}
      onMouseLeave={scheduleClose}
    >
      <button
        ref={triggerRef}
        type="button"
        className={`navbar-link genre-menu-trigger ${open ? "open" : ""} ${
          onCatalogue ? "active" : ""
        }`}
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        onKeyDown={handleTriggerKeyDown}
      >
        Thể loại
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={2.2}
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          className="genre-menu-caret"
        >
          <path d="m6 9.5 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className="genre-menu-panel" ref={panelRef} onKeyDown={handlePanelKeyDown}>
          {genres.length === 0 ? (
            <p className="muted" style={{ margin: 0, padding: "var(--space-2)" }}>
              Chưa có thể loại nào.
            </p>
          ) : (
            <div className="genre-grid">
              {genres.map((genre, index) => (
                <Link
                  key={genre.id}
                  to={`/truyen?genreId=${genre.id}`}
                  className={`genre-grid-item ${
                    String(genre.id) === currentGenreId ? "current" : ""
                  }`}
                  aria-current={String(genre.id) === currentGenreId ? "page" : undefined}
                  onClick={closeNow}
                >
                  <span
                    className={`genre-mark genre-mark-${TINTS[index % TINTS.length]}`}
                    aria-hidden="true"
                  >
                    {genre.name.slice(0, 1).toUpperCase()}
                  </span>

                  <span className="genre-grid-text">
                    <strong>{genre.name}</strong>
                    {genre.description && <span>{genre.description}</span>}
                  </span>

                  {genre.storyCount !== null && genre.storyCount !== undefined && (
                    <span className="genre-count">{genre.storyCount}</span>
                  )}
                </Link>
              ))}
            </div>
          )}

          {/* The catalogue no longer has a link of its own in the bar, so it
              keeps one here — otherwise browsing everything at once would mean
              picking a genre first. */}
          <Link to="/truyen" className="genre-menu-all" onClick={closeNow}>
            Xem tất cả truyện →
          </Link>
        </div>
      )}
    </div>
  );
}
