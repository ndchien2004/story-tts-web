import { useEffect, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { LOGO_MARK } from "../brand";
import { useAuth } from "../context/auth-context";
import GenreMenu from "./GenreMenu";
import NotificationBell from "./NotificationBell";
import ThemeToggle from "./ThemeToggle";
import UserMenu from "./UserMenu";
import { ButtonLink } from "./ui";

const AUTH_PATHS = ["/dang-nhap", "/dang-ky"];

const MenuIcon = ({ open }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    {open ? <path d="m6 6 12 12M18 6 6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
  </svg>
);

/**
 * The site header.
 *
 * Two halves with a clear job each: on the left the brand and two ways into the
 * catalogue, on the right the controls that belong to the person looking at the
 * screen, folded behind one account button.
 *
 * The two links are plain text that simply darkens under the cursor. In a bar
 * this short, boxes and glyphs were decoration competing with the only thing
 * that had to stand out — the brand on one side, the account on the other.
 * Everything else a reader owns (their shelf, the console) lives in the account
 * menu rather than spread across the bar.
 *
 * Below 900px the links collapse behind the menu button, while the theme switch
 * and the account button stay in the bar: those two are needed most often and
 * cost the least room.
 */
export default function Navbar() {
  const { isAuthenticated } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const { pathname } = useLocation();

  const closeMenu = () => setMenuOpen(false);

  // Following a link inside the collapsed menu should put it away; the route
  // changing is the one signal that covers every way of leaving.
  useEffect(closeMenu, [pathname]);

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="navbar-brand" onClick={closeMenu}>
          <img className="navbar-logo" src={LOGO_MARK} alt="" width="40" height="40" />
          <span className="navbar-wordmark">
            <strong>Truyện Nghe</strong>
            <small>Đọc &amp; nghe truyện online</small>
          </span>
        </Link>

        <div className="grow" />

        <nav className={`navbar-links ${menuOpen ? "open" : ""}`} aria-label="Điều hướng chính">
          <NavLink to="/" end className="navbar-link" onClick={closeMenu}>
            Trang chủ
          </NavLink>

          <GenreMenu onNavigate={closeMenu} />
        </nav>

        <div className="navbar-actions">
          <ThemeToggle />

          {isAuthenticated ? (
            /* The bell sits immediately left of the avatar, and only for
               someone signed in: a notification belongs to an account, so
               there is nothing for it to count otherwise. It stays in the bar
               at every width — like the theme switch and the account button,
               it is needed often and costs almost no room. */
            <>
              <NotificationBell onNavigate={closeMenu} />
              <UserMenu onNavigate={closeMenu} />
            </>
          ) : (
            /* One entry point, not two look-alike buttons side by side. The
               login page carries the link to registration, and so does the
               home page, so nothing becomes unreachable by dropping it here.
               On the auth pages themselves the button would point at the form
               already on screen, so it steps aside. */
            !AUTH_PATHS.includes(pathname) && (
              <ButtonLink to="/dang-nhap" size="sm" variant="primary">
                Đăng nhập
              </ButtonLink>
            )
          )}

          <button
            type="button"
            className="nb-btn nb-btn-sm nb-icon-btn navbar-toggle"
            aria-expanded={menuOpen}
            aria-label={menuOpen ? "Đóng menu" : "Mở menu"}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <MenuIcon open={menuOpen} />
          </button>
        </div>
      </div>
    </header>
  );
}
