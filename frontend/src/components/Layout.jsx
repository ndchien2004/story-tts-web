import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

/** App shell: sticky navigation, routed page content and a footer. */
export default function Layout() {
  return (
    <>
      <Navbar />
      <main className="container page">
        <Outlet />
      </main>
      <footer className="footer">
        <div className="container row-between">
          <span className="muted">
            Truyện Nghe — website đọc &amp; nghe truyện có chuyển văn bản thành giọng nói.
          </span>
          <span className="muted">Đồ án cá nhân</span>
        </div>
      </footer>
    </>
  );
}
