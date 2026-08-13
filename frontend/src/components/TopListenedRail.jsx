import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { storyApi } from "../api/endpoints";
import { Spinner } from "./ui";

const PERIODS = [
  { key: "day", label: "Ngày" },
  { key: "week", label: "Tuần" },
  { key: "month", label: "Tháng" },
];

const HOW_MANY = 8;

/** 12.400 rather than 12400 — a ranking is read at a glance, not parsed. */
const formatCount = (value) => new Intl.NumberFormat("vi-VN").format(value);

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
 * "Nghe nhiều nhất" down the right edge of the home page.
 *
 * The three tabs are three different questions, not three slices of one
 * answer, so each is fetched on demand and kept once fetched: flicking between
 * them is the whole point of the control and should not re-query every time.
 *
 * Counts come from the listen events inside the chosen window, so a story that
 * was big last month can sit below one that only started climbing yesterday —
 * which is what makes the day tab worth having.
 */
export default function TopListenedRail() {
  const [period, setPeriod] = useState("day");
  const [cache, setCache] = useState({});
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (cache[period]) return undefined;

    let cancelled = false;
    setLoading(true);
    setFailed(false);

    storyApi
      .topListened({ period, limit: HOW_MANY })
      .then((rows) => {
        if (!cancelled) setCache((current) => ({ ...current, [period]: rows }));
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [period, cache]);

  const rows = cache[period];

  return (
    <section className="rank-rail" aria-label="Truyện nghe nhiều nhất">
      <header className="rank-rail-header">
        <h2>Nghe nhiều nhất</h2>

        <div className="rank-tabs" role="tablist">
          {PERIODS.map((option) => (
            <button
              key={option.key}
              type="button"
              role="tab"
              aria-selected={period === option.key}
              className={`rank-tab ${period === option.key ? "is-active" : ""}`}
              onClick={() => setPeriod(option.key)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </header>

      {/* Only the first load of a tab shows the spinner; a tab already in the
          cache swaps instantly. */}
      {loading && !rows && <Spinner label="Đang xếp hạng…" />}

      {failed && !rows && <p className="muted rank-empty">Chưa lấy được bảng xếp hạng.</p>}

      {rows && rows.length === 0 && (
        <p className="muted rank-empty">Chưa có lượt nghe nào trong khoảng này.</p>
      )}

      {rows && rows.length > 0 && (
        <ol className="rank-list">
          {rows.map((story, index) => (
            <li key={story.id}>
              <Link to={`/truyen/${story.id}`} className="rank-row">
                {/* The top three carry the weight; below that the number is
                    just a position and stays quiet. */}
                <span className={`rank-position ${index < 3 ? "is-podium" : ""}`}>
                  {index + 1}
                </span>

                <span className="rank-thumb">
                  {story.coverImage ? (
                    <img src={story.coverImage} alt="" loading="lazy" />
                  ) : (
                    <span className="rank-thumb-initials">{initialsOf(story.title)}</span>
                  )}
                </span>

                <span className="rank-body">
                  <strong className="rank-title">{story.title}</strong>
                  <small className="rank-meta">
                    {story.genreName ?? "Chưa phân loại"} · {formatCount(story.listenCount)} lượt
                    nghe
                  </small>
                </span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
