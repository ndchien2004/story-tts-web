import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import Avatar from "./Avatar";

const icon = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

const PersonIcon = () => (
  <svg {...icon}>
    <circle cx="12" cy="8" r="3.6" />
    <path d="M4.8 20a7.2 7.2 0 0 1 14.4 0" />
  </svg>
);

const ShelfIcon = () => (
  <svg {...icon}>
    <path d="M4 4.5h6a2 2 0 0 1 2 2V20a2.4 2.4 0 0 0-2-1.4H4z" />
    <path d="M20 4.5h-6a2 2 0 0 0-2 2V20a2.4 2.4 0 0 1 2-1.4h6z" />
  </svg>
);

const CrownIcon = () => (
  <svg {...icon}>
    <path d="M4 17.5h16M4.5 7.2l3.6 3.1L12 5.5l3.9 4.8 3.6-3.1-1.4 8.3H5.9z" />
  </svg>
);

const ShieldIcon = () => (
  <svg {...icon}>
    <path d="M12 3.5 5 6.2v5.1c0 4 2.9 7.6 7 9.2 4.1-1.6 7-5.2 7-9.2V6.2z" />
  </svg>
);

const ConsoleIcon = () => (
  <svg {...icon}>
    <rect x="3.2" y="4.5" width="17.6" height="15" rx="2" />
    <path d="M3.2 9.2h17.6M7.5 13h4" />
  </svg>
);

const LogoutIcon = () => (
  <svg {...icon}>
    <path d="M14.5 8V5.8a1.8 1.8 0 0 0-1.8-1.8H6.3A1.8 1.8 0 0 0 4.5 5.8v12.4A1.8 1.8 0 0 0 6.3 20h6.4a1.8 1.8 0 0 0 1.8-1.8V16" />
    <path d="M10 12h9.5m0 0-2.8-2.8M19.5 12l-2.8 2.8" />
  </svg>
);

/**
 * The account button in the navbar, and the menu behind it.
 *
 * Everything tied to *this reader* rather than to the catalogue lives in here —
 * their profile, their shelf, the console, signing out. Spread across the bar
 * those four competed with "Trang chủ" and "Danh sách truyện" for the same
 * attention; behind one avatar the bar reads as two things: what there is to
 * read, and you.
 *
 * Dismissal follows the usual menu contract: a click anywhere outside, or
 * Escape, which also returns focus to the button that opened it.
 */
export default function UserMenu({ onNavigate }) {
  const { user, isAdmin, isVip, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const navigate = useNavigate();

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

  const handleLogout = () => {
    close();
    logout();
    navigate("/");
  };

  const name = user.displayName || user.username;

  return (
    <div className="user-menu" ref={containerRef}>
      <button
        ref={triggerRef}
        type="button"
        className={`user-menu-trigger ${open ? "open" : ""}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Tài khoản của ${name}`}
        onClick={() => setOpen((value) => !value)}
      >
        {/* The rank sits on the avatar itself, in the bar — it is a standing
            fact about the account, and inside the menu it was only visible to
            someone who had already opened it. */}
        <span className="user-menu-avatar">
          <Avatar src={user.avatarUrl} name={name} size="sm" />
          {isAdmin && (
            <span className="user-menu-rank" title="Quản trị viên" aria-hidden="true">
              <ShieldIcon />
            </span>
          )}
          {!isAdmin && isVip && (
            <span className="user-menu-rank" title="Thành viên VIP" aria-hidden="true">
              <CrownIcon />
            </span>
          )}
        </span>

        <span className="user-menu-name">{name}</span>
        <svg {...icon} className="user-menu-caret">
          <path d="m6 9.5 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className="user-menu-panel" role="menu">
          <div className="user-menu-head">
            <Avatar src={user.avatarUrl} name={name} size="md" />
            <div className="user-menu-identity">
              <strong>{name}</strong>

              {/* The rank rides on the handle rather than taking a row of its
                  own under it. It is one short word about the account, the
                  same kind of thing the handle is, and a full-size badge on a
                  line by itself made the menu open two lines taller to say
                  it. */}
              <span className="user-menu-handle">
                <span className="muted">@{user.username}</span>
                {isAdmin && <span className="user-menu-tag">Admin</span>}
                {!isAdmin && isVip && <span className="user-menu-tag user-menu-tag-vip">VIP</span>}
              </span>
            </div>
          </div>

          <Link to="/tai-khoan" className="user-menu-item" role="menuitem" onClick={close}>
            <PersonIcon />
            Hồ sơ của tôi
          </Link>

          <Link to="/tu-truyen" className="user-menu-item" role="menuitem" onClick={close}>
            <ShelfIcon />
            Tủ truyện
          </Link>

          {/* Hidden from admins and from anyone already VIP: neither has
              anything to buy. */}
          {!isAdmin && !isVip && (
            <Link to="/nang-cap" className="user-menu-item" role="menuitem" onClick={close}>
              <CrownIcon />
              Nâng cấp VIP
            </Link>
          )}

          {isAdmin && (
            <Link to="/admin" className="user-menu-item" role="menuitem" onClick={close}>
              <ConsoleIcon />
              Bảng quản trị
            </Link>
          )}

          <button
            type="button"
            className="user-menu-item user-menu-item-danger"
            role="menuitem"
            onClick={handleLogout}
          >
            <LogoutIcon />
            Đăng xuất
          </button>
        </div>
      )}
    </div>
  );
}
