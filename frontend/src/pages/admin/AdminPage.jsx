import { Fragment } from "react";
import { Link, NavLink } from "react-router-dom";
import { useAdminShell } from "../../context/admin-shell-context";
import ThemeToggle from "../../components/ThemeToggle";
import { Button } from "../../components/ui";

/**
 * Frame shared by every admin screen.
 *
 * Two bands:
 *
 * — **The chrome bar.** Where am I, and the controls that reach the whole
 *   screen: the sidebar toggle, the trail, this page's primary action, the
 *   theme switch. One line, on every page.
 * — **The work area.** Panels, tables, forms.
 *
 * There is deliberately no band of heading between them. The console names the
 * open screen twice already — lit in the sidebar and last in the trail — and a
 * third naming, set large with a sentence of explanation under it, pushed the
 * tables down the screen on every page for something nobody reads twice. An
 * admin comes here to work on a list, not to be introduced to it. The title
 * survives as a screen-reader heading, since a page still needs one.
 *
 * The bar is a sibling of the scrolling area, not part of it, so a long table
 * never pushes "Lưu" or "Thêm" out of reach.
 *
 * @param crumbs trail of `{ to, label }` between "Quản trị" and this page
 * @param tabs   sibling screens of the same subject, as `{ to, label }`
 */
export default function AdminPage({ crumbs = [], title, tabs, actions, children }) {
  const { openSidebar, toggleRail, collapsed } = useAdminShell();

  /*
   * The trail always starts at the console root, and ends at this page.
   *
   * The title is appended only when the caller's last crumb is a link: a crumb
   * without a `to` is already the caller naming where it is (AdminChaptersPage
   * ends its trail with "Chương"), and appending the title after that would
   * repeat the same place in two words.
   */
  const lastCrumb = crumbs[crumbs.length - 1];
  const trail = [{ to: "/admin", label: "Quản trị" }, ...crumbs];
  if (!lastCrumb || lastCrumb.to) trail.push({ label: title });

  return (
    <>
      <header className="admin-topbar">
        {/* One button, two jobs, decided by width: on a wide screen it narrows
            the sidebar to a rail, on a narrow one it opens the drawer. Both are
            "show me more room", which is why they share a spot. */}
        <Button
          className="nb-icon-btn admin-rail-toggle"
          variant="ghost"
          aria-label={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
          title={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
          aria-pressed={collapsed}
          onClick={toggleRail}
        >
          <PanelIcon collapsed={collapsed} />
        </Button>

        <Button
          className="nb-icon-btn admin-menu-toggle"
          variant="ghost"
          aria-label="Mở menu quản trị"
          title="Mở menu quản trị"
          onClick={openSidebar}
        >
          <MenuIcon />
        </Button>

        <nav className="admin-crumbs" aria-label="Đường dẫn">
          {trail.map((crumb, index) => (
            <Fragment key={`${crumb.label}-${index}`}>
              {index > 0 && (
                <span className="admin-crumb-sep" aria-hidden="true">
                  /
                </span>
              )}
              {crumb.to ? (
                <Link to={crumb.to}>{crumb.label}</Link>
              ) : (
                <span aria-current="page">{crumb.label}</span>
              )}
            </Fragment>
          ))}
        </nav>

        {/* The page's own action rides in the bar now that the head band it
            used to sit in is gone. It stays a sibling of the scrolling area,
            which was the point of that band: "Thêm chương" has to be reachable
            from the bottom of a long table too. */}
        <div className="admin-topbar-actions">
          {actions}
          <ThemeToggle />
        </div>
      </header>

      <div className="admin-body">
        {/* Sighted readers have the trail above and the lit sidebar entry; this
            is the same name for everyone else, and the heading the page needs
            to have at all. */}
        <h1 className="sr-only">{title}</h1>

        {/* Sibling screens of one subject, at the top of the work they belong
            to — rather than as two more entries in the sidebar, which would
            have claimed they are two separate parts of the console. */}
        {tabs && tabs.length > 0 && (
          <nav className="admin-tabs" aria-label="Mục con">
            {tabs.map((tab) => (
              <NavLink
                key={tab.to}
                to={tab.to}
                end
                className={({ isActive }) => `admin-tab ${isActive ? "active" : ""}`}
              >
                {tab.label}
              </NavLink>
            ))}
          </nav>
        )}

        {children}
      </div>
    </>
  );
}

/** Chevrons pointing at the edge the sidebar is about to move towards. */
function PanelIcon({ collapsed }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      style={collapsed ? { transform: "rotate(180deg)" } : undefined}
    >
      <rect x="3.2" y="4.5" width="17.6" height="15" rx="2" />
      <path d="M9.2 4.5v15" />
      <path d="m6.6 10.2-1.8 1.8 1.8 1.8" />
    </svg>
  );
}

function MenuIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}
