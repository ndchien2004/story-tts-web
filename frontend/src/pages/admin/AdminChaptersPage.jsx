import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
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

export default function AdminChaptersPage() {
  const { storyId } = useParams();

  const [story, setStory] = useState(null);
  const [chapters, setChapters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [savingId, setSavingId] = useState(null);
  const [filter, setFilter] = useState("");

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Bulk selection. Kept as ids rather than as a flag on each row so the filter
  // above can change under it without the ticks going missing.
  const [selected, setSelected] = useState([]);
  const [bulkLevel, setBulkLevel] = useState("PUBLIC");
  const [bulkSaving, setBulkSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    storyApi
      .detail(storyId)
      .then((data) => {
        setStory(data.story);
        setChapters(data.chapters);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [storyId]);

  useEffect(load, [load]);

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
    if (!needle) return chapters;
    return chapters.filter(
      (chapter) =>
        chapter.title.toLowerCase().includes(needle) ||
        String(chapter.chapterNumber).includes(needle),
    );
  }, [chapters, filter]);

  // "Select all" means all of what is on screen, not all of what exists — with
  // a filter applied, the other reading would tick rows nobody can see.
  const allVisibleSelected =
    visibleChapters.length > 0 &&
    visibleChapters.every((chapter) => selected.includes(chapter.id));

  function toggleAllVisible() {
    setSelected(allVisibleSelected ? [] : visibleChapters.map((chapter) => chapter.id));
  }

  const audioCount = chapters.filter((chapter) => chapter.hasAudio).length;

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
                Áp dụng
              </Button>
              <Button variant="ghost" disabled={bulkSaving} onClick={() => setSelected([])}>
                Bỏ chọn
              </Button>
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
                  {visibleChapters.map((chapter) => (
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
                          onChange={(event) => handleAccessLevelChange(chapter, event.target.value)}
                        >
                          {ACCESS_LEVELS.map((level) => (
                            <option key={level.value} value={level.value}>
                              {level.label}
                            </option>
                          ))}
                        </Select>
                      </td>
                      <td>
                        {chapter.hasAudio ? (
                          <Badge tone="public">Có</Badge>
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                      <td className="admin-cell-actions">
                        <div className="admin-row-actions">
                          <AudioUploadButton
                            chapterId={chapter.id}
                            hasAudio={chapter.hasAudio}
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
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              Hiển thị {visibleChapters.length}/{chapters.length} chương
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
