import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useNotifications } from "../context/notification-context";
import { iconFor, primaryAction, secondaryAction } from "../utils/notificationRoutes";
import { relativeTime } from "../utils/format";

const BellIcon = ({ hasUnread }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M18 8.4a6 6 0 1 0-12 0c0 5.2-1.8 6.6-1.8 6.6h15.6S18 13.6 18 8.4" />
    {/* Cái quai chỉ đung đưa khi có tin: một cái chuông tĩnh và một cái chuông
        đang rung là hai hình khác nhau, và mắt bắt được sự khác nhau ấy trước
        khi kịp đọc con số. */}
    <path d={hasUnread ? "M13.7 19a2 2 0 0 1-3.4 0" : "M10.3 19a2 2 0 0 0 3.4 0"} />
  </svg>
);

/**
 * Cái chuông trong thanh điều hướng, và hộp thư sau nó.
 *
 * <h3>Vì sao nó đứng cạnh ảnh đại diện chứ không nằm trong menu tài khoản</h3>
 * Menu tài khoản trả lời "tôi làm gì được"; cái chuông trả lời "có gì mới
 * không". Câu thứ hai phải trả lời được <i>mà không cần bấm gì</i> — đó là toàn
 * bộ lý do tồn tại của một con số nhỏ trên thanh điều hướng. Nhét nó vào trong
 * menu là biến một câu trả lời tức thì thành một câu phải đi tìm.
 *
 * <h3>Cùng bộ khung với menu tài khoản</h3>
 * Cùng lớp `user-menu-panel`, cùng cách đóng (bấm ra ngoài, phím Esc, trả tiêu
 * điểm về nút), cùng hoạt ảnh rơi xuống. Đây là cái hộp thứ ba của thanh này —
 * sau menu thể loại và menu tài khoản — và ba cái hộp mở ra ba kiểu khác nhau
 * sẽ khiến thanh điều hướng trông như ba trang web ghép lại.
 *
 * <h3>Trên màn hình hẹp</h3>
 * Tấm bảng bỏ neo ở góc phải và trải ra gần hết bề rộng, kèm hạn chiều cao theo
 * viewport — xem `components.css`. Một dropdown 24rem neo phải sẽ tràn ra ngoài
 * màn hình điện thoại, và phần tràn ấy đúng là chỗ đặt các nút.
 */
export default function NotificationBell({ onNavigate }) {
  const { unread, latest, loading, error, markRead, markAllRead, refresh } = useNotifications();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);

  // Mở ra là hỏi lại một lần. Con số trên chuông vốn đã đúng nhờ luồng đẩy,
  // nhưng danh sách bên dưới có thể đã cũ nếu tab này vừa ngủ dậy.
  useEffect(() => {
    if (open) refresh();
  }, [open, refresh]);

  useEffect(() => {
    if (!open) return undefined;

    const onPointerDown = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    };
    const onKeyDown = (event) => {
      if (event.key !== "Escape") return;
      setOpen(false);
      triggerRef.current?.focus();
    };

    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  const close = () => {
    setOpen(false);
    onNavigate?.();
  };

  // Trên 99 thì con số thôi không còn là thông tin nữa, chỉ là một cái nhãn dài
  // làm méo cái chấm tròn.
  const badge = unread > 99 ? "99+" : String(unread);

  return (
    <div className="notif" ref={containerRef}>
      <button
        ref={triggerRef}
        type="button"
        className={`notif-trigger ${open ? "open" : ""}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
        onClick={() => setOpen((value) => !value)}
      >
        <BellIcon hasUnread={unread > 0} />
        {unread > 0 && (
          /* aria-hidden vì aria-label của nút đã nói con số thành câu; để cả
             hai thì trình đọc màn hình đọc số hai lần. */
          <span className="notif-badge tabular-num" aria-hidden="true">
            {badge}
          </span>
        )}
      </button>

      {open && (
        <div className="user-menu-panel notif-panel" role="dialog" aria-label="Thông báo">
          <div className="notif-head">
            <strong>Thông báo</strong>
            {unread > 0 && (
              <button type="button" className="notif-mark-all" onClick={markAllRead}>
                Đánh dấu đã đọc
              </button>
            )}
          </div>

          <div className="notif-list scroll-area">
            {error && <p className="notif-empty">{error}</p>}

            {!error && latest.length === 0 && (
              <p className="notif-empty">
                {loading ? "Đang tải…" : "Chưa có thông báo nào."}
              </p>
            )}

            {latest.map((notification) => (
              <NotificationRow
                key={notification.id}
                notification={notification}
                onOpen={markRead}
                onNavigate={close}
              />
            ))}
          </div>

          <Link to="/tai-khoan?tab=notifications" className="notif-all" onClick={close}>
            Xem tất cả thông báo
          </Link>
        </div>
      )}
    </div>
  );
}

/**
 * Một dòng trong hộp thư.
 *
 * <h3>Chữ, không phải HTML</h3>
 * `title` và `message` đi thẳng vào nội dung một thẻ, nên React tự thoát ký tự.
 * Không có `dangerouslySetInnerHTML` ở đâu trong tính năng này, và đó là điều
 * kiện chứ không phải sở thích: nội dung có thể do quản trị viên gõ.
 *
 * <h3>Đích đến do bảng tra dựng, không do máy chủ gửi</h3>
 * Xem `notificationRoutes.js`. Chỗ này chỉ nhận về một đường dẫn đã được ghép từ
 * hằng cộng một số, nên không có chuỗi nào từ cơ sở dữ liệu trở thành `to`.
 *
 * <h3>Bấm vào là đã đọc, kể cả khi không đi đâu</h3>
 * Một thông báo không có nút nào — tin chung của quản trị viên — vẫn phải đánh
 * dấu được. Nên chính cái dòng là nút, và hai liên kết bên trong chỉ chồng thêm
 * đích đến lên trên nó.
 */
function NotificationRow({ notification, onOpen, onNavigate }) {
  const primary = primaryAction(notification);
  const secondary = secondaryAction(notification);

  const read = Boolean(notification.read);

  const handleOpen = () => {
    if (!read) onOpen(notification.id);
  };

  return (
    <article className={`notif-item ${read ? "" : "unread"}`}>
      <button
        type="button"
        className="notif-item-body"
        onClick={handleOpen}
        aria-label={read ? notification.title : `${notification.title} (chưa đọc)`}
      >
        <span className="notif-item-icon" aria-hidden="true">
          {iconFor(notification.type)}
        </span>

        <span className="notif-item-text">
          <span className="notif-item-title">{notification.title}</span>
          <span className="notif-item-message">{notification.message}</span>
          <span className="notif-item-time">{relativeTime(notification.createdAt)}</span>
        </span>

        {!read && <span className="notif-item-dot" aria-hidden="true" />}
      </button>

      {(primary || secondary) && (
        <div className="notif-item-actions">
          {primary && (
            <Link
              to={primary.to}
              className="notif-item-action"
              onClick={() => {
                handleOpen();
                onNavigate();
              }}
            >
              {primary.label}
            </Link>
          )}
          {secondary && (
            <Link
              to={secondary.to}
              className="notif-item-action"
              onClick={() => {
                handleOpen();
                onNavigate();
              }}
            >
              {secondary.label}
            </Link>
          )}
        </div>
      )}
    </article>
  );
}

export { NotificationRow };
