import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LOGO_MARK } from "../../brand";
import { AdminShellContext } from "../../context/admin-shell-context";
import { useAuth } from "../../context/auth-context";
import { Button } from "../../components/ui";

/* ------------------------------------------------------------------ */
/* Icons                                                               */
/* ------------------------------------------------------------------ */

const iconProps = {
  className: "admin-nav-icon",
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2.2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

function BooksIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H10v16H5.5A1.5 1.5 0 0 1 4 18.5z" />
      <path d="M10 4h8.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H10" />
      <path d="M13.5 8.5h3M13.5 12h3" />
    </svg>
  );
}

function UsersIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19.5a5.5 5.5 0 0 1 11 0" />
      <path d="M16 5.6a3.2 3.2 0 0 1 0 6.3M17.5 14.6a5.5 5.5 0 0 1 3 4.9" />
    </svg>
  );
}

function GaugeIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4.2 17.5a8.5 8.5 0 1 1 15.6 0" />
      <path d="m12 13.8 3.6-3.9" />
      <circle cx="12" cy="14.6" r="1.1" />
    </svg>
  );
}

function CommentIcon() {
  return (
    <svg {...iconProps}>
      <path d="M20 14.5a2.5 2.5 0 0 1-2.5 2.5H9l-4 3.2V6.5A2.5 2.5 0 0 1 7.5 4h10A2.5 2.5 0 0 1 20 6.5z" />
      <path d="M8.6 10.5h6.8M8.6 13.4h4.2" />
    </svg>
  );
}

function CardIcon() {
  return (
    <svg {...iconProps}>
      <rect x="3" y="5.5" width="18" height="13" rx="2.2" />
      <path d="M3 10h18M6.5 14.5h3" />
    </svg>
  );
}

/** Chevrons pointing at the edge the sidebar is about to move towards. */
function CollapseIcon({ collapsed }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      style={collapsed ? { transform: "rotate(180deg)" } : undefined}
    >
      <path d="M14 6 8 12l6 6" />
      <path d="M18.5 6v12" />
    </svg>
  );
}

function TagsIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 11.4V5.5A1.5 1.5 0 0 1 5.5 4h5.9a2 2 0 0 1 1.4.6l6.6 6.6a1.5 1.5 0 0 1 0 2.1l-5.7 5.7a1.5 1.5 0 0 1-2.1 0l-6.6-6.6a2 2 0 0 1-.6-1.4z" />
      <circle cx="8.4" cy="8.4" r="1.3" />
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Navigation                                                          */
/* ------------------------------------------------------------------ */

/**
 * Story pages sit under several paths (`/admin/truyen/…`, `/admin/chuong/…`),
 * so the active tab is decided here rather than by NavLink's own matching:
 * `end` would drop the highlight on every nested screen, and dropping `end`
 * would keep it lit on the members tab.
 */
const NAV_ITEMS = [
  {
    to: "/admin",
    label: "Tổng quan",
    Icon: GaugeIcon,
    isActive: (pathname) => pathname === "/admin" || pathname === "/admin/",
  },
  // Audio has no tab of its own: it belongs to a chapter, and every way of
  // reaching a chapter already runs through here.
  {
    to: "/admin/truyen",
    label: "Truyện, chương & audio",
    Icon: BooksIcon,
    // Chapter screens live under /admin/chuong too, and both belong here.
    isActive: (pathname) =>
      pathname.startsWith("/admin/truyen") || pathname.startsWith("/admin/chuong"),
  },
  {
    to: "/admin/danh-muc",
    label: "Thể loại & tác giả",
    Icon: TagsIcon,
    isActive: (pathname) => pathname.startsWith("/admin/danh-muc"),
  },
  {
    to: "/admin/binh-luan",
    label: "Bình luận",
    Icon: CommentIcon,
    isActive: (pathname) => pathname.startsWith("/admin/binh-luan"),
  },
  {
    to: "/admin/thanh-vien",
    label: "Thành viên",
    Icon: UsersIcon,
    isActive: (pathname) => pathname.startsWith("/admin/thanh-vien"),
  },
  {
    to: "/admin/vip",
    label: "Gói VIP & thanh toán",
    Icon: CardIcon,
    isActive: (pathname) => pathname.startsWith("/admin/vip"),
  },
];

/**
 * Shell for the whole admin console.
 *
 * Deliberately not the reader's layout: the console gets its own sidebar and
 * none of the reading navigation, so managing content and browsing it never
 * share a toolbar.
 */
const COLLAPSED_KEY = "storytts.adminSidebarCollapsed";

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();

  // Only used once the sidebar collapses into a drawer on narrow screens.
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Rail mode on wide screens, remembered: someone who works in the tables
  // wants the width back on every visit, not once per session.
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(COLLAPSED_KEY) === "true",
  );

  useEffect(() => {
    localStorage.setItem(COLLAPSED_KEY, String(collapsed));
  }, [collapsed]);

  // Navigating always means the drawer has served its purpose.
  useEffect(() => setSidebarOpen(false), [pathname]);

  const openSidebar = useCallback(() => setSidebarOpen(true), []);
  const shell = useMemo(() => ({ openSidebar }), [openSidebar]);

  const displayName = user?.displayName || user?.username || "Admin";

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <AdminShellContext.Provider value={shell}>
      <div className={`admin-shell ${collapsed ? "admin-shell-collapsed" : ""}`}>
        {sidebarOpen && (
          <div className="admin-scrim" role="presentation" onClick={() => setSidebarOpen(false)} />
        )}

        <aside className={`admin-sidebar ${sidebarOpen ? "open" : ""}`}>
          <div className="admin-sidebar-head">
            <Link to="/admin" className="admin-brand" title="Truyện Nghe — Bảng quản trị">
              {/* The site's own mark, so the console reads as part of the same
                  product rather than as a separate tool. Collapsed, the mark
                  alone is the whole brand. */}
              <img className="admin-brand-mark" src={LOGO_MARK} alt="" width="40" height="40" />
              <span className="admin-brand-text">
                <span className="admin-brand-name">Truyện Nghe</span>
                <span className="admin-brand-sub">Bảng quản trị</span>
              </span>
            </Link>

            <Button
              className="nb-icon-btn admin-collapse-btn"
              variant="ghost"
              aria-label={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
              title={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
              aria-pressed={collapsed}
              onClick={() => setCollapsed((value) => !value)}
            >
              <CollapseIcon collapsed={collapsed} />
            </Button>
          </div>

          <nav className="admin-nav" aria-label="Điều hướng quản trị">
            <span className="admin-nav-label">Quản lý</span>
            {NAV_ITEMS.map(({ to, label, Icon, isActive }) => (
              <NavLink
                key={to}
                to={to}
                // Collapsed there is no label to read, so the tooltip carries it.
                title={label}
                // A function, not a string. Given a string, NavLink appends its
                // own "active" class on top of ours — and by its reckoning
                // "/admin" matches every path beneath it, so the first tab
                // stayed lit alongside whichever one was really open. Returning
                // the class list from a function leaves the decision entirely to
                // the rules above.
                className={() => `admin-nav-link ${isActive(pathname) ? "active" : ""}`}
              >
                <Icon />
                <span className="admin-nav-text">{label}</span>
              </NavLink>
            ))}
          </nav>

          <div className="admin-sidebar-spacer" />

          {/* The theme switch lives in the top bar, with the other controls
              that belong to the person rather than to the page. */}
          <div className="admin-sidebar-foot">
            <div className="admin-whoami" title={`${displayName} — Quản trị viên`}>
              <span className="admin-whoami-avatar" aria-hidden="true">
                {displayName.slice(0, 1).toUpperCase()}
              </span>
              <span className="admin-whoami-text">
                <span className="admin-whoami-name">{displayName}</span>
                <span className="admin-whoami-role">Quản trị viên</span>
              </span>
            </div>

            <Button
              size="sm"
              block
              variant="danger"
              className="admin-logout"
              title="Đăng xuất"
              onClick={handleLogout}
            >
              <span className="admin-nav-text">Đăng xuất</span>
              <span className="admin-logout-short" aria-hidden="true">
                ⏻
              </span>
            </Button>
          </div>
        </aside>

        <div className="admin-main">
          <Outlet />
        </div>
      </div>
    </AdminShellContext.Provider>
  );
}
