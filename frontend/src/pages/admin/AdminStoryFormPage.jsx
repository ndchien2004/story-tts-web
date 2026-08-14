import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { adminApi, catalogApi, storyApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import { Alert, Button, Field, Select, Spinner, TextArea, TextInput } from "../../components/ui";

const EMPTY_FORM = {
  title: "",
  authorId: "",
  authorName: "",
  genreId: "",
  genreName: "",
  coverImage: "",
  description: "",
  status: "ONGOING",
};

/** Create and edit form for a story. Editing is keyed off the `:storyId` param. */
export default function AdminStoryFormPage() {
  const { storyId } = useParams();
  const navigate = useNavigate();
  const notify = useAdminToast();
  const isEdit = Boolean(storyId);

  const [form, setForm] = useState(EMPTY_FORM);
  const [genres, setGenres] = useState([]);
  const [authors, setAuthors] = useState([]);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  useEffect(() => {
    Promise.all([catalogApi.genres(), catalogApi.authors()])
      .then(([genreList, authorList]) => {
        setGenres(genreList);
        setAuthors(authorList);
      })
      .catch(() => {
        setGenres([]);
        setAuthors([]);
      });
  }, []);

  useEffect(() => {
    if (!isEdit) return;

    storyApi
      .detail(storyId)
      .then(({ story }) => {
        setForm({
          title: story.title,
          authorId: story.author?.id ?? "",
          authorName: "",
          genreId: story.genre?.id ?? "",
          genreName: "",
          coverImage: story.coverImage ?? "",
          description: story.description ?? "",
          status: story.status,
        });
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [isEdit, storyId]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    // Blank ids are sent as undefined so the server falls back to the
    // free-text name and creates the author or genre on demand.
    const payload = {
      title: form.title,
      authorId: form.authorId || undefined,
      authorName: form.authorId ? undefined : form.authorName || undefined,
      genreId: form.genreId || undefined,
      genreName: form.genreId ? undefined : form.genreName || undefined,
      coverImage: form.coverImage || undefined,
      description: form.description || undefined,
      status: form.status,
    };

    try {
      const saved = isEdit
        ? await adminApi.updateStory(storyId, payload)
        : await adminApi.createStory(payload);

      // Said after the navigation is queued, not before: the toaster lives in
      // the layout above these routes, so the message outlives the page that
      // sent it and lands on the chapter list the save arrives at. Until there
      // was somewhere for it to live, saving a story said nothing at all.
      notify(isEdit ? `Đã lưu “${saved.title}”.` : `Đã thêm truyện “${saved.title}”.`);
      navigate(`/admin/truyen/${saved.id}/chuong`);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  const title = isEdit ? "Sửa truyện" : "Thêm truyện mới";

  if (loading) {
    return (
      <AdminPage crumbs={[{ to: "/admin/truyen", label: "Truyện" }]} title={title}>
        <Spinner />
      </AdminPage>
    );
  }

  return (
    <AdminPage
      crumbs={[{ to: "/admin/truyen", label: "Truyện" }]}
      title={title}
      actions={
        <Button variant="ghost" onClick={() => navigate("/admin/truyen")}>
          Hủy
        </Button>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      <form className="admin-split" onSubmit={handleSubmit}>
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Thông tin cơ bản</span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad scroll-area">
            <div className="stack">
              <Field label="Tên truyện" htmlFor="title" error={fieldErrors.title}>
                <TextInput
                  id="title"
                  required
                  value={form.title}
                  onChange={(event) => updateField("title", event.target.value)}
                />
              </Field>

              <Field
                label="Tác giả"
                htmlFor="authorId"
                hint="Chọn tác giả có sẵn, hoặc để trống rồi nhập tên mới bên dưới"
              >
                <Select
                  id="authorId"
                  value={form.authorId}
                  onChange={(event) => updateField("authorId", event.target.value)}
                >
                  <option value="">— Nhập tác giả mới —</option>
                  {authors.map((author) => (
                    <option key={author.id} value={author.id}>
                      {author.name}
                    </option>
                  ))}
                </Select>
              </Field>

              {!form.authorId && (
                <Field label="Tên tác giả mới" htmlFor="authorName" error={fieldErrors.authorName}>
                  <TextInput
                    id="authorName"
                    required
                    value={form.authorName}
                    onChange={(event) => updateField("authorName", event.target.value)}
                  />
                </Field>
              )}

              <Field
                label="Thể loại"
                htmlFor="genreId"
                hint="Chọn thể loại có sẵn, hoặc để trống rồi nhập tên mới bên dưới"
              >
                <Select
                  id="genreId"
                  value={form.genreId}
                  onChange={(event) => updateField("genreId", event.target.value)}
                >
                  <option value="">— Nhập thể loại mới —</option>
                  {genres.map((genre) => (
                    <option key={genre.id} value={genre.id}>
                      {genre.name}
                    </option>
                  ))}
                </Select>
              </Field>

              {!form.genreId && (
                <Field label="Tên thể loại mới" htmlFor="genreName" error={fieldErrors.genreName}>
                  <TextInput
                    id="genreName"
                    required
                    value={form.genreName}
                    onChange={(event) => updateField("genreName", event.target.value)}
                  />
                </Field>
              )}

              <Field label="Trạng thái" htmlFor="status">
                <Select
                  id="status"
                  value={form.status}
                  onChange={(event) => updateField("status", event.target.value)}
                >
                  <option value="ONGOING">Đang ra</option>
                  <option value="COMPLETED">Hoàn thành</option>
                </Select>
              </Field>
            </div>
          </div>
        </aside>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Ảnh bìa &amp; mô tả</span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad admin-panel-body-fill">
            {/* Cover and description share the wide pane: the URL is typed on
                the left and previewed on the right, so a broken link shows up
                before saving. */}
            <div className="admin-cover-grid">
              <div className="admin-editor">
                <Field
                  label="Ảnh bìa (URL)"
                  htmlFor="coverImage"
                  error={fieldErrors.coverImage}
                  hint="Không bắt buộc"
                >
                  <TextInput
                    id="coverImage"
                    type="url"
                    placeholder="https://…"
                    value={form.coverImage}
                    onChange={(event) => updateField("coverImage", event.target.value)}
                  />
                </Field>

                <div className="admin-editor" style={{ marginTop: "var(--space-4)" }}>
                  <label className="nb-label" htmlFor="description">
                    Mô tả
                  </label>
                  <TextArea
                    id="description"
                    style={{ fontFamily: "var(--font-sans)", marginTop: "var(--space-2)" }}
                    value={form.description}
                    onChange={(event) => updateField("description", event.target.value)}
                  />
                  {fieldErrors.description && (
                    <span className="nb-error">{fieldErrors.description}</span>
                  )}
                </div>
              </div>

              <div>
                <span className="nb-label">Xem trước</span>
                {form.coverImage ? (
                  <img
                    className="admin-cover-preview"
                    style={{ marginTop: "var(--space-2)" }}
                    src={form.coverImage}
                    alt="Xem trước ảnh bìa"
                  />
                ) : (
                  <div
                    className="admin-cover-preview"
                    style={{
                      marginTop: "var(--space-2)",
                      display: "grid",
                      placeItems: "center",
                      background: "var(--surface-sunken)",
                    }}
                  >
                    <span className="muted" style={{ fontSize: "0.8rem", textAlign: "center" }}>
                      Chưa có ảnh bìa
                    </span>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              {isEdit ? "Lưu xong sẽ quay lại danh sách chương" : "Tạo xong sẽ tới bước thêm chương"}
            </span>
            <div className="row" style={{ gap: "var(--space-2)" }}>
              <Button variant="ghost" onClick={() => navigate("/admin/truyen")}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                {isEdit ? "Lưu thay đổi" : "Tạo truyện"}
              </Button>
            </div>
          </div>
        </section>
      </form>
    </AdminPage>
  );
}
