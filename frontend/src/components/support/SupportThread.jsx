import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { CONNECTED, CONNECTING, FAILED, PENDING, RECONNECTING } from "../../hooks/useSupportThread";
import { formatDate, formatDateTime } from "../../utils/format";
import { Button, Spinner } from "../ui";

/**
 * Khung hội thoại dùng chung cho cả hai phía.
 *
 * <h3>Vì sao một thành phần, không phải hai</h3>
 * Trang hỗ trợ của người đọc và màn hình trả lời của quản trị viên vẽ ra đúng
 * một thứ: một danh sách bong bóng, một ô soạn tin, một dòng trạng thái kết
 * nối. Cái khác nhau — gọi API nào, tin nào là "của tôi", ai được gửi — đã được
 * quyết xong ở {@code useSupportThread}, nên phần còn lại không có nhánh nào.
 *
 * <h3>Ba việc khiến một luồng chat đọc được thay vì chỉ hiện ra</h3>
 * Cả ba đều là chuyện gom nhóm, và cả ba đều nằm ở {@link #groupMessages}:
 *
 * <pre>
 *   tên người gửi  → chỉ ở tin ĐẦU của một chuỗi liên tiếp
 *   giờ            → chỉ ở tin CUỐI của chuỗi ấy
 *   "đã xem"       → chỉ ở tin cuối cùng mà bên kia đã đọc
 * </pre>
 *
 * Không gom thì mỗi bong bóng mang đủ ba thứ, và năm câu gửi liền nhau thành
 * năm khối chữ giống hệt nhau xen giữa mười dòng chú thích — thứ khiến một
 * đoạn hội thoại ngắn trông vừa dài vừa lộn xộn.
 *
 * <h3>Nội dung được dựng bằng text node, không bao giờ bằng HTML</h3>
 * Đó là hàng rào chống XSS thật của tính năng này — không phải việc lọc ký tự ở
 * máy chủ, thứ nhằm vào một chuyện khác. React thoát ký tự mặc định, và không
 * có {@code dangerouslySetInnerHTML} ở bất kỳ đâu trong nhánh này. Xuống dòng
 * được giữ bằng CSS ({@code white-space: pre-wrap}), không bằng cách đổi
 * {@code \n} thành {@code <br>}.
 */
export default function SupportThread({
  thread,
  mySide = "USER",
  emptyHint = "Chưa có tin nhắn nào. Hãy mô tả vấn đề bạn gặp phải.",
  composerPlaceholder = "Nhập tin nhắn…",
  canSend = true,
  disabledNotice = null,
}) {
  const {
    connection, conversation, messages, hasMore, loading, loadingOlder,
    error, limits, send, retry, loadOlder, markRead,
  } = thread;

  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);

  const scrollRef = useRef(null);
  const bottomRef = useRef(null);
  const inputRef = useRef(null);

  const rows = useMemo(
    () => groupMessages(messages, mySide, conversation?.peerLastReadMessageId ?? 0),
    [messages, mySide, conversation?.peerLastReadMessageId],
  );

  /*
   * Bám đáy, nhưng chỉ khi người ta đang ở đáy.
   *
   * Cuộn xuống vô điều kiện là cách chắc chắn nhất để làm phiền một người đang
   * đọc lại đoạn trước: một tin mới tới và màn hình nhảy đi. `stuckToBottom`
   * được đo NGAY TRƯỚC khi trình duyệt vẽ (useLayoutEffect), vì sau khi vẽ thì
   * chiều cao đã đổi và câu trả lời không còn đúng nữa.
   */
  const stuckToBottom = useRef(true);

  useLayoutEffect(() => {
    const box = scrollRef.current;
    if (!box) return;
    const distance = box.scrollHeight - box.scrollTop - box.clientHeight;
    stuckToBottom.current = distance < 120;
  }, [messages.length]);

  useEffect(() => {
    if (stuckToBottom.current) {
      bottomRef.current?.scrollIntoView({ block: "end" });
    }
  }, [messages]);

  /*
   * Đánh dấu đã đọc khi tab đang hiện.
   *
   * Điều kiện `visibilityState` không thừa: một tab mở sau lưng vẫn nhận khung
   * tin và vẫn dựng lại danh sách, và báo "đã đọc" cho một màn hình không ai
   * nhìn là làm đúng cái mà đặc tả cấm — coi việc nhận được là việc đã đọc.
   */
  useEffect(() => {
    if (typeof document !== "undefined" && document.visibilityState !== "visible") return;
    markRead();
  }, [messages, markRead]);

  useEffect(() => {
    if (typeof document === "undefined") return undefined;
    const onVisible = () => {
      if (document.visibilityState === "visible") markRead();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [markRead]);

  /*
   * Ô soạn tin cao theo số dòng đang gõ, tới một trần.
   *
   * Chiều cao cố định thì một câu dài cuộn bên trong hai dòng — người ta không
   * đọc lại được thứ mình vừa viết. Không có trần thì một tin dài đẩy cả khung
   * hội thoại ra khỏi màn hình.
   */
  useLayoutEffect(() => {
    const box = inputRef.current;
    if (!box) return;
    box.style.height = "auto";
    box.style.height = `${Math.min(box.scrollHeight, 180)}px`;
  }, [draft]);

  const max = limits.maxMessageLength;
  const tooLong = draft.length > max;
  const canSubmit = canSend && draft.trim().length > 0 && !tooLong && !sending;

  const submit = async (event) => {
    event?.preventDefault();
    if (!canSubmit) return;

    setSending(true);
    const text = draft;
    // Xóa ô ngay: bong bóng lạc quan đã hiện ra, và giữ lại chữ trong ô sẽ
    // khiến người ta tưởng tin chưa gửi và bấm lần nữa.
    setDraft("");
    await send(text);
    setSending(false);
    // Không trả chữ về ô khi hỏng: tin thất bại vẫn nằm trên màn hình kèm nút
    // thử lại, và thử lại ở đó giữ nguyên clientMessageId — thứ khiến nó không
    // bao giờ thành hai tin. Trả chữ về ô sẽ mời gọi đúng điều ngược lại.
  };

  return (
    <div className="support-thread">
      <ConnectionBanner connection={connection} error={error} />

      <div className="support-scroll scroll-area" ref={scrollRef}>
        {hasMore && (
          <div className="support-more">
            <Button variant="ghost" size="sm" onClick={loadOlder} disabled={loadingOlder}>
              {loadingOlder ? "Đang tải…" : "Xem tin cũ hơn"}
            </Button>
          </div>
        )}

        {loading && messages.length === 0 && <Spinner label="Đang tải cuộc trò chuyện…" />}

        {!loading && messages.length === 0 && (
          <p className="support-empty">{emptyHint}</p>
        )}

        {rows.map(({ kind, key, label, ...row }) => (
          // `key` được tách ra khỏi phần spread chứ không đi cùng nó: React
          // cảnh báo khi một object chứa `key` bị trải vào JSX, vì `key` không
          // phải một prop mà là thứ React tự giữ.
          kind === "day"
            ? <DaySeparator key={key} label={label} />
            : <MessageBubble key={key} {...row} onRetry={retry} />
        ))}

        <div ref={bottomRef} />
      </div>

      {disabledNotice && <p className="support-notice">{disabledNotice}</p>}

      <form className="support-composer" onSubmit={submit}>
        <textarea
          ref={inputRef}
          className="support-input"
          rows={1}
          value={draft}
          placeholder={canSend ? composerPlaceholder : "Không thể gửi tin trong cuộc trò chuyện này."}
          disabled={!canSend}
          aria-label="Nội dung tin nhắn"
          aria-invalid={tooLong || undefined}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            // Enter gửi, Shift+Enter xuống dòng — nếp của mọi ứng dụng nhắn tin.
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              submit(event);
            }
          }}
        />

        <div className="support-composer-foot">
          {/* Bộ đếm chỉ hiện khi nó bắt đầu có nghĩa. Một con số chạy suốt từ
              ký tự đầu tiên là một con số không ai đọc, và nó lấy mất sự chú ý
              đúng vào lúc nó cần được chú ý. */}
          <span className={`support-count ${tooLong ? "over" : ""}`}>
            {draft.length > max * 0.8 ? `${draft.length}/${max}` : ""}
          </span>
          <span className="support-hint">Enter để gửi · Shift+Enter xuống dòng</span>
          <Button type="submit" size="sm" disabled={!canSubmit}>Gửi</Button>
        </div>
      </form>
    </div>
  );
}

/**
 * Trạng thái đường truyền, nói ra chỉ khi nó không bình thường.
 *
 * Một dải băng "đã kết nối" thường trực chỉ chiếm chỗ; thứ người dùng cần biết
 * là lúc đường <i>không</i> thông, vì khi ấy tin của bên kia sẽ tới muộn.
 */
function ConnectionBanner({ connection, error }) {
  if (error) {
    return <p className="support-banner warn">{error}</p>;
  }
  if (connection === CONNECTED || connection === CONNECTING) {
    return null;
  }
  if (connection === RECONNECTING) {
    return (
      <p className="support-banner">
        Mất kết nối tức thời — đang kết nối lại. Tin nhắn của bạn vẫn gửi được.
      </p>
    );
  }
  return null;
}

/** Vạch ngăn ngày, để một luồng dài đọc được mà không phải rê chuột lên từng mốc. */
function DaySeparator({ label }) {
  return (
    <div className="support-day">
      <span>{label}</span>
    </div>
  );
}

/**
 * Một bong bóng.
 *
 * {@code mine} quyết định bên nào, và nó được tính từ {@code senderRole} — thứ
 * đến từ máy chủ. Trình duyệt không tự suy ra từ id người gửi, vốn có thể vắng
 * mặt: câu của quản trị viên hiện ra với người đọc là ẩn danh, không kèm id.
 * Xem {@code SupportMessageDto}.
 */
function MessageBubble({ message, mine, showName, showTime, showSeen, onRetry }) {
  if (message.type === "SYSTEM") {
    return (
      <div className="support-system">
        <span>{message.content}</span>
      </div>
    );
  }

  const failed = message.status === FAILED;

  return (
    <div className={`support-row ${mine ? "mine" : "theirs"} ${showTime ? "run-end" : ""}`}>
      {showName && <span className="support-sender">{message.senderName ?? "—"}</span>}

      <div className={`support-bubble ${mine ? "mine" : "theirs"} ${failed ? "failed" : ""}`}>
        {/* Text node, không phải HTML. Xuống dòng do CSS giữ. */}
        {message.content}
      </div>

      {(showTime || message.status === PENDING || failed) && (
        <span className="support-meta">
          {message.status === PENDING && <span className="support-state">Đang gửi…</span>}

          {failed && (
            <>
              <span className="support-state failed">
                {message.error ?? "Chưa gửi được"}
              </span>
              <button
                type="button"
                className="support-retry"
                onClick={() => onRetry(message.clientMessageId)}
              >
                Gửi lại
              </button>
            </>
          )}

          {!failed && message.status !== PENDING && showTime && (
            <time dateTime={message.createdAt} title={formatDateTime(message.createdAt)}>
              {clockTime(message.createdAt)}
            </time>
          )}

          {showSeen && <span className="support-state">Đã xem</span>}
        </span>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Gom nhóm                                                            */
/* ------------------------------------------------------------------ */

/**
 * Biến một danh sách tin phẳng thành những dòng sẵn sàng để vẽ.
 *
 * <h3>Vì sao việc này nằm ở đây chứ không trong lúc vẽ</h3>
 * Vì mỗi quyết định cần nhìn cả tin <i>trước</i> lẫn tin <i>sau</i>: tên hiện
 * khi người gửi đổi, giờ hiện khi người gửi sắp đổi, vạch ngày hiện khi ngày
 * đổi. Hỏi những câu ấy từ bên trong vòng lặp vẽ sẽ là ba lần dò tới lui trong
 * mảng cho mỗi bong bóng.
 *
 * <p>Một chuỗi bị cắt khi đổi người gửi, đổi ngày, hoặc khi hai tin cách nhau
 * quá {@link #RUN_GAP_MS}. Vế cuối là thứ giữ cho hai câu cách nhau nửa tiếng
 * không dính vào nhau thành một khối trông như gửi liền một lúc.
 *
 * @param peerReadMark mốc đã đọc của bên kia; dùng để đặt "Đã xem" vào đúng
 *                     <i>một</i> chỗ — tin cuối cùng của mình mà họ đã đọc
 */
function groupMessages(messages, mySide, peerReadMark) {
  const rows = [];
  let lastDay = null;

  // Tin cuối cùng của mình đã được bên kia đọc. Tính trước một lượt, thay vì
  // hỏi lại ở từng bong bóng — và quan trọng hơn: nó phải là MỘT chỗ duy nhất,
  // nên phải biết cái cuối cùng là cái nào trước khi vẽ bất cứ cái gì.
  let seenAnchor = null;
  for (const message of messages) {
    if (message.senderRole === mySide && message.id != null
        && message.type !== "SYSTEM" && message.id <= peerReadMark) {
      seenAnchor = message.id;
    }
  }

  for (let i = 0; i < messages.length; i++) {
    const message = messages[i];
    const day = dayKey(message.createdAt);

    if (day && day !== lastDay) {
      rows.push({ kind: "day", key: `day-${day}`, label: dayLabel(message.createdAt) });
      lastDay = day;
    }

    if (message.type === "SYSTEM") {
      rows.push({ kind: "message", key: rowKey(message), message, mine: false });
      continue;
    }

    const mine = message.senderRole === mySide;
    const previous = previousChat(messages, i);
    const next = nextChat(messages, i);

    rows.push({
      kind: "message",
      key: rowKey(message),
      message,
      mine,
      showName: !continues(previous, message),
      showTime: !continues(message, next),
      showSeen: mine && message.id != null && message.id === seenAnchor,
    });
  }

  return rows;
}

/** Quãng lặng đủ dài để hai tin không còn là một mạch nói. */
const RUN_GAP_MS = 5 * 60 * 1000;

/** `b` có tiếp nối `a` không: cùng người, cùng ngày, và không cách nhau quá lâu. */
function continues(a, b) {
  if (!a || !b) return false;
  if (a.senderRole !== b.senderRole) return false;
  if (dayKey(a.createdAt) !== dayKey(b.createdAt)) return false;

  const gap = new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  return Number.isFinite(gap) && gap < RUN_GAP_MS;
}

/* Tin hệ thống đứng giữa và không thuộc về bên nào, nên nó không được phép cắt
   một chuỗi làm đôi — nó bị bỏ qua khi đi tìm hàng xóm. */
function previousChat(messages, index) {
  for (let i = index - 1; i >= 0; i--) {
    if (messages[i].type !== "SYSTEM") return messages[i];
  }
  return null;
}

function nextChat(messages, index) {
  for (let i = index + 1; i < messages.length; i++) {
    if (messages[i].type !== "SYSTEM") return messages[i];
  }
  return null;
}

function rowKey(message) {
  return message.id != null ? `m-${message.id}` : `c-${message.clientMessageId}`;
}

function dayKey(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toDateString();
}

/** "Hôm nay", "Hôm qua", hoặc ngày tháng đầy đủ. */
function dayLabel(value) {
  const date = new Date(value);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (date.toDateString() === today.toDateString()) return "Hôm nay";
  if (date.toDateString() === yesterday.toDateString()) return "Hôm qua";
  return formatDate(value);
}

/**
 * Chỉ giờ và phút.
 *
 * Trong một luồng đã chia theo ngày thì ngày tháng là thông tin thừa; giờ mới
 * là thứ trả lời "câu này cách câu kia bao lâu". Mốc đầy đủ vẫn còn ở
 * {@code title}, cho lần hiếm hoi ai đó cần tới nó.
 */
function clockTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}
