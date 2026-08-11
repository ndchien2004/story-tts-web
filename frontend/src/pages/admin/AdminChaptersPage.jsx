import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import { pollUntilSettled } from "../../utils/poll";
import AdminPage from "./AdminPage";
import AudioPreview from "../../components/admin/AudioPreview";
import AudioUploadButton from "../../components/AudioUploadButton";
import ConfirmDialog from "../../components/ConfirmDialog";
import {
  Alert,
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  Select,
  Spinner,
  TextInput,
} from "../../components/ui";

const ACCESS_LEVELS = [
  { value: "PUBLIC", label: "Công khai" },
  { value: "MEMBER", label: "Yêu cầu đăng nhập" },
  { value: "VIP", label: "Yêu cầu VIP" },
];

const AUDIO_FILTERS = [
  { value: "", label: "Mọi tình trạng audio" },
  { value: "missing", label: "Chưa có audio" },
  { value: "has", label: "Đã có audio" },
];

/** Mirrors MAX_BATCH on the server; selecting more is refused there anyway. */
const MAX_BATCH = 20;

/** The server clamps a page to this many rows, so asking for more buys nothing. */
const AUDIO_STATUS_PAGE_SIZE = 100;

/**
 * Audio status for every chapter of a story, however long the story is.
 *
 * Paged rather than fetched in one go because the endpoint refuses to return
 * more than a hundred rows at a time — asking for the lot and trusting the
 * answer would quietly mark chapter 101 onwards as having no audio.
 */
async function fetchAudioStatus(storyId) {
  const rows = [];
  let page = 0;
  let totalPages = 1;

  do {
    const data = await adminApi.listChapterAudio({
      storyId,
      page,
      size: AUDIO_STATUS_PAGE_SIZE,
    });
    rows.push(...(data.content ?? []));
    totalPages = data.totalPages ?? 1;
    page += 1;
  } while (page < totalPages);

  return rows;
}

/**
 * Everything that happens to one story's chapters, audio included.
 *
 * Audio used to have a console tab of its own, listing every chapter of every
 * story. That view answered "what is missing" but detached the answer from the
 * chapter it belonged to: fixing something meant finding the same chapter again
 * somewhere else. It lives here now, beside the chapter it describes, and the
 * "what is missing" question survives as a filter.
 *
 * Two requests feed the table. The story detail carries the chapters; the audio
 * status of each one comes separately, because whether a track is merely queued
 * or has already failed is not something the reader's view of a story knows or
 * should carry.
 */
export default function AdminChaptersPage() {
  const { storyId } = useParams();

  const [story, setStory] = useState(null);
  const [chapters, setChapters] = useState([]);
  const [audioStatus, setAudioStatus] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [savingId, setSavingId] = useState(null);

  const [filter, setFilter] = useState("");
  const [audioFilter, setAudioFilter] = useState("");

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Bulk selection. Kept as ids rather than as a flag on each row so the filter
  // above can change under it without the ticks going missing.
  const [selected, setSelected] = useState([]);
  const [bulkLevel, setBulkLevel] = useState("PUBLIC");
  const [bulkSaving, setBulkSaving] = useState(false);

  const [voices, setVoices] = useState([]);
  const [voice, setVoice] = useState("");
  const [generating, setGenerating] = useState(false);
  const [batchResults, setBatchResults] = useState(null);

  /** Which chapter is showing its preview player; only one at a time. */
  const [previewing, setPreviewing] = useState(null);

  // Polling outlives a render but must not outlive the screen.
  const gone = useRef(false);
  useEffect(() => {
    gone.current = false;
    return () => {
      gone.current = true;
    };
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([
      storyApi.detail(storyId),
      // Losing the status only costs the badges; the chapter list itself is
      // still worth showing, so this failure is swallowed rather than raised.
      fetchAudioStatus(storyId).catch(() => []),
    ])
      .then(([detail, audioRows]) => {
        setStory(detail.story);
        setChapters(detail.chapters);
        setAudioStatus(Object.fromEntries(audioRows.map((row) => [row.chapterId, row])));
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [storyId]);

  useEffect(load, [load]);

  // The voice list comes from whichever provider is configured, so it cannot be
  // hard-coded. No provider means no list, and the selector stays hidden.
  useEffect(() => {
    adminApi
      .ttsVoices()
      .then((data) => setVoices(data ?? []))
      .catch(() => setVoices([]));
  }, []);

  /** Inline access-level change; updates just the affected row on success. */
  async function handleAccessLevelChange(chapter, accessLevel) {
    setSavingId(chapter.id);
    setError(null);

    try {
      const updated = await adminApi.setChapterAccessLevel(chapter.id, accessLevel);
      setChapters((current) =>
        current.map((row) => (row.id === chapter.id ? { ...row, ...updated } : row)),
      );
      setNotice(`Đã đổi mức khóa của “${chapter.title}”.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  function toggleSelected(chapterId) {
    setSelected((current) =>
      current.includes(chapterId)
        ? current.filter((id) => id !== chapterId)
        : [...current, chapterId],
    );
  }

  /**
   * Sets one access level across every ticked chapter in a single request.
   *
   * One request rather than a loop of them: the server does the whole list in
   * one transaction, so a story can never be left half-locked in a way nobody
   * could tell apart from a deliberate arrangement.
   */
  async function applyBulkLevel() {
    setBulkSaving(true);
    setError(null);
    try {
      const { updated } = await adminApi.setChapterAccessLevelBulk(selected, bulkLevel);
      const label = ACCESS_LEVELS.find((level) => level.value === bulkLevel)?.label ?? bulkLevel;
      setNotice(`Đã đổi mức khóa của ${updated} chương thành “${label}”.`);
      setSelected([]);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBulkSaving(false);
    }
  }

  /**
   * Queues narration for the ticked chapters, then waits for it.
   *
   * The server answers as soon as the jobs are queued, so stopping there would
   * leave the button still and the rows saying "Đang tạo…" with nothing to say
   * when that changed. Polling keeps the badges moving and turns them into
   * "Đã có" — with a play button — the moment each chapter lands.
   */
  async function generateAudio() {
    const queuedIds = selected;

    setGenerating(true);
    setBatchResults(null);
    setError(null);

    try {
      const results = await adminApi.batchTts({
        chapterIds: queuedIds,
        voice: voice || undefined,
      });
      setBatchResults(results);
      setSelected([]);

      const accepted = results.filter((entry) => entry.queued).map((entry) => entry.chapterId);
      if (accepted.length === 0) return;

      const outcome = await pollUntilSettled(
        async () => {
          const rows = await fetchAudioStatus(storyId);
          if (gone.current) return true;
          setAudioStatus(Object.fromEntries(rows.map((row) => [row.chapterId, row])));
          return accepted.every((id) => !rows.find((row) => row.chapterId === id)?.processing);
        },
        { isCancelled: () => gone.current },
      );

      if (gone.current) return;

      if (outcome === "timeout") {
        setError("Quá thời gian chờ tạo audio. Việc tạo vẫn chạy ở máy chủ, hãy tải lại trang sau.");
      } else {
        setNotice(`Đã tạo xong audio cho ${accepted.length} chương. Bấm “Nghe thử” để kiểm tra.`);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      if (!gone.current) setGenerating(false);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await adminApi.deleteChapter(pendingDelete.id);
      setNotice(`Đã xóa chương “${pendingDelete.title}”.`);
      setPendingDelete(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  // Filtering happens client-side: the whole chapter list already arrived with
  // the story, so a round trip per keystroke would buy nothing.
  const visibleChapters = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return chapters.filter((chapter) => {
      if (
        needle &&
        !chapter.title.toLowerCase().includes(needle) &&
        !String(chapter.chapterNumber).includes(needle)
      ) {
        return false;
      }
      if (!audioFilter) return true;
      const hasAudio = audioStatus[chapter.id]?.hasAudio ?? chapter.hasAudio;
      return audioFilter === "has" ? hasAudio : !hasAudio;
    });
  }, [chapters, filter, audioFilter, audioStatus]);

  // "Select all" means all of what is on screen, not all of what exists — with
  // a filter applied, the other reading would tick rows nobody can see.
  const allVisibleSelected =
    visibleChapters.length > 0 &&
    visibleChapters.every((chapter) => selected.includes(chapter.id));

  function toggleAllVisible() {
    setSelected(allVisibleSelected ? [] : visibleChapters.map((chapter) => chapter.id));
  }

  const audioCount = chapters.filter(
    (chapter) => audioStatus[chapter.id]?.hasAudio ?? chapter.hasAudio,
  ).length;

  const queued = batchResults?.filter((entry) => entry.queued).length ?? 0;

  if (loading) {
    return (
      <AdminPage crumbs={[{ to: "/admin/truyen", label: "Truyện" }]} title="Đang tải…">
        <Spinner />
      </AdminPage>
    );
  }

  return (
    <AdminPage
      crumbs={[{ to: "/admin/truyen", label: "Truyện" }, { label: "Chương" }]}
      title={story?.title ?? "Quản lý chương"}
      actions={
        <ButtonLink to={`/admin/truyen/${storyId}/chuong/moi`} variant="primary">
          + Thêm chương
        </ButtonLink>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

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

      <div className="admin-split">
        {/* Context column: what is being edited, and the actions that apply to
            the story as a whole rather than to one chapter. */}
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Thông tin truyện</span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad scroll-area">
            <div className="stack" style={{ gap: "var(--space-4)" }}>
              {story?.coverImage ? (
                <img className="admin-cover-preview" src={story.coverImage} alt="" />
              ) : (
                <div
                  className="admin-cover-preview"
                  style={{ display: "grid", placeItems: "center", background: "var(--bg-alt)" }}
                >
                  <span style={{ fontSize: "2.4rem", fontWeight: 600, color: "var(--ink-muted)" }}>
                    {story?.title?.slice(0, 1).toUpperCase()}
                  </span>
                </div>
              )}

              <dl className="admin-meta-list">
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Tác giả</dt>
                  <dd className="admin-meta-value">{story?.author?.name ?? "—"}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Thể loại</dt>
                  <dd className="admin-meta-value">{story?.genre?.name ?? "—"}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Trạng thái</dt>
                  <dd className="admin-meta-value">
                    <Badge tone={story?.status === "COMPLETED" ? "public" : "neutral"}>
                      {story?.statusLabel}
                    </Badge>
                  </dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Số chương</dt>
                  <dd className="admin-meta-value">{chapters.length}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Đã có audio</dt>
                  <dd className="admin-meta-value">
                    {audioCount}/{chapters.length}
                  </dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Lượt xem</dt>
                  <dd className="admin-meta-value">
                    {(story?.viewCount ?? 0).toLocaleString("vi-VN")}
                  </dd>
                </div>
              </dl>

              <div className="stack" style={{ gap: "var(--space-2)" }}>
                <ButtonLink to={`/admin/truyen/${storyId}`} block>
                  Sửa thông tin truyện
                </ButtonLink>
              </div>
            </div>
          </div>
        </aside>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Danh sách chương</span>

            <Select
              aria-label="Lọc theo tình trạng audio"
              value={audioFilter}
              onChange={(event) => setAudioFilter(event.target.value)}
            >
              {AUDIO_FILTERS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>

            <div className="admin-search">
              <TextInput
                type="search"
                aria-label="Lọc chương"
                placeholder="Lọc theo tiêu đề hoặc số chương…"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
              />
            </div>
          </div>

          {/* Only appears once rows are ticked; an empty toolbar above every
              list is noise. */}
          {selected.length > 0 && (
            <div className="admin-bulkbar">
              <strong>Đã chọn {selected.length} chương</strong>

              <Select
                aria-label="Đặt mức khóa cho các chương đã chọn"
                value={bulkLevel}
                onChange={(event) => setBulkLevel(event.target.value)}
              >
                {ACCESS_LEVELS.map((level) => (
                  <option key={level.value} value={level.value}>
                    {level.label}
                  </option>
                ))}
              </Select>

              <Button variant="primary" loading={bulkSaving} onClick={applyBulkLevel}>
                Đổi mức khóa
              </Button>

              {voices.length > 0 && (
                <Select
                  aria-label="Giọng đọc dùng cho lô này"
                  value={voice}
                  onChange={(event) => setVoice(event.target.value)}
                >
                  <option value="">Giọng mặc định của máy chủ</option>
                  {voices.map((option) => (
                    <option key={option.code} value={option.code}>
                      {option.name}
                      {option.gender ? ` — ${option.gender}` : ""}
                    </option>
                  ))}
                </Select>
              )}

              <Button
                variant="violet"
                loading={generating}
                disabled={selected.length > MAX_BATCH}
                title={
                  selected.length > MAX_BATCH
                    ? `Mỗi lượt tạo tối đa ${MAX_BATCH} chương`
                    : "Tạo audio cho các chương đã chọn"
                }
                onClick={generateAudio}
              >
                Tạo audio
              </Button>

              <Button variant="ghost" disabled={bulkSaving || generating} onClick={() => setSelected([])}>
                Bỏ chọn
              </Button>

              {selected.length > MAX_BATCH && (
                <span className="muted">Tạo audio tối đa {MAX_BATCH} chương mỗi lượt.</span>
              )}
            </div>
          )}

          <div className="admin-panel-body scroll-area">
            {chapters.length === 0 && (
              <EmptyState title="Truyện chưa có chương nào">
                Bấm “Thêm chương” để đăng chương đầu tiên.
              </EmptyState>
            )}

            {chapters.length > 0 && visibleChapters.length === 0 && (
              <EmptyState title="Không có chương nào khớp bộ lọc" />
            )}

            {visibleChapters.length > 0 && (
              <table className="nb-table">
                <thead>
                  <tr>
                    <th className="admin-cell-check">
                      <input
                        type="checkbox"
                        aria-label="Chọn tất cả chương đang hiện"
                        checked={allVisibleSelected}
                        onChange={toggleAllVisible}
                      />
                    </th>
                    <th>#</th>
                    <th>Tiêu đề</th>
                    <th>Mức khóa</th>
                    <th>Audio</th>
                    <th className="admin-cell-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleChapters.flatMap((chapter) => {
                    const audio = audioStatus[chapter.id];
                    const hasAudio = audio?.hasAudio ?? chapter.hasAudio;
                    const rows = [];

                    rows.push(
                      <tr
                        key={chapter.id}
                        className={selected.includes(chapter.id) ? "admin-row-editing" : ""}
                      >
                        <td className="admin-cell-check">
                          <input
                            type="checkbox"
                            aria-label={`Chọn chương ${chapter.chapterNumber}`}
                            checked={selected.includes(chapter.id)}
                            onChange={() => toggleSelected(chapter.id)}
                          />
                        </td>
                        <td>{chapter.chapterNumber}</td>
                        <td className="admin-cell-main">{chapter.title}</td>
                        <td>
                          {/* The select both states the current level and
                              changes it, so no badge repeats it beside. */}
                          <Select
                            aria-label={`Mức khóa của chương ${chapter.chapterNumber}`}
                            style={{ width: "auto", padding: "0.25rem 0.4rem" }}
                            value={chapter.accessLevel}
                            disabled={savingId === chapter.id}
                            onChange={(event) =>
                              handleAccessLevelChange(chapter, event.target.value)
                            }
                          >
                            {ACCESS_LEVELS.map((level) => (
                              <option key={level.value} value={level.value}>
                                {level.label}
                              </option>
                            ))}
                          </Select>
                        </td>
                        <td>
                          {audio?.processing ? (
                            <span className="admin-audio-working">
                              <span className="spinner" aria-hidden="true" />
                              <Badge tone="info">Đang tạo…</Badge>
                            </span>
                          ) : hasAudio ? (
                            <Badge tone="public">Đã có</Badge>
                          ) : (
                            <Badge tone="neutral">Chưa có</Badge>
                          )}
                          {audio?.failed && !hasAudio && <Badge tone="danger">Lần trước hỏng</Badge>}
                        </td>
                        <td className="admin-cell-actions">
                          <div className="admin-row-actions">
                            {audio?.audioId && (
                              <Button
                                size="sm"
                                variant="info"
                                aria-expanded={previewing === chapter.id}
                                onClick={() =>
                                  setPreviewing((current) =>
                                    current === chapter.id ? null : chapter.id,
                                  )
                                }
                              >
                                {previewing === chapter.id ? "Đóng" : "Nghe thử"}
                              </Button>
                            )}
                            <AudioUploadButton
                              chapterId={chapter.id}
                              hasAudio={hasAudio}
                              onUploaded={() => {
                                setNotice(`Đã tải lên audio cho “${chapter.title}”.`);
                                load();
                              }}
                              onError={setError}
                            />
                            <ButtonLink to={`/admin/chuong/${chapter.id}`} size="sm">
                              Sửa
                            </ButtonLink>
                            <Button
                              size="sm"
                              variant="danger"
                              onClick={() => setPendingDelete(chapter)}
                            >
                              Xóa
                            </Button>
                          </div>
                        </td>
                      </tr>,
                    );

                    // The player gets a row of its own rather than a cell: it is
                    // wider than any column here, and squeezing it into one would
                    // set the whole table's column widths by it.
                    if (previewing === chapter.id && audio?.audioId) {
                      rows.push(
                        <tr key={`${chapter.id}-preview`} className="admin-row-preview">
                          <td colSpan={6}>
                            <AudioPreview
                              streamUrl={`/api/chapters/${chapter.id}/audio/${audio.audioId}`}
                            />
                          </td>
                        </tr>,
                      );
                    }

                    return rows;
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              Hiển thị {visibleChapters.length}/{chapters.length} chương · {audioCount} chương có
              audio
            </span>
          </div>
        </section>
      </div>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa chương?"
        message={`Chương “${pendingDelete?.title}” sẽ bị xóa khỏi truyện.`}
        detail="File audio của chương cũng bị xóa theo, không khôi phục được."
        confirmLabel="Xóa chương"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}
