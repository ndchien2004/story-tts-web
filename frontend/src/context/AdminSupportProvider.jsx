import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { adminSupportApi } from "../api/endpoints";
import { AdminSupportContext } from "./admin-support-context";
import { useAuth } from "./auth-context";
import { useSupportSocket } from "./support-socket-context";

/**
 * Giữ số liệu hộp thư hỗ trợ cho cả bảng quản trị, và làm nó tươi theo thời
 * gian thực.
 *
 * <h3>Bốn đường làm mới, và vì sao cần cả bốn</h3>
 * <pre>
 *   mở bảng quản trị     → hỏi một lượt          (F5, hoặc vừa đăng nhập)
 *   khung tin WebSocket  → hỏi lại               (tức thời, đường chính)
 *   socket nối lại       → hỏi lại               (phần bỏ lỡ lúc mất mạng)
 *   tab quay lại tiền cảnh → hỏi lại             (trình duyệt điện thoại đóng băng tab)
 *   nhịp thưa 45 giây    → hỏi lại               (lưới đỡ cuối)
 * </pre>
 *
 * Ba đường giữa là thứ khiến huy hiệu <i>đúng</i>; đường đầu và đường cuối là
 * thứ khiến nó <i>không bao giờ kẹt</i>. Nhịp thưa không thừa dù đã có
 * WebSocket: nó là thứ duy nhất còn chạy trong quãng mất kết nối, và là thứ duy
 * nhất bắt được tin được ghi ở một bản ứng dụng khác — xem phần "Nhiều bản ứng
 * dụng" trong `docs/SUPPORT_MESSAGING.md`.
 *
 * <h3>Khung tin nào đáng làm mới</h3>
 * <pre>
 *   message:new                    → LUÔN LUÔN
 *   message:read, reader = ADMIN   → có
 *   message:read, reader = USER    → không
 * </pre>
 *
 * Dòng đầu đáng nói, vì trực giác sai ở đây: một tin do <i>quản trị viên</i>
 * gửi cũng đổi con số. Lượt ghi ở máy chủ đẩy luôn mốc "đã đọc" của chính phía
 * gửi (`SupportStore.append` → `advanceReadMark`), nên trả lời một luồng là
 * đọc nốt nó — và luồng ấy rời khỏi hàng đợi chờ trả lời. Lọc theo
 * `senderRole === "USER"` ở đây sẽ để lại một huy hiệu không bao giờ tắt sau
 * khi người ta vừa trả lời xong.
 *
 * <p>Dòng cuối thì ngược lại: người đọc xem tin của mình tới đâu không liên
 * quan gì tới việc phía hỗ trợ còn bao nhiêu việc. Khung tin ấy vẫn tới mọi
 * cửa sổ quản trị (một hình dạng khung tin cho cả hai phía — xem
 * `SupportRealtime.onReadUpdated`), nên bỏ qua nó ở đây là bỏ đi một lượt gọi
 * API cho mỗi lần người đọc mở hộp thoại chat.
 */
export default function AdminSupportProvider({ children }) {
  const { user } = useAuth();
  const socket = useSupportSocket();

  /*
   * Treo vào cả id lẫn vai trò.
   *
   * Vai trò một mình là chưa đủ: đăng xuất rồi một quản trị viên khác đăng nhập
   * vào cùng trình duyệt thì vai trò không đổi, và con số của người trước sẽ
   * nằm lại trên màn hình người sau cho tới lượt hỏi kế tiếp.
   */
  const accountKey = user?.role === "ADMIN" ? `admin:${user.id}` : null;

  const [summary, setSummary] = useState(null);

  /*
   * Chống response về sai thứ tự.
   *
   * Một đợt tin nhắn dồn dập sinh ra vài lượt hỏi chồng nhau, và mạng không hứa
   * chúng về đúng thứ tự đã đi. Lượt hỏi cũ về sau lượt hỏi mới sẽ ghi đè một
   * con số mới bằng một con số cũ — và vì không có gì kéo nó về cho tới nhịp 45
   * giây sau, huy hiệu sẽ hiện sai suốt quãng ấy.
   *
   * `applied` là số thứ tự của lượt hỏi đã được vẽ lên. Chỉ lượt hỏi mới hơn nó
   * mới được phép vẽ đè.
   */
  const issued = useRef(0);
  const applied = useRef(0);

  /** Màn hình còn cần con số này không — đọc được từ trong hàm bất đồng bộ. */
  const liveKey = useRef(null);
  liveKey.current = accountKey;

  /*
   * Tháo hẳn thì cũng không còn màn hình nào để vẽ lên.
   *
   * Gán ở lúc vẽ (dòng trên) lo được mọi lần ĐỔI tài khoản, nhưng không lo được
   * lần cuối cùng: sau khi tháo thì không có lần vẽ nào nữa để gán null, và một
   * response đang bay sẽ tưởng mình vẫn còn chỗ để hạ cánh.
   */
  useEffect(() => () => { liveKey.current = null; }, []);

  const coalesceTimer = useRef(null);

  /* ---------------------------------------------------------------- */
  /* Hỏi lại                                                           */
  /* ---------------------------------------------------------------- */

  const refresh = useCallback(async () => {
    const mine = liveKey.current;
    if (!mine) return;

    const seq = (issued.current += 1);
    try {
      const next = await adminSupportApi.summary();
      // Ba phép so, ba chuyện khác nhau: tài khoản đã đổi, nhà cung cấp đã
      // tháo, và câu trả lời này đã cũ hơn cái đang hiện.
      if (liveKey.current !== mine) return;
      if (seq <= applied.current) return;
      applied.current = seq;
      setSummary(next);
    } catch {
      // Huy hiệu là thứ trang trí, và mọi đường làm mới đều lặp lại. Một lượt
      // hỏi hỏng không đáng biến thành một thông báo lỗi trên màn hình của
      // người đang làm việc khác — lượt kế tiếp sửa nó.
      //
      // Cố ý KHÔNG tiến `applied`: lượt hỏng chưa vẽ gì, nên nó không được
      // phép chặn một lượt cũ hơn đang trên đường về.
    }
  }, []);

  /**
   * Gom nhiều tín hiệu sát nhau thành một lượt hỏi.
   *
   * <p>Một người đọc dán vào ô chat năm dòng rồi bấm gửi năm lần trong hai giây
   * là năm khung tin `message:new`. Không gom thì đó là năm lượt gọi API cho
   * một con số duy nhất, trên một máy chủ hai mươi luồng — và bốn lượt đầu bị
   * lượt thứ năm ghi đè ngay.
   *
   * <p>Hẹn giờ đuôi chứ không phải đầu: con số phải phản ánh khung tin
   * <i>cuối</i> của đợt, không phải khung tin đầu.
   */
  const scheduleRefresh = useCallback(() => {
    if (coalesceTimer.current) return;
    coalesceTimer.current = window.setTimeout(() => {
      coalesceTimer.current = null;
      refresh();
    }, COALESCE_MS);
  }, [refresh]);

  /* ---------------------------------------------------------------- */
  /* Mở bảng quản trị, và đổi tài khoản                                */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    // Đổi tài khoản là quên sạch: con số cũ không được phép nán lại dù một
    // khung hình, và mọi lượt hỏi đang bay không được phép hạ cánh.
    issued.current = 0;
    applied.current = 0;
    setSummary(null);

    if (!accountKey) return undefined;
    refresh();

    return () => {
      if (coalesceTimer.current) {
        window.clearTimeout(coalesceTimer.current);
        coalesceTimer.current = null;
      }
    };
  }, [accountKey, refresh]);

  /* ---------------------------------------------------------------- */
  /* Khung tin đi xuống                                                */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!accountKey || !socket) return undefined;

    return socket.subscribe((frame) => {
      if (frame.type === EVENT_MESSAGE_NEW) {
        scheduleRefresh();
        return;
      }
      if (frame.type === EVENT_MESSAGE_READ && frame.payload?.reader === "ADMIN") {
        // Đây là đường mà huy hiệu tụt xuống khi MỘT quản trị viên nào đó mở
        // luồng ra đọc — kể cả người ngồi máy khác. Mốc đã đọc của phía hỗ trợ
        // là mốc dùng chung, nên việc ấy đã xong với cả đội.
        scheduleRefresh();
      }
    });
  }, [accountKey, socket, scheduleRefresh]);

  /*
   * Đường thông trở lại là một lần hỏi lại.
   *
   * Chỉ ở lúc CHUYỂN từ mất kết nối sang có. Thiếu phép so này thì mỗi lần cờ
   * `live` được đọc lại là một lượt gọi API thừa — và nó được đọc lại mỗi lần
   * `AdminSupportProvider` vẽ lại.
   */
  const live = socket?.live ?? false;
  const wasLive = useRef(false);
  useEffect(() => {
    const reconnected = live && !wasLive.current;
    wasLive.current = live;
    if (accountKey && reconnected) refresh();
  }, [accountKey, live, refresh]);

  /* ---------------------------------------------------------------- */
  /* Tab quay lại, và nhịp thưa                                        */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!accountKey || typeof document === "undefined") return undefined;
    const onVisible = () => {
      if (document.visibilityState === "visible") refresh();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [accountKey, refresh]);

  useEffect(() => {
    if (!accountKey) return undefined;
    const timer = window.setInterval(refresh, POLL_MS);
    return () => window.clearInterval(timer);
  }, [accountKey, refresh]);

  /* ---------------------------------------------------------------- */

  const value = useMemo(() => ({
    /** Số luồng có tin của người đọc mà phía hỗ trợ chưa đọc. Đây là con số trên huy hiệu. */
    awaitingReply: summary?.awaitingReply ?? 0,
    /** Tổng số kết nối WebSocket đang mở trên bản ứng dụng này. */
    openConnections: summary?.openConnections ?? 0,
    /** Phần trong số ấy thuộc về quản trị viên. */
    adminConnections: summary?.adminConnections ?? 0,
    /** Đã có câu trả lời đầu tiên chưa — để giao diện khỏi vẽ "0 chờ trả lời" lúc còn đang tải. */
    loaded: summary != null,
    /** Đường thời gian thực có đang thông không. */
    live,
    refresh,
  }), [summary, live, refresh]);

  return (
    <AdminSupportContext.Provider value={value}>{children}</AdminSupportContext.Provider>
  );
}

/* ------------------------------------------------------------------ */
/* Hằng số                                                             */
/* ------------------------------------------------------------------ */

/*
 * Cùng nhịp với danh sách hộp thư trong `AdminSupportPage`, và cố ý như vậy:
 * hai thứ nói về cùng một dữ liệu thì không nên lệch nhịp, nếu không sẽ có
 * những quãng mà huy hiệu nói một đằng còn danh sách ngay bên cạnh nói một nẻo.
 */
const POLL_MS = 45_000;

/** Đủ dài để gom một đợt tin dồn dập, đủ ngắn để không ai thấy huy hiệu chậm. */
const COALESCE_MS = 250;

/* Cùng tên với `SupportRealtime` ở máy chủ. */
const EVENT_MESSAGE_NEW = "message:new";
const EVENT_MESSAGE_READ = "message:read";
