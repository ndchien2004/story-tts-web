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

  return (
    <Link to={`/truyen/${story.id}`} className="nb-card story-card">
      <div className="story-cover" style={{ background: tint }}>
        {story.coverImage ? (
          <img src={story.coverImage} alt="" loading="lazy" />
        ) : (
          <span className="story-cover-initials">{initialsOf(story.title)}</span>
        )}
      </div>

      <div className="story-card-body">
        <span className="story-card-title">{story.title}</span>
        <span className="muted" style={{ fontSize: "0.88rem" }}>
          {story.author?.name ?? "Chưa rõ tác giả"}
        </span>
        <div className="row" style={{ gap: "0.35rem" }}>
          {story.genre && <Badge tone="info">{story.genre.name}</Badge>}
          <Badge tone={story.status === "COMPLETED" ? "public" : "neutral"}>
            {story.statusLabel}
          </Badge>
        </div>
        <span className="muted" style={{ fontSize: "0.82rem" }}>
          {story.chapterCount} chương · {story.viewCount} lượt xem
        </span>
      </div>
    </Link>
  );
}
