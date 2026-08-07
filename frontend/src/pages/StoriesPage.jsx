import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { catalogApi, storyApi } from "../api/endpoints";
import StoryCard from "../components/StoryCard";
import Pagination from "../components/Pagination";
import useDebouncedValue from "../hooks/useDebouncedValue";
import { Alert, EmptyState, Field, Select, Spinner, TextInput } from "../components/ui";

const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { value: "newest", label: "Mới nhất" },
  { value: "popular", label: "Phổ biến nhất" },
  { value: "oldest", label: "Cũ nhất" },
  { value: "title", label: "Tên A → Z" },
];

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả trạng thái" },
  { value: "ONGOING", label: "Đang ra" },
  { value: "COMPLETED", label: "Hoàn thành" },
];

export default function StoriesPage() {
  // The filter state lives in the URL so results can be shared or bookmarked.
  const [searchParams, setSearchParams] = useSearchParams();

  const keyword = searchParams.get("keyword") ?? "";
  const genreId = searchParams.get("genreId") ?? "";
  const status = searchParams.get("status") ?? "";
  const sort = searchParams.get("sort") ?? "newest";
  const page = Number(searchParams.get("page") ?? 0);

  const [keywordInput, setKeywordInput] = useState(keyword);
  const debouncedKeyword = useDebouncedValue(keywordInput);

  const [genres, setGenres] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    catalogApi.genres().then(setGenres).catch(() => setGenres([]));
  }, []);

  // Push the debounced keyword into the URL, resetting to the first page.
  useEffect(() => {
    if (debouncedKeyword === keyword) return;
    updateParams({ keyword: debouncedKeyword, page: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    storyApi
      .list({
        keyword: keyword || undefined,
        genreId: genreId || undefined,
        status: status || undefined,
        sort,
        page,
        size: PAGE_SIZE,
      })
      .then((data) => {
        if (!cancelled) {
          setResult(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [keyword, genreId, status, sort, page]);

  function updateParams(changes) {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === "" || value === null || value === undefined) {
        next.delete(key);
      } else {
        next.set(key, String(value));
      }
    });
    setSearchParams(next);
  }

  function changePage(nextPage) {
    updateParams({ page: nextPage });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  return (
    <div className="stack" style={{ gap: "1.75rem" }}>
      <div className="nb-section-title">
        <h1>Danh sách truyện</h1>
      </div>

      <div className="nb-card filter-bar">
        <Field label="Tìm kiếm" htmlFor="keyword">
          <TextInput
            id="keyword"
            type="search"
            placeholder="Tên truyện hoặc tác giả…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </Field>

        <Field label="Thể loại" htmlFor="genre">
          <Select
            id="genre"
            value={genreId}
            onChange={(event) => updateParams({ genreId: event.target.value, page: 0 })}
          >
            <option value="">Tất cả thể loại</option>
            {genres.map((genre) => (
              <option key={genre.id} value={genre.id}>
                {genre.name}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Trạng thái" htmlFor="status">
          <Select
            id="status"
            value={status}
            onChange={(event) => updateParams({ status: event.target.value, page: 0 })}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Sắp xếp" htmlFor="sort">
          <Select
            id="sort"
            value={sort}
            onChange={(event) => updateParams({ sort: event.target.value, page: 0 })}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {loading && <Spinner />}

      {!loading && result && result.content.length === 0 && (
        <EmptyState icon="🔍" title="Không tìm thấy truyện nào">
          Thử đổi từ khóa hoặc bỏ bớt bộ lọc.
        </EmptyState>
      )}

      {!loading && result && result.content.length > 0 && (
        <>
          <p className="muted">Tìm thấy {result.totalElements} truyện.</p>
          <div className="story-grid">
            {result.content.map((story) => (
              <StoryCard key={story.id} story={story} />
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={changePage} />
        </>
      )}
    </div>
  );
}
