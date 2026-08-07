import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import Pagination from "../../components/Pagination";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { Alert, Badge, Button, ButtonLink, EmptyState, Field, Spinner, TextInput } from "../../components/ui";

const PAGE_SIZE = 10;

export default function AdminStoriesPage() {
  const [keywordInput, setKeywordInput] = useState("");
  const keyword = useDebouncedValue(keywordInput);

  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    storyApi
      .list({ keyword: keyword || undefined, sort: "newest", page, size: PAGE_SIZE })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [keyword, page]);

  useEffect(load, [load]);

  // Reset to the first page whenever the search term changes.
  useEffect(() => setPage(0), [keyword]);

  async function handleDelete(story) {
    const confirmed = window.confirm(
      `Xóa truyện "${story.title}"?\n\nToàn bộ chương và file audio của truyện này cũng sẽ bị xóa.`,
    );
    if (!confirmed) return;

    try {
      await adminApi.deleteStory(story.id);
      setNotice(`Đã xóa truyện "${story.title}".`);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="stack" style={{ gap: "1.5rem" }}>
      <div className="row-between">
        <div className="nb-section-title" style={{ marginBottom: 0 }}>
          <h1>Quản lý truyện</h1>
        </div>
        <ButtonLink to="/admin/truyen/moi" variant="primary">
          + Thêm truyện
        </ButtonLink>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="nb-card">
        <Field label="Tìm truyện" htmlFor="admin-search">
          <TextInput
            id="admin-search"
            type="search"
            placeholder="Tên truyện hoặc tác giả…"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </Field>
      </div>

      {loading && <Spinner />}

      {!loading && result && result.content.length === 0 && (
        <EmptyState icon="📚" title="Chưa có truyện nào">
          Bấm “Thêm truyện” để tạo truyện đầu tiên.
        </EmptyState>
      )}

      {!loading && result && result.content.length > 0 && (
        <>
          <div className="nb-table-wrap">
            <table className="nb-table">
              <thead>
                <tr>
                  <th>Tên truyện</th>
                  <th>Tác giả</th>
                  <th>Thể loại</th>
                  <th>Trạng thái</th>
                  <th>Chương</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((story) => (
                  <tr key={story.id}>
                    <td>
                      <Link to={`/truyen/${story.id}`} style={{ fontWeight: 700 }}>
                        {story.title}
                      </Link>
                    </td>
                    <td>{story.author?.name ?? "—"}</td>
                    <td>{story.genre?.name ?? "—"}</td>
                    <td>
                      <Badge tone={story.status === "COMPLETED" ? "public" : "neutral"}>
                        {story.statusLabel}
                      </Badge>
                    </td>
                    <td>{story.chapterCount}</td>
                    <td>
                      <div className="row" style={{ gap: "0.35rem", flexWrap: "nowrap" }}>
                        <ButtonLink to={`/admin/truyen/${story.id}/chuong`} size="sm" variant="info">
                          Chương
                        </ButtonLink>
                        <ButtonLink to={`/admin/truyen/${story.id}`} size="sm">
                          Sửa
                        </ButtonLink>
                        <Button size="sm" variant="danger" onClick={() => handleDelete(story)}>
                          Xóa
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
