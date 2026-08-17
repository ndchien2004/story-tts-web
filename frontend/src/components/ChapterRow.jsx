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

const CoinIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="8" />
    <path d="M12 8.2v7.6M9.9 10.1h3.2a1.9 1.9 0 0 1 0 3.8H9.9" />
  </svg>
);

/**
 * One line in a story's chapter list.
 *
 * A locked row says so, and says what is missing — signing in, VIP, or a price
 * in Xu. Leaving it to the reader page would mean every locked chapter is
 * discovered by clicking into a refusal, and a reader scanning a long list could
 * not tell which chapters are actually theirs to open.
 *
 * `locked`, `purchasable` and `requirementLabel` all come from the server, which
 * decides them against the caller's own standing: the same row reads differently
 * to a guest, a member, someone who already bought it, and a VIP.
 *
 * <p>Một khóa mở được bằng Xu hiện giá thay vì hiện chữ "Yêu cầu VIP": hai thứ
 * đòi hai hành động khác nhau, và cái ổ khóa chung cho cả hai từng khiến chương
 * bán lẻ trông như chương không mua được.
 *
 * @param read the current reader has finished this chapter
 */
export default function ChapterRow({ chapter, read = false }) {
  const forSale = chapter.locked && chapter.purchasable;

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

        {forSale && (
          <span className="chapter-price" title="Mở khóa bằng Xu">
            <CoinIcon />
            {chapter.coinPrice.toLocaleString("vi-VN")} Xu
          </span>
        )}

        {chapter.locked && !forSale && (
          <span className="chapter-lock">
            <LockIcon />
            {chapter.requirementLabel}
          </span>
        )}
      </Link>
    </li>
  );
}
