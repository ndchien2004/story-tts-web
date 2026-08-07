import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { adminApi, catalogApi, storyApi } from "../../api/endpoints";
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
      navigate(`/admin/truyen/${saved.id}/chuong`);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <Spinner />;

  return (
    <div className="container-narrow">
      <form className="nb-card stack" onSubmit={handleSubmit}>
        <div className="nb-section-title">
          <h1>{isEdit ? "Sửa truyện" : "Thêm truyện mới"}</h1>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

        <Field label="Tên truyện" htmlFor="title" error={fieldErrors.title}>
          <TextInput
            id="title"
            required
            value={form.title}
            onChange={(event) => updateField("title", event.target.value)}
          />
        </Field>

        <Field label="Tác giả" htmlFor="authorId" hint="Chọn tác giả có sẵn, hoặc để trống rồi nhập tên mới bên dưới">
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

        <Field label="Thể loại" htmlFor="genreId" hint="Chọn thể loại có sẵn, hoặc để trống rồi nhập tên mới bên dưới">
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

        <Field label="Ảnh bìa (URL)" htmlFor="coverImage" error={fieldErrors.coverImage} hint="Không bắt buộc">
          <TextInput
            id="coverImage"
            type="url"
            placeholder="https://…"
            value={form.coverImage}
            onChange={(event) => updateField("coverImage", event.target.value)}
          />
        </Field>

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

        <Field label="Mô tả" htmlFor="description" error={fieldErrors.description}>
          <TextArea
            id="description"
            style={{ minHeight: "8rem", fontFamily: "var(--font-sans)" }}
            value={form.description}
            onChange={(event) => updateField("description", event.target.value)}
          />
        </Field>

        <div className="row">
          <Button type="submit" variant="primary" size="lg" loading={submitting}>
            {isEdit ? "Lưu thay đổi" : "Tạo truyện"}
          </Button>
          <Button variant="ghost" onClick={() => navigate("/admin")}>
            Hủy
          </Button>
        </div>
      </form>
    </div>
  );
}
