/**
 * A reader's picture, or the first letter of their name when they have none.
 *
 * Both forms occupy exactly the same square so a mixed list — a comment thread,
 * say — still lines up down its left edge.
 */
export default function Avatar({ src, name, size = "md", className = "" }) {
  const initial = (name ?? "").trim().slice(0, 1).toUpperCase() || "?";

  return (
    <span className={`avatar avatar-${size} ${className}`.trim()}>
      {src ? (
        <img src={src} alt="" loading="lazy" />
      ) : (
        <span aria-hidden="true">{initial}</span>
      )}
    </span>
  );
}
