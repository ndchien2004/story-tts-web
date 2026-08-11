import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import Pagination from "../../components/Pagination";
import { Alert, Badge, Button, EmptyState, Select, Spinner } from "../../components/ui";

const PAGE_SIZE = 20;

/** Mirrors MAX_BATCH on the server; selecting more is refused there anyway. */
const MAX_BATCH = 20;

const FILTERS = [
  { value: "missing", label: "Chưa có audio" },
  { value: "has", label: "Đã có audio" },
  { value: "all", label: "Tất cả" },
];

const ACCESS_TONE = { PUBLIC: "public", MEMBER: "neutral", VIP: "vip" };

/**
 * Where narration is missing, and the one button that fills it in.
 *
 * The gap used to be invisible: audio lives per chapter, so finding what had no
 * narration meant opening every story and reading down the list. Here it is a
 * filter, and the default one — an admin arriving at this screen is almost
 * always arriving because something is missing.
 *
 * Generation runs through the same queue as the reader's "Nghe bằng AI" button.
 * The batch is capped because each chapter is a call to a provider with a rate
 * limit; the cap is the server's, repeated here only so the UI can say so
 * before the click rather than after it.
 */
export default function AdminAudioPage() {
  const [searchParams] = useSearchParams();

  // The dashboard links here with ?thieu=1 when it has counted missing audio.
  const [filter, setFilter] = useState(searchParams.get("thieu") ? "missing" : "missing");
  const [storyId, setStoryId] = useState("");
  const [page, setPage] = useState(0);

  const [stories, setStories] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [selected, setSelected] = useState([]);
  const [running, setRunning] = useState(false);
  const [batchResults, setBatchResults] = useState(null);
  const [voices, setVoices] = useState([]);
  const [voice, setVoice] = useState("");

  useEffect(() => {
    storyApi
      .list({ size: 100, sort: "title" })
      .then((data) => setStories(data.content ?? []))
      .catch(() => setStories([]));

    // The voice list comes from whichever provider is configured, so it cannot
    // be hard-coded. No provider means no list, and the selector stays hidden.
    adminApi
      .ttsVoices()
      .then((data) => setVoices(data ?? []))
      .catch(() => setVoices([]));
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .listChapterAudio({
        storyId: storyId || undefined,
        withAudio: filter === "all" ? undefined : filter === "has",
        page,
        size: PAGE_SIZE,
      })
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [storyId, filter, page]);

  useEffect(load, [load]);

  // Any change of filter invalidates both the page number and the selection:
  // acting on rows that scrolled out of view is never what was meant.
  useEffect(() => {
    setPage(0);
    setSelected([]);
  }, [storyId, filter]);

  const chapters = result?.content ?? [];
  const selectable = chapters.filter((chapter) => !chapter.processing);
  const allSelected = selectable.length > 0 && selected.length === selectable.length;

  function toggle(chapterId) {
    setSelected((current) =>
      current.includes(chapterId)
        ? current.filter((id) => id !== chapterId)
        : [...current, chapterId],
    );
  }

  function toggleAll() {
    setSelected(allSelected ? [] : selectable.slice(0, MAX_BATCH).map((c) => c.chapterId));
  }

  async function runBatch() {
    setRunning(true);
    setBatchResults(null);
    setError(null);
    try {
      const results = await adminApi.batchTts({
        chapterIds: selected,
        voice: voice || undefined,
      });
      setBatchResults(results);
      setSelected([]);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  }

  const queued = batchResults?.filter((entry) => entry.queued).length ?? 0;

  return (
    <AdminPage title="Audio & giọng đọc AI">
      {error && <Alert tone="error">{error}</Alert>}

      {batchResults && (
        <Alert tone={queued === batchResults.length ? "success" : "warning"}>
          <strong>
            Đã xếp hàng {queued}/{batchResults.length} chương.
          </strong>
          <ul className="admin-batch-report">
            {batchResults.map((entry) => (
              <li key={entry.chapterId}>
                {entry.queued ? "✓" : "✕"} {entry.chapterTitle} — {entry.message}
              </li>
            ))}
          </ul>
          Audio được tạo ở luồng nền, tải lại trang sau ít phút để thấy kết quả.
        </Alert>
      )}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Chương</span>
          {result && (
            <span className="admin-stat">
              <b>{result.totalElements}</b>
              <span>chương</span>
            </span>
          )}

          <Select
            aria-label="Lọc theo tình trạng audio"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          >
            {FILTERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>

          <Select
            aria-label="Lọc theo truyện"
            value={storyId}
            onChange={(event) => setStoryId(event.target.value)}
          >
            <option value="">Tất cả truyện</option>
            {stories.map((story) => (
              <option key={story.id} value={story.id}>
                {story.title}
              </option>
            ))}
          </Select>
        </div>

        {/* The action bar only exists once something is selected — an empty
            toolbar sitting above every list is noise. */}
        {selected.length > 0 && (
          <div className="admin-bulkbar">
            <strong>Đã chọn {selected.length} chương</strong>

            {voices.length > 0 && (
              <Select
                aria-label="Giọng đọc"
                value={voice}
                onChange={(event) => setVoice(event.target.value)}
              >
                <option value="">Giọng mặc định của máy chủ</option>
                {voices.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.name}
                    {option.gender ? ` — ${option.gender}` : ""}
                    {option.region ? `, ${option.region}` : ""}
                  </option>
                ))}
              </Select>
            )}

            <Button variant="primary" loading={running} onClick={runBatch}>
              Tạo audio bằng AI
            </Button>
            <Button variant="ghost" disabled={running} onClick={() => setSelected([])}>
              Bỏ chọn
            </Button>

            <span className="muted">Tối đa {MAX_BATCH} chương mỗi lượt.</span>
          </div>
        )}

        <div className="admin-panel-body scroll-area">
          {loading && <Spinner />}

          {!loading && chapters.length === 0 && (
            <EmptyState
              title={filter === "missing" ? "Mọi chương đều đã có audio" : "Không có chương nào"}
            >
              {filter === "missing"
                ? "Không còn chương nào thiếu giọng đọc."
                : "Thử đổi bộ lọc phía trên."}
            </EmptyState>
          )}

          {!loading && chapters.length > 0 && (
            <table className="nb-table">
              <thead>
                <tr>
                  <th className="admin-cell-check">
                    <input
                      type="checkbox"
                      aria-label="Chọn tất cả chương trong trang"
                      checked={allSelected}
                      onChange={toggleAll}
                    />
                  </th>
                  <th>Truyện</th>
                  <th>Chương</th>
                  <th>Mức khóa</th>
                  <th>Độ dài</th>
                  <th>Tình trạng</th>
                </tr>
              </thead>
              <tbody>
                {chapters.map((chapter) => (
                  <tr
                    key={chapter.chapterId}
                    className={selected.includes(chapter.chapterId) ? "admin-row-editing" : ""}
                  >
                    <td className="admin-cell-check">
                      <input
                        type="checkbox"
                        aria-label={`Chọn ${chapter.title}`}
                        disabled={chapter.processing}
                        checked={selected.includes(chapter.chapterId)}
                        onChange={() => toggle(chapter.chapterId)}
                      />
                    </td>

                    <td className="muted">{chapter.storyTitle}</td>

                    <td className="admin-cell-main">
                      <div className="admin-title-cell">
                        <strong>
                          {chapter.chapterNumber}. {chapter.title}
                        </strong>
                      </div>
                    </td>

                    <td>
                      <Badge tone={ACCESS_TONE[chapter.accessLevel] ?? "neutral"}>
                        {chapter.accessLevel}
                      </Badge>
                    </td>

                    <td className="tabular-num muted">
                      {chapter.characters.toLocaleString("vi-VN")} ký tự
                    </td>

                    <td>
                      {chapter.processing ? (
                        <Badge tone="info">Đang tạo…</Badge>
                      ) : chapter.hasAudio ? (
                        <Badge tone="public">Đã có audio</Badge>
                      ) : (
                        <Badge tone="neutral">Chưa có</Badge>
                      )}
                      {chapter.failed && !chapter.hasAudio && (
                        <Badge tone="danger">Lần trước hỏng</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {result && result.totalPages > 1 && (
          <div className="admin-panel-foot">
            <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
          </div>
        )}
      </section>
    </AdminPage>
  );
}
