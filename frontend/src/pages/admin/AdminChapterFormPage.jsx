import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import ChapterAudioPanel from "../../components/admin/ChapterAudioPanel";
import { Alert, Button, Field, Select, Spinner, TextArea, TextInput } from "../../components/ui";

const ACCESS_LEVELS = [
  { value: "PUBLIC", label: "Công khai — ai cũng đọc được" },
  { value: "MEMBER", label: "Yêu cầu đăng nhập — chỉ thành viên" },
  { value: "VIP", label: "Yêu cầu VIP — chỉ thành viên VIP" },
];

const EMPTY_FORM = {
  title: "",
  content: "",
  chapterNumber: "",
  accessLevel: "PUBLIC",
  coinPrice: "0",
};

/**
 * Create and edit form for a chapter.
 *
 * Creating uses `:storyId`, editing uses `:chapterId`; exactly one is present.
 *
 * The metadata fields sit in a narrow column and the chapter text takes the
 * whole remaining pane, so the editor grows with the screen instead of being a
 * fixed box in the middle of a scrolling page.
 */
export default function AdminChapterFormPage() {
  const { storyId, chapterId } = useParams();
  const navigate = useNavigate();
  const notify = useAdminToast();
  const isEdit = Boolean(chapterId);

  const [form, setForm] = useState(EMPTY_FORM);

  /**
   * The chapter as the server last confirmed it.
   *
   * Kept so the page can tell whether what is on screen has been saved — which
   * matters to the audio panel below: narration is built from the chapter in
   * the database, not from the text sitting unsaved in this textarea.
   */
  const [savedForm, setSavedForm] = useState(EMPTY_FORM);

  const [parentStoryId, setParentStoryId] = useState(storyId ?? null);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  useEffect(() => {
    if (!isEdit) return;

    adminApi
      .getChapter(chapterId)
      .then((chapter) => {
        const loaded = {
          title: chapter.title,
          content: chapter.content,
          chapterNumber: String(chapter.chapterNumber),
          accessLevel: chapter.accessLevel,
          coinPrice: String(chapter.coinPrice ?? 0),
        };
        setForm(loaded);
        setSavedForm(loaded);
        setParentStoryId(chapter.storyId);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [isEdit, chapterId]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  const backTo = `/admin/truyen/${parentStoryId}/chuong`;

  const dirty = useMemo(
    () => Object.keys(form).some((key) => form[key] !== savedForm[key]),
    [form, savedForm],
  );

  /**
   * Writes the chapter and remembers what was written.
   *
   * Separate from the submit handler because the audio panel needs the same
   * thing without leaving the page — narration of the version still only in
   * this textarea would be narration of nothing the server has ever seen.
   *
   * @throws the API error, so callers can stop instead of carrying on
   */
  const saveChapter = useCallback(async () => {
    const snapshot = form;
    const payload = {
      title: snapshot.title,
      content: snapshot.content,
      // Left blank on create, the server assigns the next free number.
      chapterNumber: snapshot.chapterNumber ? Number(snapshot.chapterNumber) : undefined,
      accessLevel: snapshot.accessLevel,
    };

    if (isEdit) {
      await adminApi.updateChapter(chapterId, payload);

      /*
       * Giá đi bằng một lệnh gọi riêng, và chỉ khi nó thật sự đổi.
       *
       * Không phải để tiết kiệm một request: máy chủ từ chối giá dương trên
       * chương công khai, nên gọi kèm mỗi lần lưu sẽ làm việc sửa tiêu đề một
       * chương công khai hỏng vì một con số 0 không ai chạm vào. Đường ghi giá
       * cũng cố ý tách khỏi ChapterRequest ở phía máy chủ, cùng lý do.
       */
      const price = Number(snapshot.coinPrice) || 0;
      if (price !== (Number(savedForm.coinPrice) || 0)) {
        await adminApi.setChapterPrice(chapterId, price);
      }
    } else {
      await adminApi.createChapter(storyId, payload);
    }
    setSavedForm(snapshot);
  }, [form, savedForm, isEdit, chapterId, storyId]);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      await saveChapter();

      // The toaster sits in the layout above this route, so the message
      // survives the jump back to the chapter list and is read there.
      notify(isEdit ? `Đã lưu “${form.title}”.` : `Đã thêm chương “${form.title}”.`);
      navigate(backTo);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  const title = isEdit ? "Sửa chương" : "Thêm chương mới";

  if (loading) {
    return (
      <AdminPage crumbs={[{ to: "/admin/truyen", label: "Truyện" }]} title={title}>
        <Spinner />
      </AdminPage>
    );
  }

  const characterCount = form.content.length;
  // A rough reading estimate; the synthesised narration lands in the same range.
  const minutes = Math.max(1, Math.round(characterCount / 900));

  return (
    <AdminPage
      crumbs={[
        { to: "/admin/truyen", label: "Truyện" },
        { to: backTo, label: "Chương" },
      ]}
      title={title}
      actions={
        <Button variant="ghost" onClick={() => navigate(backTo)}>
          Hủy
        </Button>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      <form className="admin-split" onSubmit={handleSubmit}>
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Thông tin chương</span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad scroll-area">
            <div className="stack">
              <Field label="Tiêu đề chương" htmlFor="title" error={fieldErrors.title}>
                <TextInput
                  id="title"
                  required
                  placeholder="Chương 1: …"
                  value={form.title}
                  onChange={(event) => updateField("title", event.target.value)}
                />
              </Field>

              <Field
                label="Số thứ tự"
                htmlFor="chapterNumber"
                error={fieldErrors.chapterNumber}
                hint={isEdit ? undefined : "Để trống để hệ thống tự đánh số tiếp theo"}
              >
                <TextInput
                  id="chapterNumber"
                  type="number"
                  min="1"
                  value={form.chapterNumber}
                  onChange={(event) => updateField("chapterNumber", event.target.value)}
                />
              </Field>

              <Field
                label="Mức truy cập"
                htmlFor="accessLevel"
                hint="Quyết định ai được đọc và nghe chương này"
              >
                <Select
                  id="accessLevel"
                  value={form.accessLevel}
                  onChange={(event) => updateField("accessLevel", event.target.value)}
                >
                  {ACCESS_LEVELS.map((level) => (
                    <option key={level.value} value={level.value}>
                      {level.label}
                    </option>
                  ))}
                </Select>
              </Field>

              {/* Giá là một trục riêng, không phải một mức nữa của ô trên: mức
                  truy cập nói ai được nhìn tới chương, giá nói mở nó tốn bao
                  nhiêu. Một chương VIP đặt giá nghĩa là VIP đọc miễn phí còn
                  người thường trả Xu — thứ không diễn đạt được bằng một ô chọn. */}
              {isEdit && (
                <Field
                  label="Giá mở khóa (Xu)"
                  htmlFor="coinPrice"
                  hint={
                    form.accessLevel === "PUBLIC"
                      ? "Chương công khai không đặt giá được — ai cũng đọc được rồi."
                      : "0 = không bán lẻ. Người có VIP luôn đọc miễn phí."
                  }
                >
                  <TextInput
                    id="coinPrice"
                    type="number"
                    min="0"
                    step="10"
                    disabled={form.accessLevel === "PUBLIC"}
                    value={form.coinPrice}
                    onChange={(event) => updateField("coinPrice", event.target.value)}
                  />
                </Field>
              )}

              <div className="row" style={{ gap: "var(--space-2)" }}>
                <span className="admin-stat">
                  <b>{characterCount.toLocaleString("vi-VN")}</b>
                  <span>ký tự</span>
                </span>
                <span className="admin-stat">
                  <b>~{minutes}</b>
                  <span>phút nghe</span>
                </span>
              </div>

              {/* Only once the chapter exists: audio hangs off a chapter id, and
                  a chapter being created has none yet. */}
              {isEdit && (
                <div className="stack" style={{ gap: "var(--space-3)" }}>
                  <div className="nb-section-title" style={{ marginBottom: 0 }}>
                    <h3>Audio của chương</h3>
                  </div>
                  <ChapterAudioPanel
                    chapterId={chapterId}
                    chapterTitle={form.title}
                    dirty={dirty}
                    onSaveFirst={saveChapter}
                  />
                </div>
              )}
            </div>
          </div>
        </aside>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Nội dung chương</span>
            {fieldErrors.content && <span className="nb-error">{fieldErrors.content}</span>}
          </div>

          <div className="admin-panel-body admin-panel-body-pad admin-panel-body-fill">
            <div className="admin-editor">
              <TextArea
                id="content"
                required
                aria-label="Nội dung chương"
                placeholder="Dán hoặc gõ nội dung chương ở đây…"
                value={form.content}
                onChange={(event) => updateField("content", event.target.value)}
              />
            </div>
          </div>

          {/* The save bar is pinned to the panel, so it stays reachable no
              matter how long the chapter is. */}
          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              {isEdit ? "Đang sửa chương đã đăng" : "Chương mới chưa được lưu"}
            </span>
            <div className="row" style={{ gap: "var(--space-2)" }}>
              <Button variant="ghost" onClick={() => navigate(backTo)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                {isEdit ? "Lưu thay đổi" : "Đăng chương"}
              </Button>
            </div>
          </div>
        </section>
      </form>
    </AdminPage>
  );
}
