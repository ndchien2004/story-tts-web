import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

const POINTS = [
  "Nghe chương bằng giọng đọc AI tiếng Việt, không cần chờ bản thu",
  "Tự chuyển chương khi nghe hết, đọc và nghe nối tiếp nhau",
  "Lưu cỡ chữ, nền sáng tối và bản audio đã tạo cho lần sau",
];

/**
 * Shell for signing in and signing up.
 *
 * The pitch column belongs to the layout rather than to either page: it is the
 * same on both, so switching between the two swaps only the form and the text
 * beside it never flickers.
 *
 * Like the reader, this shell sizes itself to what the header leaves and takes
 * no page padding or footer — anything below would be unreachable.
 */
export default function AuthLayout() {
  return (
    <>
      <Navbar />
      <main className="container">
        <div className="auth-shell">
          <section className="auth-intro">
            <span className="auth-eyebrow">Truyện Nghe</span>

            <h2 className="auth-headline">
              Đọc truyện. Nghe truyện. Bằng cả <span className="auth-mark">giọng AI</span>.
            </h2>

            <p className="auth-blurb">
              Chưa có bản thu âm? Bấm một nút để hệ thống tự chuyển chương truyện thành giọng đọc
              tiếng Việt và nghe ngay trên trình duyệt.
            </p>

            <ul className="auth-points">
              {POINTS.map((point) => (
                <li key={point}>{point}</li>
              ))}
            </ul>
          </section>

          <div className="auth-form-pane scroll-area">
            <Outlet />
          </div>
        </div>
      </main>
    </>
  );
}
