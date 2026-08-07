import { useEffect, useState } from "react";
import { storyApi } from "../api/endpoints";
import StoryCard from "../components/StoryCard";
import { Alert, ButtonLink, Spinner } from "../components/ui";

export default function HomePage() {
  const [newest, setNewest] = useState([]);
  const [popular, setPopular] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      storyApi.list({ sort: "newest", size: 6 }),
      storyApi.list({ sort: "popular", size: 6 }),
    ])
      .then(([newestPage, popularPage]) => {
        if (cancelled) return;
        setNewest(newestPage.content);
        setPopular(popularPage.content);
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
  }, []);

  return (
    <div className="stack" style={{ gap: "2.5rem" }}>
      <section className="hero">
        <div>
          <h1 style={{ marginBottom: "0.75rem" }}>Đọc truyện. Nghe truyện. Bằng cả giọng AI.</h1>
          <p style={{ maxWidth: "56ch", fontWeight: 600 }}>
            Chưa có bản thu âm? Bấm một nút để hệ thống tự chuyển chương truyện thành giọng đọc
            tiếng Việt và nghe ngay trên trình duyệt.
          </p>
        </div>
        <div className="row">
          <ButtonLink to="/truyen" size="lg" className="nb-btn-info">
            Khám phá truyện
          </ButtonLink>
          <ButtonLink to="/dang-ky" size="lg">
            Tạo tài khoản
          </ButtonLink>
        </div>
      </section>

      {error && <Alert tone="error">{error}</Alert>}

      {loading ? (
        <Spinner />
      ) : (
        <>
          <section>
            <div className="row-between nb-section-title">
              <h2>Mới cập nhật</h2>
            </div>
            <div className="story-grid">
              {newest.map((story) => (
                <StoryCard key={story.id} story={story} />
              ))}
            </div>
          </section>

          <section>
            <div className="row-between nb-section-title">
              <h2>Được xem nhiều</h2>
            </div>
            <div className="story-grid">
              {popular.map((story) => (
                <StoryCard key={story.id} story={story} />
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
