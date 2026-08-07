import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { adminApi } from "../../api/endpoints";
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
};

/**
 * Create and edit form for a chapter.
 *
 * Creating uses `:storyId`, editing uses `:chapterId`; exactly one is present.
 */
export default function AdminChapterFormPage() {
  const { storyId, chapterId } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(chapterId);

  const [form, setForm] = useState(EMPTY_FORM);
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
        setForm({
          title: chapter.title,
          content: chapter.content,
          chapterNumber: String(chapter.chapterNumber),
          accessLevel: chapter.accessLevel,
        });
        setParentStoryId(chapter.storyId);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [isEdit, chapterId]);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    const payload = {
      title: form.title,
      content: form.content,
      // Left blank on create, the server assigns the next free number.
      chapterNumber: form.chapterNumber ? Number(form.chapterNumber) : undefined,
      accessLevel: form.accessLevel,
    };

    try {
      if (isEdit) {
        await adminApi.updateChapter(chapterId, payload);
      } else {
        await adminApi.createChapter(storyId, payload);
      }
      navigate(`/admin/truyen/${parentStoryId}/chuong`);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <Spinner />;

  const characterCount = form.content.length;

  return (
    <div className="container-narrow">
      <form className="nb-card stack" onSubmit={handleSubmit}>
        <div className="nb-section-title">
          <h1>{isEdit ? "Sửa chương" : "Thêm chương mới"}</h1>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

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

        <Field
          label="Nội dung chương"
          htmlFor="content"
          error={fieldErrors.content}
          hint={`${characterCount.toLocaleString("vi-VN")} ký tự`}
        >
          <TextArea
            id="content"
            required
            style={{ minHeight: "22rem" }}
            value={form.content}
            onChange={(event) => updateField("content", event.target.value)}
          />
        </Field>

        <div className="row">
          <Button type="submit" variant="primary" size="lg" loading={submitting}>
            {isEdit ? "Lưu thay đổi" : "Đăng chương"}
          </Button>
          <Button
            variant="ghost"
            onClick={() => navigate(`/admin/truyen/${parentStoryId}/chuong`)}
          >
            Hủy
          </Button>
        </div>
      </form>
    </div>
  );
}
