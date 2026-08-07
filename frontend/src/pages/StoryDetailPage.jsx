import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { storyApi } from "../api/endpoints";
import ChapterRow from "../components/ChapterRow";
import { Alert, Badge, ButtonLink, EmptyState, Spinner } from "../components/ui";

export default function StoryDetailPage() {
  const { storyId } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    storyApi
      .detail(storyId)
      .then((detail) => {
        if (!cancelled) {
          setData(detail);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [storyId]);

  if (loading) return <Spinner />;
  if (error) return <Alert tone="error">{error}</Alert>;
  if (!data) return null;

  const { story, chapters } = data;
  const firstReadable = chapters.find((chapter) => !chapter.locked) ?? chapters[0];

  return (
    <div className="stack" style={{ gap: "2rem" }}>
      <article className="nb-card">
        <div style={{ display: "grid", gap: "1.5rem", gridTemplateColumns: "minmax(0, 1fr)" }}>
          <div className="stack">
            <div className="row" style={{ gap: "0.4rem" }}>
              {story.genre && <Badge tone="info">{story.genre.name}</Badge>}
              <Badge tone={story.status === "COMPLETED" ? "public" : "neutral"}>
                {story.statusLabel}
              </Badge>
            </div>

            <h1>{story.title}</h1>

            <p className="muted">
              Tác giả: <strong>{story.author?.name ?? "Chưa rõ"}</strong> · {story.chapterCount}{" "}
              chương · {story.viewCount} lượt xem
            </p>

            {story.description && <p>{story.description}</p>}

            {firstReadable && (
              <div className="row">
                <ButtonLink to={`/chuong/${firstReadable.id}`} variant="primary" size="lg">
                  Đọc từ đầu
                </ButtonLink>
              </div>
            )}
          </div>
        </div>
      </article>

      <section>
        <div className="nb-section-title">
          <h2>Danh sách chương</h2>
        </div>

        {chapters.length === 0 ? (
          <EmptyState icon="📄" title="Truyện chưa có chương nào">
            Quản trị viên sẽ cập nhật nội dung sớm.
          </EmptyState>
        ) : (
          <ul className="chapter-list">
            {chapters.map((chapter) => (
              <ChapterRow key={chapter.id} chapter={chapter} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
