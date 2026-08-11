import { useEffect, useState } from "react";
import { progressApi, storyApi } from "../api/endpoints";
import { HERO_BANNER } from "../brand";
import StoryCard from "../components/StoryCard";
import { useAuth } from "../context/auth-context";
import { Alert, ButtonLink, Spinner } from "../components/ui";

export default function HomePage() {
  const { user, isAuthenticated, initialising } = useAuth();

  const [newest, setNewest] = useState([]);
  const [popular, setPopular] = useState([]);
  const [suggested, setSuggested] = useState([]);
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

  /**
   * Suggestions load on their own, after the token has been checked.
   *
   * Kept out of the block above for two reasons: it needs to know whether
   * anyone is signed in, and it must not hold up the two lists everybody sees.
   * A failure here leaves the section unrendered rather than showing an error —
   * nobody asked for suggestions, so nobody should be told they failed.
   */
  useEffect(() => {
    if (initialising || !isAuthenticated) {
      setSuggested([]);
      return undefined;
    }

    let cancelled = false;
    progressApi
      .recommendations(6)
      .then((data) => {
        if (!cancelled) setSuggested(data);
      })
      .catch(() => {
        if (!cancelled) setSuggested([]);
      });

    return () => {
      cancelled = true;
    };
  }, [initialising, isAuthenticated]);

  return (
    <div className="stack" style={{ gap: "2.5rem" }}>
      {/* The pitch is aimed at visitors, so it gives way to a greeting once
          someone is signed in — and the sign-up button disappears with it.
          Nothing is decided while the stored token is still being checked,
          which would otherwise flash "Tạo tài khoản" at a signed-in reader. */}
      <section className="hero">
        {/* The artwork already carries the wordmark and the tagline, so it
            sits above the copy rather than behind it — text over the top would
            be the same message twice, and unreadable over the illustration on
            the right. */}
        {/* The source's own dimensions: they set the box the browser reserves
            before the image loads, so they have to match what arrives or the
            page shifts when it does. */}
        <img
          className="hero-banner"
          src={HERO_BANNER}
          alt="Truyện Nghe — đọc và nghe truyện online"
          width="1376"
          height="768"
          fetchPriority="high"
        />

        <div>
          <h1 style={{ marginBottom: "0.75rem" }}>
            {isAuthenticated
              ? `Chào ${user.displayName || user.username}, hôm nay nghe gì?`
              : "Đọc truyện. Nghe truyện. Bằng cả giọng AI."}
          </h1>
          <p style={{ maxWidth: "56ch", fontWeight: 600 }}>
            Chưa có bản thu âm? Bấm một nút để hệ thống tự chuyển chương truyện thành giọng đọc
            tiếng Việt và nghe ngay trên trình duyệt.
          </p>
        </div>
        <div className="row">
          <ButtonLink to="/truyen" size="lg" className="nb-btn-info">
            Khám phá truyện
          </ButtonLink>
          {!isAuthenticated && !initialising && (
            <ButtonLink to="/dang-ky" size="lg">
              Tạo tài khoản
            </ButtonLink>
          )}
        </div>
      </section>

      {error && <Alert tone="error">{error}</Alert>}

      {loading ? (
        <Spinner />
      ) : (
        <>
          {/* Above the general lists: a suggestion built from this reader's own
              history is worth more to them than "mới nhất". Hidden entirely
              when there is no history to build one from. */}
          {suggested.length > 0 && (
            <section>
              <div className="row-between nb-section-title">
                <h2>Gợi ý cho bạn</h2>
                <span className="muted" style={{ fontWeight: 500 }}>
                  Cùng thể loại với truyện bạn đã đọc
                </span>
              </div>
              <div className="story-grid">
                {suggested.map((story) => (
                  <StoryCard key={story.id} story={story} />
                ))}
              </div>
            </section>
          )}

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
