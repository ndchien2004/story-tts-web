import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { favoriteApi, notificationApi, progressApi, vipApi, walletApi } from "../api/endpoints";
import Avatar from "../components/Avatar";
import AvatarUpload from "../components/AvatarUpload";
import GiftCodeRedeem from "../components/GiftCodeRedeem";
import { NotificationRow } from "../components/NotificationBell";
import Pagination from "../components/Pagination";
import StoryCard from "../components/StoryCard";
import { useAuth } from "../context/auth-context";
import { useNotifications } from "../context/notification-context";
import { formatCoinDelta, formatCoins, formatDate, formatDateTime, formatVnd } from "../utils/format";
import { Alert, Badge, Button, ButtonLink, EmptyState, Spinner } from "../components/ui";

const TABS = [
  { id: "reading", label: "Đang đọc dở" },
  { id: "finished", label: "Đã đọc xong" },
  { id: "favorites", label: "Yêu thích" },
  { id: "wallet", label: "Lịch sử Xu" },
  { id: "orders", label: "Đơn đã mua" },
  // Cùng chỗ với bốn cái kia vì cùng trả lời một câu: "của tôi". Cái chuông
  // trên thanh điều hướng giữ mười dòng mới nhất; lịch sử đầy đủ ở đây.
  { id: "notifications", label: "Thông báo" },
];

const FAVORITES_PAGE_SIZE = 12;
const WALLET_PAGE_SIZE = 20;
const NOTIFICATION_PAGE_SIZE = 20;

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

  /*
   * Tab mở sẵn đọc từ địa chỉ, không chỉ từ state trong bộ nhớ.
   *
   * Một thông báo "chương của bạn đã bị gỡ" mời người đọc đi xem lại sổ Xu, và
   * cái nút ấy phải mở đúng tab chứ không đổ họ xuống "Đang đọc dở" rồi để họ
   * tự tìm. Đó cũng là lý do đường dẫn được dựng từ ý định ở một chỗ duy nhất —
   * xem `notificationRoutes.js`.
   *
   * Giá trị lạ thì rơi về tab mặc định: địa chỉ là thứ người ta gõ tay được.
   */
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");

  /*
   * Suy thẳng từ địa chỉ, không giữ thêm một bản trong state.
   *
   * Hai nguồn sự thật ở đây sẽ lệch nhau ở đúng một trường hợp, và nó là trường
   * hợp thật: đang mở tab "Lịch sử Xu" rồi bấm "Xem tất cả thông báo" trong
   * chuông. Địa chỉ đổi mà trang không bị dựng lại, nên một state khởi tạo một
   * lần từ URL sẽ ở lại nguyên chỗ cũ và cái liên kết trông như hỏng.
   */
  const tab = TABS.some((entry) => entry.id === requestedTab) ? requestedTab : "reading";

  // Đổi tab thì địa chỉ đổi theo, để một lần tải lại trang không quay về đầu.
  // `replace` chứ không đẩy thêm mục lịch sử: bấm Back sau khi xem ba tab nên
  // đưa người ta rời khỏi trang tài khoản, không phải đi ngược ba bước tại chỗ.
  const openTab = useCallback(
    (id) => setSearchParams(id === "reading" ? {} : { tab: id }, { replace: true }),
    [setSearchParams],
  );

  const [shelf, setShelf] = useState(null);
  const [shelfError, setShelfError] = useState(null);

  const [favoritePage, setFavoritePage] = useState(0);
  const [favorites, setFavorites] = useState(null);
  const [favoritesError, setFavoritesError] = useState(null);
  const [loadingFavorites, setLoadingFavorites] = useState(true);

  // Số dư nằm ở cột hồ sơ nên phải tải cùng trang, không đợi mở tab.
  const [balance, setBalance] = useState(null);

  useEffect(() => {
    let cancelled = false;
    walletApi
      .balance()
      .then((data) => {
        if (!cancelled) setBalance(data.balance);
      })
      // Ví hỏng không được làm hỏng cả trang tài khoản: chỗ số dư hiện dấu ba
      // chấm, phần còn lại vẫn dùng bình thường.
      .catch(() => {
        if (!cancelled) setBalance(0);
      });

    return () => {
      cancelled = true;
    };
  }, []);

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

  // Cùng con số với cái chuông trên thanh điều hướng, lấy từ cùng một chỗ —
  // không phải một phép đếm thứ hai chạy song song và lệch dần với nó.
  const { unread } = useNotifications();
  const counts = {
    reading: reading?.length,
    finished: finished?.length,
    notifications: unread,
  };

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
          {/* A granted VIP has no date to give — which is the fact itself, not
              a reason to say nothing. Leaving the row out altogether left an
              account reading "Thành viên VIP" with no answer to "đến bao giờ".
              Only shown at all to an account that has a VIP of some kind. */}
          {(user.vipGranted || user.vipUntil) && (
            <div>
              <dt>Hạn VIP</dt>
              <dd>{user.vipGranted ? "Vĩnh viễn" : formatDate(user.vipUntil)}</dd>
            </div>
          )}
          <div>
            <dt>Tham gia</dt>
            <dd>{formatWhen(user.createdAt)}</dd>
          </div>
        </dl>

        {/* Ví đứng riêng khỏi bảng thông tin: số dư là thứ thay đổi và là thứ
            người ta tới đây để xem, còn email với ngày tham gia thì không. */}
        <div className="wallet-panel">
          <div className="wallet-panel-head">
            <span className="label">Ví Xu</span>
            <strong className="wallet-balance tabular-num">
              {balance === null ? "…" : formatCoins(balance)}
            </strong>
          </div>
          <ButtonLink to="/nap-xu" size="sm" block>
            Nạp Xu
          </ButtonLink>

          {/* Ngay dưới số dư, vì đó là chỗ người vừa nhận được một cái mã sẽ
              nhìn tới. Số dư ở trên tự cập nhật khi đổi xong, nên không có
              khoảnh khắc nào nó nói một con số cũ. */}
          <GiftCodeRedeem compact onRedeemed={(result) => setBalance(result.balance)} />
        </div>

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
              onClick={() => openTab(entry.id)}
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

          {tab === "wallet" && <WalletHistory balance={balance} />}

          {tab === "orders" && <OrderList />}

          {tab === "notifications" && <NotificationHistory />}

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

const ORDER_TONE = {
  PAID: "public",
  PENDING: "warning",
  CANCELLED: "neutral",
  EXPIRED: "neutral",
};

/**
 * Sổ cái Xu của chính người đọc.
 *
 * <p>Mỗi dòng nói số dư đi từ đâu sang đâu, không chỉ nói cộng hay trừ bao nhiêu.
 * Đó là thứ khiến người dùng tự đối chiếu được khi thấy số dư lạ — và một câu hỏi
 * tự trả lời được là một câu hỏi không đến hộp thư hỗ trợ.
 */
function WalletHistory({ balance }) {
  const [page, setPage] = useState(0);
  const [history, setHistory] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    walletApi
      .transactions({ page, size: WALLET_PAGE_SIZE })
      .then((data) => {
        if (!cancelled) {
          setHistory(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });

    return () => {
      cancelled = true;
    };
  }, [page]);

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!history) return <Spinner label="Đang tải lịch sử Xu…" />;

  if (history.content.length === 0) {
    return (
      <EmptyState title="Chưa có giao dịch Xu nào">
        Xu dùng để mở khóa từng chương. <Link to="/nap-xu">Nạp Xu</Link> rồi mọi lần cộng trừ sẽ
        được ghi lại đầy đủ ở đây.
      </EmptyState>
    );
  }

  return (
    <div className="stack" style={{ gap: "var(--space-4)" }}>
      <p className="muted">
        Số dư hiện tại: <strong className="tabular-num">{formatCoins(balance ?? 0)}</strong>
      </p>

      <ul className="wallet-log">
        {history.content.map((tx) => (
          <li key={tx.id} className="wallet-log-row">
            <div className="wallet-log-main">
              <strong>{tx.description || tx.typeLabel}</strong>
              <span className="muted">
                {tx.typeLabel} · {formatDateTime(tx.createdAt)}
              </span>
            </div>

            <div className="wallet-log-amounts">
              <span
                className={`wallet-log-delta tabular-num ${tx.amount < 0 ? "spend" : "earn"}`}
              >
                {formatCoinDelta(tx.amount)}
              </span>
              {/* Số dư sau giao dịch, để dò lại một chuỗi cộng trừ mà không phải
                  tự cộng nhẩm từ đầu lịch sử. */}
              <span className="muted tabular-num">còn {formatCoins(tx.balanceAfter)}</span>
            </div>
          </li>
        ))}
      </ul>

      <Pagination page={history.page} totalPages={history.totalPages} onChange={setPage} />
    </div>
  );
}

/**
 * Toàn bộ hộp thư, có phân trang.
 *
 * <h3>Vì sao có màn hình này khi đã có cái chuông</h3>
 * Cái chuông giữ mười dòng mới nhất — vừa đủ để trả lời "có gì mới không" mà
 * không kéo cả lịch sử về theo mỗi lần tải trang. Nhưng một lời báo hoàn Xu từ
 * tháng trước vẫn là thứ người ta cần tìm lại được, và một danh sách mười dòng
 * thì không có chỗ cho nó.
 *
 * <h3>Số chưa đọc vẫn do provider giữ</h3>
 * Trang này không đếm lại: nó bấm vào cùng `markRead` mà cái chuông dùng, nên
 * đánh dấu ở đây làm con số trên thanh điều hướng đổi ngay, và làm nó đổi ở tab
 * khác nữa — máy chủ đẩy tin ấy đi. Một bộ đếm riêng ở màn hình này sẽ là nguồn
 * sự thật thứ ba cho một con số vốn chỉ có một.
 */
function NotificationHistory() {
  const { markRead, markAllRead, unread } = useNotifications();

  const [page, setPage] = useState(0);
  const [inbox, setInbox] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    let cancelled = false;
    notificationApi
      .list({ page, size: NOTIFICATION_PAGE_SIZE })
      .then((data) => {
        if (cancelled) return;
        setInbox(data);
        setError(null);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });

    return () => {
      cancelled = true;
    };
  }, [page]);

  useEffect(load, [load]);

  const handleMarkRead = useCallback(
    (id) => {
      // Đổi ngay tại chỗ rồi mới gọi: danh sách này không nghe luồng đẩy, nên
      // đợi response mới đổi màu sẽ thành một khoảng trễ nhìn thấy được.
      setInbox((current) =>
        current
          ? {
              ...current,
              content: current.content.map((item) =>
                item.id === id ? { ...item, read: true } : item,
              ),
            }
          : current,
      );
      markRead(id);
    },
    [markRead],
  );

  const handleMarkAll = useCallback(async () => {
    await markAllRead();
    load();
  }, [markAllRead, load]);

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!inbox) return <Spinner label="Đang tải thông báo…" />;

  if (inbox.content.length === 0) {
    return (
      <EmptyState title="Chưa có thông báo nào">
        Những việc liên quan tới tài khoản của bạn — VIP, hoàn Xu, thanh toán, nội dung bị gỡ —
        sẽ được ghi lại ở đây.
      </EmptyState>
    );
  }

  return (
    <div className="stack" style={{ gap: "var(--space-4)" }}>
      {unread > 0 && (
        <div className="row" style={{ justifyContent: "space-between" }}>
          <span className="muted">{unread} thông báo chưa đọc</span>
          <Button size="sm" onClick={handleMarkAll}>
            Đánh dấu tất cả đã đọc
          </Button>
        </div>
      )}

      <div className="notif-history">
        {inbox.content.map((notification) => (
          <NotificationRow
            key={notification.id}
            notification={notification}
            onOpen={handleMarkRead}
            onNavigate={() => {}}
          />
        ))}
      </div>

      <Pagination page={inbox.page} totalPages={inbox.totalPages} onChange={setPage} />
    </div>
  );
}

/**
 * The reader's own VIP orders.
 *
 * The upgrade page has always told people to "xem lại đơn của bạn ở trang tài
 * khoản", and this is the page it meant — until now there was nothing here, so
 * anyone who closed the payment window mid-way had no way back to their own
 * order. That is what the `checkoutUrl` on a pending order is for.
 */
function OrderList() {
  const { user, refresh: refreshUser } = useAuth();

  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = useCallback(
    () =>
      vipApi
        .orders()
        .then((data) => {
          setOrders(data);
          setError(null);
        })
        .catch((err) => setError(err.message)),
    [],
  );

  useEffect(() => {
    load();
  }, [load]);

  /**
   * Re-asks the gateway about one pending order.
   *
   * Someone who paid and then closed the tab before being redirected back has a
   * paid order the site has not heard about yet; this is the button that lets
   * them say so themselves instead of waiting for the webhook.
   */
  async function recheck(orderCode) {
    setBusy(orderCode);
    try {
      await vipApi.checkOrder(orderCode);
      await load();
      // A paid order changes what the account is, so the header and the badges
      // have to be told as well.
      await refreshUser();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(null);
    }
  }

  async function cancel(orderCode) {
    setBusy(orderCode);
    try {
      await vipApi.cancelOrder(orderCode);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(null);
    }
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!orders) return <Spinner label="Đang tải đơn nâng cấp…" />;

  /*
   * VIP an admin handed over rather than VIP someone bought.
   *
   * It has no order behind it and no date on it, so this tab had nothing to
   * show and fell through to "bạn chưa có đơn nâng cấp nào" — which reads, to
   * an account that *is* VIP, as if its VIP were not real. The grant is the
   * thing to state here, above whatever was bought before it.
   */
  const granted = user?.vipGranted ? (
    <div className="order-grant">
      <Badge tone="vip">VIP vĩnh viễn</Badge>
      <p>
        Quản trị viên đã cấp VIP cho tài khoản này. Quyền VIP không có hạn sử dụng và không cần mua
        gói nào.
      </p>
    </div>
  ) : null;

  if (orders.length === 0) {
    return (
      granted ?? (
        <EmptyState title="Bạn chưa có đơn nâng cấp nào">
          Các gói VIP nằm ở <Link to="/nang-cap">trang nâng cấp</Link>. Đơn đã tạo sẽ hiện ở đây kèm
          tình trạng thanh toán.
        </EmptyState>
      )
    );
  }

  return (
    <>
      {granted}

      <ul className="order-list">
        {orders.map((order) => (
          <li key={order.orderCode} className="order-card">
            <div className="order-card-head">
              <div>
                <strong>{order.itemName}</strong>
                <span className="muted">
                  {order.kind === "COIN_PACKAGE"
                    ? ` · ${formatCoins(order.coinsGranted)}`
                    : order.months
                      ? ` · ${order.months} tháng`
                      : ""}
                </span>
              </div>
              <Badge tone={ORDER_TONE[order.status] ?? "neutral"}>{order.statusLabel}</Badge>
            </div>

            <dl className="order-facts">
              <div>
                <dt>Số tiền</dt>
                <dd className="tabular-num">{formatVnd(order.amountVnd)}</dd>
              </div>
              <div>
                <dt>Mã đơn</dt>
                <dd className="tabular-num">{order.orderCode}</dd>
              </div>
              <div>
                <dt>Tạo lúc</dt>
                <dd>{formatDateTime(order.createdAt)}</dd>
              </div>
              {order.paidAt && (
                <div>
                  <dt>Thanh toán</dt>
                  <dd>{formatDateTime(order.paidAt)}</dd>
                </div>
              )}
              {order.vipUntilAfter && (
                <div>
                  <dt>VIP đến hết</dt>
                  <dd>{formatDate(order.vipUntilAfter)}</dd>
                </div>
              )}
            </dl>

            {order.status === "PENDING" && (
              <div className="order-card-actions">
                {/* Only a pending order carries a checkout link, so this is
                    also the only branch that can offer to finish paying. */}
                {order.checkoutUrl && (
                  <a className="nb-btn nb-btn-primary nb-btn-sm" href={order.checkoutUrl}>
                    Thanh toán tiếp
                  </a>
                )}
                <Button
                  size="sm"
                  loading={busy === order.orderCode}
                  onClick={() => recheck(order.orderCode)}
                >
                  Đã trả rồi, kiểm tra lại
                </Button>
                <Button
                  size="sm"
                  loading={busy === order.orderCode}
                  onClick={() => cancel(order.orderCode)}
                >
                  Hủy đơn
                </Button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </>
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
