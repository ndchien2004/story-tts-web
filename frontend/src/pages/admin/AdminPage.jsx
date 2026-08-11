import { Fragment } from "react";
import { Link } from "react-router-dom";
import { useAdminShell } from "../../context/admin-shell-context";
import ThemeToggle from "../../components/ThemeToggle";
import { Button } from "../../components/ui";

/**
 * Frame shared by every admin screen.
 *
 * The top bar is a sibling of the scrolling area, not part of it, so the page
 * title and its primary action stay put while the content below scrolls on its
 * own. Pages therefore never grow the window itself.
 *
 * @param crumbs trail of `{ to, label }`; the last entry may omit `to`
 */
export default function AdminPage({ crumbs = [], title, actions, children }) {
  const { openSidebar } = useAdminShell();

  return (
    <>
      <header className="admin-topbar">
        <Button
          className="admin-menu-toggle"
          size="sm"
          aria-label="Mở menu quản trị"
          onClick={openSidebar}
        >
          ☰
        </Button>

        <div className="admin-topbar-heading">
          {crumbs.length > 0 && (
            <nav className="admin-crumbs" aria-label="Đường dẫn">
              {crumbs.map((crumb, index) => (
                <Fragment key={crumb.label}>
                  {index > 0 && <span aria-hidden="true">/</span>}
                  {crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span>{crumb.label}</span>}
                </Fragment>
              ))}
            </nav>
          )}
          <h1>{title}</h1>
        </div>

        {/* Page actions first, then the theme switch pinned to the corner —
            it belongs to the console, not to whichever screen is open, so it
            keeps the same spot on every page. */}
        <div className="admin-topbar-actions">
          {actions}
          <ThemeToggle />
        </div>
      </header>

      <div className="admin-body">{children}</div>
    </>
  );
}
