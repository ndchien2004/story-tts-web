import { Button } from "./ui";

const MAX_VISIBLE_PAGES = 5;

/** Builds a sliding window of page numbers centred on the current page. */
function visiblePages(current, total) {
  if (total <= MAX_VISIBLE_PAGES) {
    return Array.from({ length: total }, (_, index) => index);
  }
  const half = Math.floor(MAX_VISIBLE_PAGES / 2);
  const start = Math.min(Math.max(current - half, 0), total - MAX_VISIBLE_PAGES);
  return Array.from({ length: MAX_VISIBLE_PAGES }, (_, index) => start + index);
}

export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <nav className="pagination" aria-label="Phân trang">
      <Button size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Trước
      </Button>

      {visiblePages(page, totalPages).map((index) => (
        <Button
          key={index}
          size="sm"
          className={`pagination-page ${index === page ? "active" : ""}`}
          aria-current={index === page ? "page" : undefined}
          onClick={() => onChange(index)}
        >
          {index + 1}
        </Button>
      ))}

      <Button size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Sau
      </Button>
    </nav>
  );
}
