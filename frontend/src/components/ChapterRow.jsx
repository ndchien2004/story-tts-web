import { Link } from "react-router-dom";
import { Badge } from "./ui";

const CheckIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={3.2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="m4.5 12.5 5 5 10-11" />
  </svg>
);

/**
 * One line in a story's chapter list.
 *
 * The access level is deliberately not spelled out here. Every row is clickable
 * and the reader page states what is missing when the server refuses, which
 * keeps the list clean instead of stamping requirements on rows the visitor may
 * well be allowed to open.
 *
 * @param read the current reader has finished this chapter
 */
export default function ChapterRow({ chapter, read = false }) {
  return (
    <li>
      <Link
        to={`/chuong/${chapter.id}`}
        className={`chapter-row ${chapter.locked ? "chapter-row-locked" : ""} ${read ? "chapter-row-read" : ""}`}
      >
        {/* The number badge turns into the tick, so a read chapter costs no
            extra width in a list that is already tight. */}
        <span className="chapter-number" title={read ? "Đã đọc" : undefined}>
          {read ? <CheckIcon /> : chapter.chapterNumber}
        </span>

        <span className="chapter-title">{chapter.title}</span>

        {read && <span className="sr-only">Đã đọc</span>}

        {chapter.hasAudio && <Badge tone="info">Có audio</Badge>}
      </Link>
    </li>
  );
}
