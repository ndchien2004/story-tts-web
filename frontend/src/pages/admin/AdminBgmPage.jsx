import { useCallback, useEffect, useRef, useState } from "react";
import { adminApi, bgmApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import ConfirmDialog from "../../components/ConfirmDialog";
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  Field,
  Spinner,
  TextInput,
} from "../../components/ui";

/** Mirrors the server's own ceiling, so the refusal happens before the upload. */
const MAX_BYTES = 20 * 1024 * 1024;

function formatSize(bytes) {
  if (!bytes) return "—";
  const mb = bytes / (1024 * 1024);
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
}

/**
 * The shared background-music library.
 *
 * Why this screen exists: the reading page has always been able to play music
 * under the narration, but the only two sources were a manifest baked into the
 * frontend build and a file the listener opened from their own machine. On a
 * running server that meant the "chọn nhạc nền" box was empty for everyone who
 * had not brought their own music — an offer the site could not actually keep.
 * Here an admin uploads a track and every listener has it on their next visit.
 *
 * A track can be switched off instead of deleted, and that is the control to
 * reach for first: switching off takes it out of the listener's list while
 * leaving the file in place, which is what a licence question usually needs.
 * Deleting takes the file with it.
 */
export default function AdminBgmPage() {
  const [tracks, setTracks] = useState(null);
  const [error, setError] = useState(null);
  const notify = useAdminToast();

  // The upload form doubles as the edit form: same two fields, and the file is
  // the only part an edit cannot change.
  const [editing, setEditing] = useState(null);
  const [title, setTitle] = useState("");
  const [credit, setCredit] = useState("");
  const [file, setFile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);
  const fileRef = useRef(null);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    adminApi
      .listBgm()
      .then(setTracks)
      .catch((err) => setError(err.message));
  }, []);

  useEffect(load, [load]);

  function resetForm() {
    setEditing(null);
    setTitle("");
    setCredit("");
    setFile(null);
    setFormError(null);
    if (fileRef.current) fileRef.current.value = "";
  }

  function startEdit(track) {
    setEditing(track);
    setTitle(track.title);
    setCredit(track.credit ?? "");
    setFile(null);
    setFormError(null);
    if (fileRef.current) fileRef.current.value = "";
  }

  function pickFile(chosen) {
    if (!chosen) {
      setFile(null);
      return;
    }
    if (chosen.size > MAX_BYTES) {
      setFormError("File nhạc nền vượt quá 20 MB. Hãy chọn bản ngắn hơn hoặc nén lại.");
      if (fileRef.current) fileRef.current.value = "";
      return;
    }
    setFormError(null);
    setFile(chosen);
    // An empty title takes the file's name, so the field is never a blocker.
    if (!title.trim()) setTitle(chosen.name.replace(/\.[^.]+$/, ""));
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (!editing && !file) {
      setFormError("Vui lòng chọn một file nhạc để tải lên.");
      return;
    }
    if (!title.trim()) {
      setFormError("Tên bản nhạc không được để trống.");
      return;
    }

    setSaving(true);
    setFormError(null);

    try {
      if (editing) {
        await adminApi.updateBgm(editing.id, {
          title: title.trim(),
          credit: credit.trim() || null,
        });
        notify(`Đã cập nhật “${title.trim()}”.`);
      } else {
        await adminApi.uploadBgm({ file, title: title.trim(), credit: credit.trim() });
        notify(`Đã thêm “${title.trim()}” vào kho nhạc nền.`);
      }
      setError(null);
      resetForm();
      load();
    } catch (err) {
      // Stays beside the form: what went wrong is about what was just typed or
      // just picked.
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(track) {
    setError(null);
    try {
      await adminApi.setBgmActive(track.id, !track.active);
      notify(
        track.active
          ? `Đã tạm ẩn “${track.title}” khỏi danh sách người nghe chọn.`
          : `“${track.title}” đã trở lại danh sách người nghe chọn.`,
      );
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await adminApi.deleteBgm(pendingDelete.id);
      notify(`Đã xóa “${pendingDelete.title}”.`);
      if (editing?.id === pendingDelete.id) resetForm();
      setPendingDelete(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const activeCount = tracks?.filter((track) => track.active).length ?? 0;

  return (
    <AdminPage
      title="Nhạc nền"
    >
      {error && <Alert tone="error">{error}</Alert>}

      <div className="admin-split">
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">
              {editing ? "Sửa bản nhạc" : "Thêm bản nhạc"}
            </span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad scroll-area">
            <form className="stack" style={{ gap: "var(--space-4)" }} onSubmit={handleSubmit}>
              {/* Hidden while editing: the file is what a track *is*, so
                  replacing it is uploading a new one, not editing this one. */}
              {!editing && (
                <Field
                  label="File nhạc"
                  htmlFor="bgm-file"
                  hint="MP3, WAV, OGG hoặc M4A, tối đa 20 MB. Bản vài phút lặp lại được là vừa."
                >
                  <input
                    id="bgm-file"
                    ref={fileRef}
                    className="nb-input"
                    type="file"
                    accept="audio/*"
                    onChange={(event) => pickFile(event.target.files?.[0] ?? null)}
                  />
                </Field>
              )}

              <Field label="Tên bản nhạc" htmlFor="bgm-title">
                <TextInput
                  id="bgm-title"
                  value={title}
                  placeholder="Mưa đêm, Đàn tranh chậm…"
                  maxLength={150}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </Field>

              <Field
                label="Ghi công"
                htmlFor="bgm-credit"
                hint="Hiện dưới ô chọn nhạc trên trang đọc. Để trống nếu bản nhạc không đòi hỏi."
              >
                <TextInput
                  id="bgm-credit"
                  value={credit}
                  placeholder="Nhạc: … — giấy phép …"
                  maxLength={255}
                  onChange={(event) => setCredit(event.target.value)}
                />
              </Field>

              {formError && <Alert tone="error">{formError}</Alert>}

              <div className="row">
                <Button type="submit" variant="primary" loading={saving}>
                  {editing ? "Lưu thay đổi" : "+ Tải lên"}
                </Button>
                {editing && (
                  <Button variant="ghost" disabled={saving} onClick={resetForm}>
                    Hủy
                  </Button>
                )}
              </div>
            </form>
          </div>
        </aside>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Kho nhạc nền</span>
            {tracks && (
              <span className="admin-stat">
                <b>{activeCount}</b>
                <span>đang mở / {tracks.length} bản</span>
              </span>
            )}
          </div>

          <div className="admin-panel-body scroll-area">
            {!tracks && <Spinner />}

            {tracks && tracks.length === 0 && (
              <EmptyState title="Kho nhạc nền đang trống">
                Người nghe hiện chỉ có thể mở nhạc từ máy của họ. Tải lên vài bản ở biểu mẫu bên
                cạnh để họ có sẵn thứ để chọn.
              </EmptyState>
            )}

            {tracks && tracks.length > 0 && (
              <table className="nb-table">
                <thead>
                  <tr>
                    <th className="admin-cell-main">Bản nhạc</th>
                    <th className="admin-cell-date">Dung lượng</th>
                    <th className="admin-cell-date">Trạng thái</th>
                    <th className="admin-cell-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {tracks.map((track) => (
                    <tr key={track.id} className={editing?.id === track.id ? "admin-row-editing" : ""}>
                      <td className="admin-cell-main">
                        <div className="admin-title-cell">
                          <div className="admin-user-name">
                            <strong>{track.title}</strong>
                            {track.credit && <span className="muted">{track.credit}</span>}
                          </div>
                        </div>
                        {/* The browser's own player: the only question here is
                            "did the right file go up". */}
                        <audio
                          className="admin-audio-preview"
                          controls
                          preload="none"
                          src={bgmApi.streamUrl(track.streamUrl)}
                        >
                          Trình duyệt của bạn không phát được audio.
                        </audio>
                      </td>

                      <td className="admin-cell-date muted">{formatSize(track.fileSize)}</td>

                      <td className="admin-cell-date">
                        <Badge tone={track.active ? "public" : "neutral"}>
                          {track.active ? "Đang mở" : "Đã ẩn"}
                        </Badge>
                      </td>

                      <td className="admin-cell-actions">
                        <div className="admin-row-actions">
                          <Button size="sm" onClick={() => startEdit(track)}>
                            Sửa
                          </Button>
                          <Button
                            size="sm"
                            title={
                              track.active
                                ? "Gỡ khỏi danh sách người nghe chọn, giữ lại file"
                                : "Đưa trở lại danh sách người nghe chọn"
                            }
                            onClick={() => toggleActive(track)}
                          >
                            {track.active ? "Ẩn" : "Mở lại"}
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            title="Xóa hẳn, file cũng bị xóa"
                            onClick={() => setPendingDelete(track)}
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
        </section>
      </div>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa bản nhạc nền?"
        message={`“${pendingDelete?.title}” sẽ bị xóa khỏi kho, và file trên máy chủ cũng bị xóa theo.`}
        detail="Nếu chỉ muốn tạm gỡ khỏi danh sách người nghe chọn, hãy dùng nút “Ẩn” — bản nhạc giữ nguyên và mở lại được bất cứ lúc nào."
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}
