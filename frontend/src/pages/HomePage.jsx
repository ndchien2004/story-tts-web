import { useEffect, useState } from "react";
import { progressApi, storyApi } from "../api/endpoints";
import FeaturedCarousel from "../components/FeaturedCarousel";
import SocialRail from "../components/SocialRail";
import StoryCard from "../components/StoryCard";
import TopListenedRail from "../components/TopListenedRail";
import { useAuth } from "../context/auth-context";
import { Alert, Spinner } from "../components/ui";

/**
 * Rows are locked to four covers on the wide layout, so the lists are asked for
 * multiples of four. A trailing half-empty row is the one thing a fixed grid
 * cannot hide.
 */
const ROW = 4;

export default function HomePage() {
  const { isAuthenticated, initialising } = useAuth();

  const [newest, setNewest] = useState([]);
  const [popular, setPopular] = useState([]);
  const [suggested, setSuggested] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    Promise.all([
      storyApi.list({ sort: "newest", size: ROW * 2 }),
      storyApi.list({ sort: "popular", size: ROW * 2 }),
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
      .recommendations(ROW)
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

  /**
   * The shelf is drawn from whichever list arrived with something in it.
   *
   * "Được xem nhiều" first: the covers on display should be the ones worth
   * displaying, and on a brand-new database that list is simply everything.
   */
  const featured = (popular.length > 0 ? popular : newest).slice(0, 6);

  return (
    /*
     * Three columns spanning the whole viewport, not three columns inside the
     * page's centred measure.
     *
     * The rails belong to the window rather than to the article: they sit in
     * tracks of their own at the two edges and stay there through the shelf as
     * well as the lists. Both tracks are the same width even though the left
     * one holds something far narrower — that symmetry is what keeps the middle
     * column centred on the screen rather than nudged to one side.
     *
     * They are rendered outside the loading branch on purpose. The ranking
     * fetches on its own and the contact links need nothing at all, so there is
     * no reason for either to wait on the story lists.
     */
    <div className="home">
      <div className="home-rail home-rail-left">
        <SocialRail />
      </div>

      <div className="home-centre">
        {error && <Alert tone="error">{error}</Alert>}

        {loading ? (
          <Spinner />
        ) : (
          <>
            <FeaturedCarousel stories={featured} />

            <main className="home-main">
              {/* Above the general lists: a suggestion built from this reader's
                  own history is worth more to them than "mới nhất". Hidden
                  entirely when there is no history to build one from. */}
              {suggested.length > 0 && (
                <section>
                  <div className="row-between nb-section-title">
                    <h2>Gợi ý cho bạn</h2>
                    <span className="muted" style={{ fontWeight: 500 }}>
                      Cùng thể loại với truyện bạn đã đọc
                    </span>
                  </div>
                  <div className="story-grid story-grid-quad">
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
                <div className="story-grid story-grid-quad">
                  {newest.map((story) => (
                    <StoryCard key={story.id} story={story} />
                  ))}
                </div>
              </section>

              <section>
                <div className="row-between nb-section-title">
                  <h2>Được xem nhiều</h2>
                </div>
                <div className="story-grid story-grid-quad">
                  {popular.map((story) => (
                    <StoryCard key={story.id} story={story} />
                  ))}
                </div>
              </section>
            </main>
          </>
        )}
      </div>

      <div className="home-rail home-rail-right">
        <TopListenedRail />
      </div>
    </div>
  );
}
