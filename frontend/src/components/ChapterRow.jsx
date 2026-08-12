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

const LockIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <rect x="4.5" y="10.5" width="15" height="10" rx="2" />
    <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
  </svg>
);

/**
 * One line in a story's chapter list.
 *
 * A locked row says so, and says what is missing — signing in, or VIP. Leaving
 * it to the reader page would mean every locked chapter is discovered by
 * clicking into a refusal, and a reader scanning a long list could not tell
 * which chapters are actually theirs to open.
 *
 * `locked` and `requirementLabel` both come from the server, which decides them
 * against the caller's own standing: the same row reads differently to a guest,
 * a member and a VIP.
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

        {chapter.locked && (
          <span className="chapter-lock">
            <LockIcon />
            {chapter.requirementLabel}
          </span>
        )}
      </Link>
    </li>
  );
}
