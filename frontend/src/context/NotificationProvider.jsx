import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { notificationApi } from "../api/endpoints";
import { useAuth } from "./auth-context";
import { NotificationContext } from "./notification-context";

/** Bao nhiêu dòng cái chuông giữ sẵn. Trang lịch sử đầy đủ tự phân trang lấy. */
const PREVIEW_SIZE = 10;

/** Nghỉ trước lần nối lại đầu tiên, rồi nhân đôi dần. */
const RETRY_BASE_MS = 2_000;

/** Trần của quãng nghỉ. Đủ thưa để một máy chủ đang chết không bị dội request. */
const RETRY_MAX_MS = 60_000;

/**
 * Hộp thư của người đang đăng nhập, giữ ở một chỗ cho cả trang.
 *
 * <h3>Máy chủ là nguồn sự thật; chỗ này chỉ là bản sao gần đây nhất</h3>
 * Ba thứ được giữ ở đây — số chưa đọc, mười dòng mới nhất, và trạng thái kết
 * nối — và cả ba đều được máy chủ nói lại ở mỗi lần đồng bộ. Không có phép cộng
 * trừ nào chạy một mình: đánh dấu đã đọc thì con số mới đến từ response, nhận
 * một thông báo mới thì con số mới đi kèm khung tin. Tự trừ đi một sẽ lệch dần
 * theo từng khung tin bị bỏ lỡ, và không có gì kéo nó về.
 *
 * <h3>Ba đường đồng bộ, và vì sao cần cả ba</h3>
 * <pre>
 *   mở trang / đăng nhập  → hỏi lại toàn bộ  → phần bỏ lỡ lúc offline
 *   luồng SSE nối được    → hỏi lại toàn bộ  → phần bỏ lỡ lúc mất mạng
 *   tab quay lại tiền cảnh → hỏi lại toàn bộ → phần bỏ lỡ lúc trình duyệt ngủ
 * </pre>
 *
 * Đường thứ ba tồn tại vì trình duyệt trên điện thoại đóng băng cả tab lẫn kết
 * nối của nó khi người dùng chuyển sang ứng dụng khác, và nó không báo cho ai
 * biết. Không có đường ấy thì mở lại trang sau nửa tiếng vẫn thấy con số của
 * nửa tiếng trước.
 *
 * <h3>Vòng đời phiên</h3>
 * Toàn bộ trạng thái treo vào `user.id`. Đăng xuất thì hiệu ứng dọn dẹp đóng
 * kết nối và xóa sạch; người khác đăng nhập vào cùng trình duyệt thì khóa đổi
 * nên mọi thứ được dựng lại từ đầu. Không có đường nào để thông báo của người
 * trước còn lại trên màn hình của người sau.
 */
export default function NotificationProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  const [unread, setUnread] = useState(0);
  const [latest, setLatest] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [live, setLive] = useState(false);

  /*
   * Ai đang được phục vụ, đọc được từ bên trong những hàm chạy bất đồng bộ.
   *
   * Một response rời máy chủ trước khi người dùng đăng xuất vẫn có thể về sau
   * đó. So khóa này ngay trước khi ghi vào state là thứ chặn nó ghi hộp thư của
   * người vừa đi vào màn hình của người vừa tới.
   */
  const sessionRef = useRef(null);
  sessionRef.current = userId;

  /** Hỏi lại toàn bộ: số chưa đọc và mười dòng mới nhất, trong một lượt. */
  const refresh = useCallback(async () => {
    const session = sessionRef.current;
    if (!session) return;

    setLoading(true);
    try {
      const [page, count] = await Promise.all([
        notificationApi.list({ page: 0, size: PREVIEW_SIZE }),
        notificationApi.unreadCount(),
      ]);

      if (sessionRef.current !== session) return;

      setLatest(page.content ?? []);
      setUnread(count.unread ?? 0);
      setError(null);
    } catch {
      if (sessionRef.current !== session) return;
      // Hộp thư hỏng thì thanh điều hướng vẫn phải dùng được: cái chuông giữ
      // con số cũ, và lần đồng bộ kế tiếp sửa nó.
      setError("Không tải được thông báo.");
    } finally {
      if (sessionRef.current === session) setLoading(false);
    }
  }, []);

  /* ---------------------------------------------------------------- */
  /* Đăng nhập, đăng xuất                                              */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!userId) {
      setUnread(0);
      setLatest([]);
      setError(null);
      setLive(false);
      return;
    }
    refresh();
  }, [userId, refresh]);

  /* ---------------------------------------------------------------- */
  /* Luồng đẩy                                                         */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!userId) return undefined;

    // EventSource vắng mặt ở vài webview nhúng. Ở đó hộp thư vẫn đầy đủ, chỉ là
    // nó cập nhật khi mở trang chứ không ngay lập tức.
    if (typeof window === "undefined" || typeof window.EventSource === "undefined") {
      return undefined;
    }

    let cancelled = false;
    let source = null;
    let retryTimer = null;
    let attempt = 0;

    const connect = async () => {
      if (cancelled) return;

      let url;
      try {
        // Vé dùng một lần, xin bằng header. Xem `notificationApi.streamUrl`.
        url = await notificationApi.streamUrl();
      } catch {
        scheduleRetry();
        return;
      }
      if (cancelled) return;

      try {
        source = new EventSource(url);
      } catch {
        scheduleRetry();
        return;
      }

      source.addEventListener("subscribed", () => {
        if (cancelled) return;
        attempt = 0;
        setLive(true);
        // Đồng bộ ngay khi đường thông, không phải khi có khung tin đầu tiên:
        // khoảng thời gian giữa lúc mất kết nối và lúc nối lại là đúng khoảng
        // mà những thông báo bị bỏ lỡ nằm trong đó.
        refresh();
      });

      source.addEventListener("notification", (event) => {
        if (cancelled) return;
        const frame = parse(event.data);
        if (!frame?.notification) return;

        setUnread(frame.unread ?? 0);
        setLatest((current) => prepend(current, frame.notification));
      });

      source.addEventListener("notifications-read", (event) => {
        if (cancelled) return;
        const frame = parse(event.data);
        if (!frame) return;

        // Một cửa sổ khác của cùng tài khoản vừa đọc gì đó. Cập nhật tại chỗ
        // thay vì hỏi lại cả hộp thư: khung tin đã mang đủ thứ cần.
        setUnread(frame.unread ?? 0);
        setLatest((current) => applyRead(current, frame));
      });

      source.onerror = () => {
        if (cancelled) return;
        setLive(false);
        // Vé đã bị tiêu, nên cơ chế tự nối lại của EventSource sẽ chỉ nhận 401.
        // Đóng hẳn rồi tự nối lại là đường duy nhất đi được — và cũng là đường
        // duy nhất kèm được một lần đồng bộ.
        source?.close();
        source = null;
        scheduleRetry();
      };
    };

    const scheduleRetry = () => {
      if (cancelled || retryTimer) return;
      const delay = Math.min(RETRY_BASE_MS * 2 ** attempt, RETRY_MAX_MS);
      attempt += 1;
      retryTimer = window.setTimeout(() => {
        retryTimer = null;
        connect();
      }, delay);
    };

    connect();

    return () => {
      cancelled = true;
      setLive(false);
      if (retryTimer) window.clearTimeout(retryTimer);
      source?.close();
    };
  }, [userId, refresh]);

  /* ---------------------------------------------------------------- */
  /* Tab quay lại tiền cảnh                                            */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!userId || typeof document === "undefined") return undefined;

    const onVisible = () => {
      if (document.visibilityState === "visible") refresh();
    };

    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [userId, refresh]);

  /* ---------------------------------------------------------------- */
  /* Thao tác                                                          */
  /* ---------------------------------------------------------------- */

  /**
   * Đánh dấu một thông báo đã đọc.
   *
   * Đổi màu ngay tại chỗ cho khỏi giật, nhưng con số thì lấy từ response — máy
   * chủ mới là bên biết còn bao nhiêu, nhất là khi tab kia vừa đọc cùng dòng ấy.
   */
  const markRead = useCallback(async (id) => {
    const session = sessionRef.current;
    if (!session) return;

    setLatest((current) =>
      current.map((item) => (item.id === id ? { ...item, read: true } : item)),
    );

    try {
      const result = await notificationApi.markRead(id);
      if (sessionRef.current === session) setUnread(result.unread ?? 0);
    } catch {
      // Lệnh không tới nơi: kéo màn hình về đúng trạng thái máy chủ đang giữ,
      // thay vì để lại một dòng trông như đã đọc mà lần tải sau lại chưa.
      refresh();
    }
  }, [refresh]);

  const markAllRead = useCallback(async () => {
    const session = sessionRef.current;
    if (!session) return;

    setLatest((current) => current.map((item) => ({ ...item, read: true })));
    setUnread(0);

    try {
      const result = await notificationApi.markAllRead();
      if (sessionRef.current === session) setUnread(result.unread ?? 0);
    } catch {
      refresh();
    }
  }, [refresh]);

  const value = useMemo(
    () => ({ unread, latest, loading, error, live, markRead, markAllRead, refresh }),
    [unread, latest, loading, error, live, markRead, markAllRead, refresh],
  );

  return (
    <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>
  );
}

/** Một khung tin không đọc được không đáng làm hỏng cả trang. */
function parse(data) {
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}

/**
 * Thêm một thông báo mới vào đầu danh sách.
 *
 * Bỏ qua khi id ấy đã có: luồng đẩy là kiểu "ít nhất một lần", và một lần nối
 * lại kèm đồng bộ hoàn toàn có thể mang về đúng dòng mà khung tin vừa mang.
 * Không lọc thì cùng một thông báo hiện hai lần trên màn hình.
 */
function prepend(current, incoming) {
  if (current.some((item) => item.id === incoming.id)) return current;
  return [incoming, ...current].slice(0, PREVIEW_SIZE);
}

/** Áp trạng thái đã đọc mà một cửa sổ khác vừa đặt. */
function applyRead(current, frame) {
  if (frame.all) return current.map((item) => ({ ...item, read: true }));

  const ids = new Set(frame.ids ?? []);
  return current.map((item) => (ids.has(item.id) ? { ...item, read: true } : item));
}
