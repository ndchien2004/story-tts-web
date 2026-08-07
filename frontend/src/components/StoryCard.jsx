import { Link } from "react-router-dom";
import { Badge } from "./ui";

/** Derives up to two initials to fill the placeholder cover. */
function initialsOf(title) {
  return title
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

/** Rotates placeholder cover colours so a grid of them stays readable. */
const PLACEHOLDER_TINTS = [
  "var(--accent)",
  "var(--info)",
  "var(--success)",
  "var(--violet)",
  "var(--warning)",
  "var(--danger)",
];

export default function StoryCard({ story }) {
  const tint = PLACEHOLDER_TINTS[story.id % PLACEHOLDER_TINTS.length];
  const hasCover = Boolean(story.coverImage);

  return (
    <Link to={`/truyen/${story.id}`} className="nb-card story-card">
      <div
        className={`story-cover ${hasCover ? "" : "story-cover-placeholder"}`}
        style={hasCover ? undefined : { background: tint }}
      >
        {hasCover ? (
          <img src={story.coverImage} alt="" loading="lazy" />
        ) : (
          <span className="story-cover-initials">{initialsOf(story.title)}</span>
        )}

        {story.genre && (
          <Badge tone="neutral" className="story-cover-chip">
            {story.genre.name}
          </Badge>
        )}
      </div>

      <div className="story-card-body">
        <span className="story-card-title">{story.title}</span>
        <span className="story-card-author">{story.author?.name ?? "Chưa rõ tác giả"}</span>

        <div className="story-card-meta">
          <span>{story.chapterCount} chương</span>
          <span>{story.statusLabel}</span>
        </div>
      </div>
    </Link>
  );
}
