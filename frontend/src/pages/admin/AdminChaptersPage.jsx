import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import AudioUploadButton from "../../components/AudioUploadButton";
import { AccessBadge, Alert, Button, ButtonLink, EmptyState, Select, Spinner } from "../../components/ui";

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
      setNotice(`Đã đổi mức khóa của "${chapter.title}".`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  async function handleDelete(chapter) {
    if (!window.confirm(`Xóa chương "${chapter.title}"?`)) return;

    try {
      await adminApi.deleteChapter(chapter.id);
      setNotice(`Đã xóa chương "${chapter.title}".`);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  if (loading) return <Spinner />;

  return (
    <div className="stack" style={{ gap: "1.5rem" }}>
      <div className="row-between">
        <div>
          <Link to="/admin" className="muted" style={{ fontWeight: 700 }}>
            ← Danh sách truyện
          </Link>
          <h1 style={{ marginTop: "0.35rem" }}>{story?.title}</h1>
        </div>
        <ButtonLink to={`/admin/truyen/${storyId}/chuong/moi`} variant="primary">
          + Thêm chương
        </ButtonLink>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {chapters.length === 0 ? (
        <EmptyState icon="📄" title="Truyện chưa có chương nào">
          Bấm “Thêm chương” để đăng chương đầu tiên.
        </EmptyState>
      ) : (
        <div className="nb-table-wrap">
          <table className="nb-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Tiêu đề</th>
                <th>Mức khóa hiện tại</th>
                <th>Đổi nhanh mức khóa</th>
                <th>Audio</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {chapters.map((chapter) => (
                <tr key={chapter.id}>
                  <td>{chapter.chapterNumber}</td>
                  <td style={{ whiteSpace: "normal", minWidth: "16rem" }}>{chapter.title}</td>
                  <td>
                    <AccessBadge level={chapter.accessLevel} label={chapter.requirementLabel} />
                  </td>
                  <td>
                    <Select
                      aria-label={`Mức khóa của chương ${chapter.chapterNumber}`}
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
                  <td>{chapter.hasAudio ? "🎧 Có" : "—"}</td>
                  <td>
                    <div className="row" style={{ gap: "0.35rem", flexWrap: "nowrap" }}>
                      <AudioUploadButton
                        chapterId={chapter.id}
                        hasAudio={chapter.hasAudio}
                        onUploaded={() => {
                          setNotice(`Đã tải lên audio cho "${chapter.title}".`);
                          load();
                        }}
                        onError={setError}
                      />
                      <ButtonLink to={`/admin/chuong/${chapter.id}`} size="sm">
                        Sửa
                      </ButtonLink>
                      <Button size="sm" variant="danger" onClick={() => handleDelete(chapter)}>
                        Xóa
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
