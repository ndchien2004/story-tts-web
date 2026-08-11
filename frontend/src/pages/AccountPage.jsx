import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { favoriteApi, progressApi } from "../api/endpoints";
import Avatar from "../components/Avatar";
import AvatarUpload from "../components/AvatarUpload";
import Pagination from "../components/Pagination";
import StoryCard from "../components/StoryCard";
import { useAuth } from "../context/auth-context";
import { formatDate } from "../utils/format";
import { Alert, Badge, ButtonLink, EmptyState, Spinner } from "../components/ui";

const TABS = [
  { id: "reading", label: "Đang đọc dở" },
  { id: "finished", label: "Đã đọc xong" },
  { id: "favorites", label: "Yêu thích" },
];

const FAVORITES_PAGE_SIZE = 12;

function formatWhen(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

/**
 * The reader's own page: who they are, and everything they have kept.
 *
 * Profile and shelf share one screen because they answer the same question —
 * "my stuff" — and splitting them left the profile as a page with four facts on
 * it. The identity column stays put on the left while the lists scroll beside
 * it, the same shape the story page uses.
 *
 * Reading is split in two rather than mixed into one list: a story you are
 * halfway through and a story you have finished want different things from you,
 * and a shelf that shows "đã đọc xong" inside "đang đọc dở" contradicts itself.
 */
export default function AccountPage() {
  const { user, isAdmin, isVip } = useAuth();
  const [tab, setTab] = useState("reading");

  const [shelf, setShelf] = useState(null);
  const [shelfError, setShelfError] = useState(null);

  const [favoritePage, setFavoritePage] = useState(0);
  const [favorites, setFavorites] = useState(null);
  const [favoritesError, setFavoritesError] = useState(null);
  const [loadingFavorites, setLoadingFavorites] = useState(true);

  useEffect(() => {
    let cancelled = false;
    progressApi
      .shelf()
      .then((data) => {
        if (!cancelled) {
          setShelf(data);
          setShelfError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) setShelfError(err.message);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const loadFavorites = useCallback(() => {
    setLoadingFavorites(true);
    favoriteApi
      .mine({ page: favoritePage, size: FAVORITES_PAGE_SIZE })
      .then((data) => {
        setFavorites(data);
        setFavoritesError(null);
      })
      .catch((err) => setFavoritesError(err.message))
      .finally(() => setLoadingFavorites(false));
  }, [favoritePage]);

  useEffect(loadFavorites, [loadFavorites]);

  const [reading, finished] = useMemo(() => {
    if (!shelf) return [null, null];
    return [shelf.filter((entry) => !entry.finished), shelf.filter((entry) => entry.finished)];
  }, [shelf]);

  const name = user.displayName || user.username;
  const counts = { reading: reading?.length, finished: finished?.length };

  return (
    <div className="account">
      <aside className="account-aside scroll-area">
        <div className="account-card">
          <Avatar src={user.avatarUrl} name={name} size="xl" />

          <div className="account-identity">
            <h1>{name}</h1>
            <span className="muted">@{user.username}</span>
          </div>

          {(isAdmin || isVip) && (
            <div className="row" style={{ justifyContent: "center" }}>
              {isAdmin && <Badge tone="info">Admin</Badge>}
              {!isAdmin && isVip && <Badge tone="vip">VIP</Badge>}
            </div>
          )}

          <AvatarUpload />
        </div>

        <dl className="account-facts">
          <div>
            <dt>Email</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>Loại tài khoản</dt>
            <dd>{isAdmin ? "Quản trị viên" : isVip ? "Thành viên VIP" : "Thành viên thường"}</dd>
          </div>
          {/* Only shown to someone whose VIP actually runs out — a permanent
              grant has no date to give. */}
          {user.vipUntil && !user.vipGranted && (
            <div>
              <dt>VIP đến hết</dt>
              <dd>{formatDate(user.vipUntil)}</dd>
            </div>
          )}
          <div>
            <dt>Tham gia</dt>
            <dd>{formatWhen(user.createdAt)}</dd>
          </div>
        </dl>

        {!isAdmin && !isVip && (
          <Alert tone="info">
            Tài khoản thường đọc được chương công khai và chương dành cho thành viên. Chương VIP cần
            tài khoản VIP — <Link to="/nang-cap">xem các gói</Link>.
          </Alert>
        )}

        {/* Nearly-expired is the moment renewal is worth mentioning; before
            that it is just noise on a page about something else. */}
        {!isAdmin && isVip && user.vipUntil && !user.vipGranted && (
          <Alert tone="info">
            Hết hạn VIP, tài khoản trở lại thành viên thường.{" "}
            <Link to="/nang-cap">Gia hạn</Link> bất cứ lúc nào, thời gian còn lại được cộng dồn.
          </Alert>
        )}
      </aside>

      <div className="account-main">
        <nav className="story-tabs" aria-label="Tủ truyện">
          {TABS.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className={`story-tab ${tab === entry.id ? "active" : ""}`}
              aria-selected={tab === entry.id}
              onClick={() => setTab(entry.id)}
            >
              {entry.label}
              {counts[entry.id] > 0 && <span className="story-tab-count">{counts[entry.id]}</span>}
              {entry.id === "favorites" && favorites?.totalElements > 0 && (
                <span className="story-tab-count">{favorites.totalElements}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="story-tab-panel scroll-area">
          {shelfError && tab !== "favorites" && <Alert tone="error">{shelfError}</Alert>}
          {!shelf && !shelfError && tab !== "favorites" && (
            <Spinner label="Đang tải tủ truyện…" />
          )}

          {tab === "reading" && reading && (
            <ShelfList
              entries={reading}
              emptyTitle="Bạn chưa đọc dở truyện nào"
              emptyBody="Mở một chương bất kỳ, hệ thống sẽ nhớ chỗ bạn đang đọc để lần sau quay lại."
            />
          )}

          {tab === "finished" && finished && (
            <ShelfList
              entries={finished}
              done
              emptyTitle="Chưa có truyện nào đọc xong"
              emptyBody="Một truyện chuyển sang đây khi bạn đã đọc hết mọi chương của nó."
            />
          )}

          {tab === "favorites" && (
            <>
              {favoritesError && <Alert tone="error">{favoritesError}</Alert>}
              {loadingFavorites && <Spinner label="Đang tải truyện yêu thích…" />}

              {!loadingFavorites && favorites && favorites.content.length === 0 && (
                <EmptyState title="Chưa có truyện yêu thích nào">
                  Bấm “Yêu thích” ở trang một truyện để lưu vào đây.
                </EmptyState>
              )}

              {!loadingFavorites && favorites && favorites.content.length > 0 && (
                <>
                  <div className="story-grid">
                    {favorites.content.map((story) => (
                      <StoryCard key={story.id} story={story} />
                    ))}
                  </div>
                  <Pagination
                    page={favorites.page}
                    totalPages={favorites.totalPages}
                    onChange={setFavoritePage}
                  />
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * One shelf list.
 *
 * @param done finished stories offer a re-read rather than a "read on", and
 *             show the chapter count as a total instead of as progress
 */
function ShelfList({ entries, done = false, emptyTitle, emptyBody }) {
  if (entries.length === 0) {
    return <EmptyState title={emptyTitle}>{emptyBody}</EmptyState>;
  }

  return (
    <ul className="shelf-grid">
      {entries.map((entry) => (
        <li key={entry.storyId} className="shelf-item">
          <span className="shelf-item-cover" aria-hidden="true">
            {entry.storyCoverImage ? (
              <img src={entry.storyCoverImage} alt="" loading="lazy" />
            ) : (
              entry.storyTitle.slice(0, 1).toUpperCase()
            )}
          </span>

          <div className="shelf-item-body">
            <Link to={`/truyen/${entry.storyId}`} className="shelf-item-title">
              {entry.storyTitle}
            </Link>

            <span className="shelf-item-chapter">
              Chương {entry.chapterNumber}: {entry.chapterTitle}
            </span>

            <div className="shelf-item-foot">
              <Badge tone={done ? "public" : "neutral"}>
                {done
                  ? `Đã đọc xong ${entry.totalChapters} chương`
                  : `${entry.readChapters}/${entry.totalChapters} chương`}
              </Badge>
              <span className="muted">{formatWhen(entry.updatedAt)}</span>
            </div>
          </div>

          <ButtonLink
            to={`/chuong/${entry.chapterId}`}
            size="sm"
            variant={done ? "default" : "primary"}
          >
            {done ? "Đọc lại" : "Đọc tiếp"}
          </ButtonLink>
        </li>
      ))}
    </ul>
  );
}
