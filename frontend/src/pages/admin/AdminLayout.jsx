import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LOGO_MARK } from "../../brand";
import AdminToaster from "../../components/AdminToaster";
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
  strokeWidth: 2,
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

function CoinIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 8.2v7.6M9.9 10.1h3.2a1.9 1.9 0 0 1 0 3.8H9.9" />
    </svg>
  );
}

function MusicIcon() {
  return (
    <svg {...iconProps}>
      <path d="M9 18V5.2l10-2v12.6" />
      <circle cx="6.2" cy="18" r="2.8" />
      <circle cx="16.2" cy="15.8" r="2.8" />
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

function ExternalIcon() {
  return (
    <svg {...iconProps}>
      <path d="M14 4.5h5.5V10" />
      <path d="M19.5 4.5 11 13" />
      <path d="M18 14.5v4a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6h4" />
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Navigation                                                          */
/* ------------------------------------------------------------------ */

/**
 * The console's destinations, in named groups.
 *
 * Grouped rather than listed flat because the list had grown past the point
 * where a flat column reads as anything: six unrelated words in a row make the
 * reader scan all six every time. Under a heading, "Nhạc nền" is found by
 * looking at "Nội dung" first and reading three items instead of seven.
 *
 * `isActive` is decided here rather than by NavLink's own matching: story pages
 * live under several paths (`/admin/truyen/…`, `/admin/chuong/…`), so `end`
 * would drop the highlight on every nested screen, while dropping `end` would
 * keep "/admin" lit on top of whichever tab is really open.
 */
const NAV_GROUPS = [
  {
    label: "Bảng điều khiển",
    items: [
      {
        to: "/admin",
        label: "Tổng quan",
        Icon: GaugeIcon,
        isActive: (pathname) => pathname === "/admin" || pathname === "/admin/",
      },
    ],
  },
  {
    label: "Nội dung",
    items: [
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
        to: "/admin/nhac-nen",
        label: "Nhạc nền",
        Icon: MusicIcon,
        isActive: (pathname) => pathname.startsWith("/admin/nhac-nen"),
      },
    ],
  },
  {
    label: "Cộng đồng",
    items: [
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
    ],
  },
  {
    label: "Kinh doanh",
    items: [
      {
        to: "/admin/vip",
        label: "Gói VIP & thanh toán",
        Icon: CardIcon,
        isActive: (pathname) => pathname.startsWith("/admin/vip"),
      },
      // Tab riêng chứ không gộp vào mục VIP: hai thứ bán được khác nhau về bản
      // chất — một cái bán thời gian, một cái bán lượt mở — và gộp lại thì mỗi
      // lần sửa bảng giá phải đoán xem mình đang ở bảng nào.
      //
      // Gift code ở chung mục này chứ không thành mục thứ ba, cùng cách chia với
      // mục VIP ở trên: bán Xu và phát Xu là hai mặt của một việc, nên chúng là
      // hai tab bên trong màn hình chứ không phải hai chỗ trong thanh bên.
      {
        to: "/admin/xu/goi",
        label: "Gói nạp Xu & gift code",
        Icon: CoinIcon,
        isActive: (pathname) => pathname.startsWith("/admin/xu"),
      },
    ],
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
  const toggleRail = useCallback(() => setCollapsed((value) => !value), []);
  const shell = useMemo(
    () => ({ openSidebar, toggleRail, collapsed }),
    [openSidebar, toggleRail, collapsed],
  );

  const displayName = user?.displayName || user?.username || "Admin";

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <AdminShellContext.Provider value={shell}>
      {/* Wraps the whole console so every screen under it can report a success
          the same way, into the same corner. */}
      <AdminToaster>
        <div className={`admin-shell ${collapsed ? "admin-shell-collapsed" : ""}`}>
          {sidebarOpen && (
            <div
              className="admin-scrim"
              role="presentation"
              onClick={() => setSidebarOpen(false)}
            />
          )}

          <aside className={`admin-sidebar ${sidebarOpen ? "open" : ""}`}>
            {/* The mark and the product's name, unframed: it says where you
                are, it is not one of the destinations listed under it. */}
            <Link to="/admin" className="admin-brand" title="Truyện Nghe — Bảng quản trị">
              <img className="admin-brand-mark" src={LOGO_MARK} alt="" width="40" height="40" />
              <span className="admin-brand-text">
                <span className="admin-brand-name">Truyện Nghe</span>
                <span className="admin-brand-sub">Bảng quản trị</span>
              </span>
            </Link>

            <nav className="admin-nav" aria-label="Điều hướng quản trị">
              {NAV_GROUPS.map((group) => (
                <div className="admin-nav-group" key={group.label}>
                  <span className="admin-nav-label">{group.label}</span>
                  {group.items.map(({ to, label, Icon, isActive }) => (
                    <NavLink
                      key={to}
                      to={to}
                      // Collapsed there is no label to read, so the tooltip carries it.
                      title={label}
                      // A function, not a string: given a string, NavLink appends
                      // its own "active" class on top of ours, and by its
                      // reckoning "/admin" matches everything beneath it.
                      className={() => `admin-nav-link ${isActive(pathname) ? "active" : ""}`}
                    >
                      <Icon />
                      <span className="admin-nav-text">{label}</span>
                    </NavLink>
                  ))}
                </div>
              ))}
            </nav>

            <div className="admin-sidebar-spacer" />

            {/* The theme switch lives in the top bar, with the other controls
                that belong to the person rather than to the page. */}
            <div className="admin-sidebar-foot">
              {/* Signing in as an admin now lands here rather than on the site,
                  so the way back to the reader's view has to be somewhere on
                  screen. This is that somewhere. */}
              <Link
                to="/"
                className="admin-nav-link admin-sidebar-exit"
                title="Xem trang người đọc"
              >
                <ExternalIcon />
                <span className="admin-nav-text">Xem trang người đọc</span>
              </Link>

              <div className="admin-whoami" title={displayName}>
                <span className="admin-whoami-avatar" aria-hidden="true">
                  {displayName.slice(0, 1).toUpperCase()}
                </span>
                {/* One line, not two. The account's own name sat above this and
                    an admin's display name is "Quản trị viên" as often as not,
                    which left the corner of the sidebar saying the same words
                    twice. The name is on the avatar and in the tooltip. */}
                <span className="admin-whoami-text">
                  <span className="admin-whoami-role">Quản trị viên</span>
                </span>

                <Button
                  className="nb-icon-btn admin-logout"
                  variant="ghost"
                  size="sm"
                  title="Đăng xuất"
                  aria-label="Đăng xuất"
                  onClick={handleLogout}
                >
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M14.5 8V5.8a1.8 1.8 0 0 0-1.8-1.8H6.3A1.8 1.8 0 0 0 4.5 5.8v12.4A1.8 1.8 0 0 0 6.3 20h6.4a1.8 1.8 0 0 0 1.8-1.8V16" />
                    <path d="M10 12h9.5m0 0-2.8-2.8M19.5 12l-2.8 2.8" />
                  </svg>
                </Button>
              </div>
            </div>
          </aside>

          <div className="admin-main">
            <Outlet />
          </div>
        </div>
      </AdminToaster>
    </AdminShellContext.Provider>
  );
}
