import { Link } from "react-router-dom";
import { AccessBadge, Badge } from "./ui";

/**
 * One line in a story's chapter list.
 *
 * Locked chapters stay visible and clickable on purpose: the reader page shows
 * an explanation of what is required, which is friendlier than hiding the row.
 */
export default function ChapterRow({ chapter }) {
  return (
    <li>
      <Link
        to={`/chuong/${chapter.id}`}
        className={`chapter-row ${chapter.locked ? "chapter-row-locked" : ""}`}
      >
        <span className="chapter-number">{chapter.chapterNumber}</span>

        <span className="chapter-title">
          {chapter.title}
          {chapter.locked && (
            <span aria-label="Chương bị khóa" style={{ marginLeft: "0.4rem" }}>
              🔒
            </span>
          )}
        </span>

        {chapter.hasAudio && <Badge tone="info">🎧 Có audio</Badge>}

        {chapter.accessLevel !== "PUBLIC" && (
          <AccessBadge level={chapter.accessLevel} label={chapter.requirementLabel} />
        )}
      </Link>
    </li>
  );
}
