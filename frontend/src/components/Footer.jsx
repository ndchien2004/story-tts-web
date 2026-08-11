import { Link } from "react-router-dom";
import { LOGO_MARK } from "../brand";

/**
 * Site footer.
 *
 * Three short columns rather than a wall of links: where to go next, what the
 * account gives you, and the legal line. Anything a reader never clicks —
 * social icons for accounts that do not exist, a newsletter box nobody
 * staffs — is left out on purpose.
 */
export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="footer">
      <div className="container footer-inner">
        <div className="footer-brand">
          <Link to="/" className="footer-brand-link">
            <img src={LOGO_MARK} alt="" width="36" height="36" />
            <strong>Truyện Nghe</strong>
          </Link>
          <p className="muted">
            Đọc truyện chữ và nghe bản audio của cùng một chương, với giọng đọc tiếng Việt do AI
            tổng hợp khi truyện chưa có bản thu.
          </p>
        </div>

        <nav className="footer-links" aria-label="Khám phá">
          <span className="footer-heading">Khám phá</span>
          <Link to="/">Trang chủ</Link>
          <Link to="/truyen">Tất cả truyện</Link>
          <Link to="/truyen?sort=popular">Được xem nhiều</Link>
          <Link to="/truyen?sort=newest">Mới cập nhật</Link>
        </nav>

        <nav className="footer-links" aria-label="Tài khoản">
          <span className="footer-heading">Tài khoản</span>
          <Link to="/tai-khoan">Kệ của tôi</Link>
          <Link to="/nang-cap">Nâng cấp VIP</Link>
          <Link to="/dang-nhap">Đăng nhập</Link>
          <Link to="/dang-ky">Tạo tài khoản</Link>
        </nav>
      </div>

      <div className="container footer-bottom">
        <span>© {year} Truyện Nghe</span>
        <span>Nội dung thuộc về tác giả và các nhà xuất bản tương ứng.</span>
      </div>
    </footer>
  );
}
