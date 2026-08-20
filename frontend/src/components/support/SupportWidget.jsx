import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { supportApi } from "../../api/endpoints";
import { useAuth } from "../../context/auth-context";
import { useSupportSocket } from "../../context/support-socket-context";
import { useSupportThread } from "../../hooks/useSupportThread";
import SupportThread from "./SupportThread";

/**
 * Bong bóng chat ở góc dưới bên phải, và hộp thoại mở ra từ nó.
 *
 * <h3>Vì sao là một bong bóng chứ không phải một trang</h3>
 * Vì người ta cần hỏi hỗ trợ <i>trong lúc</i> đang làm việc khác — đang đọc một
 * chương, đang xem bảng giá, đang dở một lần thanh toán. Một trang riêng bắt họ
 * rời khỏi chỗ đang đứng, và câu hỏi của họ thường là <i>về</i> chỗ ấy. Bong
 * bóng thì đi theo, và câu trả lời tới nơi mà không cần ai mở gì cả.
 *
 * <h3>Nó không tạo luồng khi chỉ đứng đó</h3>
 * Điểm này quan trọng hơn vẻ ngoài của nó. Bong bóng có mặt trên mọi trang của
 * mọi người đã đăng nhập, nên nếu nó gọi đường "lấy hoặc tạo luồng" để biết số
 * chưa đọc thì mỗi người mở trang chủ là một hàng mới trong cơ sở dữ liệu —
 * hàng nghìn luồng rỗng của những người chưa bao giờ định liên hệ.
 *
 * <p>Nên nó gọi {@code GET /api/support/summary}, đường chỉ đọc. Khung chat —
 * thứ thật sự tạo luồng — chỉ được dựng khi hộp thoại đã mở lần đầu.
 *
 * <h3>Vì sao không vẽ cho quản trị viên</h3>
 * Phía hỗ trợ là một phía <i>chung</i>, không phải một người, nên tài khoản
 * quản trị không có luồng của riêng nó — máy chủ từ chối tạo, xem
 * {@code SUPPORT_NOT_FOR_ADMIN}. Chỗ họ đọc tin hỗ trợ là hộp thư chung trong
 * bảng quản trị.
 */
export default function SupportWidget() {
  const { user } = useAuth();
  const socket = useSupportSocket();
  const { pathname } = useLocation();

  const [open, setOpen] = useState(false);

  /*
   * Đã từng mở lần nào chưa.
   *
   * Khung chat chỉ được dựng sau lần mở đầu tiên, và một khi đã dựng thì ở lại
   * — đóng hộp thoại không tháo nó. Hai lý do: mở lại không phải chờ tải lần
   * nữa, và tin nhắn vẫn tới trong lúc hộp thoại đóng nên số chưa đọc trên
   * huy hiệu là số của máy chủ chứ không phải một phép đoán.
   */
  const [everOpened, setEverOpened] = useState(false);

  /** Số chưa đọc trước lần mở đầu tiên. Sau đó khung chat là nguồn chính xác hơn. */
  const [summary, setSummary] = useState(null);

  const panelRef = useRef(null);
  const launcherRef = useRef(null);

  const isAdmin = user?.role === "ADMIN";

  /*
   * Trang đọc chương đã có một bong bóng ở đúng góc này: hộp trợ lý AI, xem
   * assistant.css. Hai cái chồng lên nhau thì cả hai cùng khó bấm, và cái ở
   * dưới thì gần như không thấy.
   *
   * Nhường chỗ chứ không xếp chồng lên nhau theo chiều dọc: trợ lý neo vào
   * .reader-grid còn cái này neo vào khung nhìn, nên vị trí tương đối giữa
   * chúng đổi theo bề rộng màn hình — một chồng "đủ cao để không đè" hôm nay sẽ
   * đè vào ngày ai đó sửa bố cục trang đọc.
   *
   * Đánh đổi: đang đọc dở một chương thì không gọi được hỗ trợ ngay tại đó.
   * Chấp nhận được, vì trợ lý AI ở ngay đấy trả lời được phần lớn câu hỏi về
   * chính chương ấy, còn hỗ trợ thì với được từ mọi trang khác.
   */
  const onReaderPage = pathname.startsWith("/chuong/");

  const enabled = Boolean(user) && !isAdmin && !onReaderPage;

  const thread = useSupportThread({ mode: "user", enabled: enabled && everOpened });

  /* ---------------------------------------------------------------- */
  /* Số chưa đọc khi hộp thoại đang đóng                               */
  /* ---------------------------------------------------------------- */

  const refreshSummary = useCallback(async () => {
    if (!enabled) return;
    try {
      setSummary(await supportApi.summary());
    } catch {
      // Huy hiệu là thứ trang trí. Không có nó thì bong bóng vẫn bấm được, và
      // lượt sau sẽ lấy lại được con số.
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      setSummary(null);
      setOpen(false);
      return;
    }
    refreshSummary();
  }, [enabled, refreshSummary]);

  /*
   * Một tin mới của phía hỗ trợ trong lúc hộp thoại đang đóng.
   *
   * Đây là lý do bong bóng phải nghe socket chứ không chỉ hỏi một lần lúc tải
   * trang: nếu không, người dùng chỉ biết có trả lời khi họ tình cờ mở lại
   * trang — và đó đúng là thứ mà cả tính năng thời gian thực này sinh ra để
   * tránh.
   */
  useEffect(() => {
    if (!enabled || !socket) return undefined;

    return socket.subscribe((frame) => {
      if (frame.type !== "message:new") return;
      const message = frame.payload?.message;
      if (!message || message.senderRole !== "ADMIN") return;

      // Hộp thoại đang mở thì khung chat đã lo phần hiển thị và phần đánh dấu
      // đã đọc; hỏi lại số chưa đọc lúc này chỉ chồng lên nhau.
      if (!open) refreshSummary();
    });
  }, [enabled, socket, open, refreshSummary]);

  /* ---------------------------------------------------------------- */
  /* Mở, đóng, phím Esc                                                */
  /* ---------------------------------------------------------------- */

  const openPanel = useCallback(() => {
    setEverOpened(true);
    setOpen(true);
  }, []);

  const closePanel = useCallback(() => {
    setOpen(false);
    // Trả tiêu điểm về nút đã mở nó, để người dùng bàn phím không bị bỏ rơi ở
    // đầu tài liệu. Cùng cách với menu tài khoản và cái chuông thông báo.
    launcherRef.current?.focus();
    // Số chưa đọc vừa đổi vì chính việc mở ra đã đánh dấu đã đọc.
    refreshSummary();
  }, [refreshSummary]);

  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event) => {
      if (event.key === "Escape") closePanel();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, closePanel]);

  /*
   * Cố ý KHÔNG đóng khi bấm ra ngoài.
   *
   * Khác menu tài khoản và cái chuông: hai cái đó là danh sách để liếc rồi đi,
   * còn đây là một ô soạn thảo. Bấm nhầm ra ngoài mà mất đoạn vừa gõ là mất
   * công của người dùng — thứ mà một cái menu không có gì để mất.
   */

  if (!enabled) return null;

  // Trước lần mở đầu tiên thì con số đến từ đường tóm tắt; sau đó khung chat
  // biết rõ hơn, vì nó nhận khung tin trực tiếp.
  const unread = everOpened
    ? (thread.conversation?.unread ?? 0)
    : (summary?.unread ?? 0);

  const blocked = (everOpened ? thread.conversation?.status : summary?.status) === "BLOCKED";

  return (
    <div className={`support-widget ${open ? "open" : ""}`}>
      {open && (
        <section
          className="support-panel"
          ref={panelRef}
          role="dialog"
          aria-label="Hỗ trợ"
        >
          <header className="support-panel-head">
            <span className="support-panel-title">Hỗ trợ</span>
            <ConnectionDot live={thread.live} />
            <button
              type="button"
              className="support-panel-close"
              onClick={closePanel}
              aria-label="Đóng hộp thoại hỗ trợ"
            >
              <CloseIcon />
            </button>
          </header>

          <SupportThread
            thread={thread}
            mySide="USER"
            canSend={!blocked}
            emptyHint="Chào bạn! Hãy mô tả vấn đề bạn gặp phải — càng cụ thể càng nhanh được giúp."
            composerPlaceholder="Nhập tin nhắn…"
            disabledNotice={blocked
              ? "Quản trị viên đã khóa cuộc trò chuyện này. Bạn vẫn xem được lịch sử."
              : null}
          />
        </section>
      )}

      <button
        type="button"
        ref={launcherRef}
        className="support-launcher"
        onClick={() => (open ? closePanel() : openPanel())}
        aria-expanded={open}
        aria-label={unread > 0 ? `Hỗ trợ, ${unread} tin chưa đọc` : "Hỗ trợ"}
      >
        {open ? <ChevronDownIcon /> : <ChatIcon />}

        {/* Huy hiệu chỉ có nghĩa khi hộp thoại đang đóng: mở ra thì tin đã ở
            ngay trước mắt, và một con số đỏ trên cái nút đang mở nói rằng có
            thứ chưa đọc ở chỗ người ta đang nhìn. */}
        {!open && unread > 0 && (
          <span className="support-launcher-badge" aria-hidden="true">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>
    </div>
  );
}

/**
 * Chấm trạng thái đường truyền.
 *
 * Không có chữ đi kèm: ở một hộp thoại rộng hai mươi rem thì một dòng "đang kết
 * nối lại" chiếm mất chỗ của nội dung, và dải băng bên trong khung chat đã nói
 * điều đó khi nó thật sự quan trọng. Cái chấm chỉ để liếc.
 */
function ConnectionDot({ live }) {
  return (
    <span
      className={`support-dot ${live ? "live" : ""}`}
      title={live ? "Đang kết nối trực tiếp" : "Mất kết nối tức thời — đang kết nối lại"}
      aria-hidden="true"
    />
  );
}

const ChatIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.9}
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M20 12.5a7 7 0 0 1-7 7H8.6L4.5 22v-4.3A7 7 0 0 1 3.5 13V12a7 7 0 0 1 7-7h2.5a7 7 0 0 1 7 7z" />
    <path d="M8.5 11.5h7" />
    <path d="M8.5 14.5h4" />
  </svg>
);

const ChevronDownIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2}
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M6 9.5l6 6 6-6" />
  </svg>
);

const CloseIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
       strokeLinecap="round" aria-hidden="true">
    <path d="M6 6l12 12M18 6L6 18" />
  </svg>
);
