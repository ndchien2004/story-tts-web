import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { useTheme } from "../context/theme-context";
import { Badge, Button, ButtonLink } from "./ui";

export default function Navbar() {
  const { user, isAuthenticated, isAdmin, isVip, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();

  const closeMenu = () => setMenuOpen(false);

  const handleLogout = () => {
    logout();
    closeMenu();
    navigate("/");
  };

  return (
    <header className="navbar">
      <div className="container navbar-inner" style={{ position: "relative" }}>
        <Link to="/" className="navbar-brand" onClick={closeMenu}>
          <span className="navbar-brand-mark" aria-hidden="true" />
          Truyện Nghe
        </Link>

        <div className="grow" />

        <Button
          className="navbar-toggle"
          size="sm"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          Menu
        </Button>

        <nav className={`navbar-links ${menuOpen ? "open" : ""}`}>
          <NavLink to="/" end className="navbar-link" onClick={closeMenu}>
            Trang chủ
          </NavLink>
          <NavLink to="/truyen" className="navbar-link" onClick={closeMenu}>
            Danh sách truyện
          </NavLink>
          {isAdmin && (
            <NavLink to="/admin" className="navbar-link" onClick={closeMenu}>
              Quản trị
            </NavLink>
          )}

          <Button size="sm" onClick={toggleTheme}>
            {isDark ? "Giao diện sáng" : "Giao diện tối"}
          </Button>

          {isAuthenticated ? (
            <>
              <span className="navbar-user">
                {user.displayName || user.username}
                {isAdmin && <Badge tone="info">Admin</Badge>}
                {!isAdmin && isVip && <Badge tone="vip">VIP</Badge>}
              </span>
              <Button size="sm" onClick={handleLogout}>
                Đăng xuất
              </Button>
            </>
          ) : (
            <>
              <ButtonLink to="/dang-nhap" size="sm" onClick={closeMenu}>
                Đăng nhập
              </ButtonLink>
              <ButtonLink to="/dang-ky" size="sm" variant="primary" onClick={closeMenu}>
                Đăng ký
              </ButtonLink>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
