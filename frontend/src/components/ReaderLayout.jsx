import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

/**
 * Shell for the reading screens — a story's page and the chapter reader.
 *
 * Unlike the default layout there is no page padding and no footer: both screens
 * size themselves to the viewport and scroll inside their panes, so anything
 * below them would be unreachable.
 *
 * <h3>Thanh điều hướng ở đây có thể tự biến mất</h3>
 * `<Navbar />` vẫn được dựng, nhưng CSS ẩn nó đi khi trang chương đang mở
 * đúng chương — quy tắc `body:has(.reader)` trong components.css. Đọc truyện
 * là một chế độ, và trên thanh của riêng chế độ ấy đã có đủ mọi chỗ người đọc
 * muốn tới.
 *
 * <p>Ẩn bằng CSS chứ không bằng một cờ truyền xuống, vì phép thử đúng không
 * phải "đang ở route nào" mà là "màn hình đọc có thật sự hiện ra không". Trang
 * chương còn ba nhánh nữa — đang tải, chương bị khoá, chương phải trả Xu — và
 * ở những nhánh ấy thanh điều hướng là đường ra duy nhất, nên nó phải ở lại.
 */
export default function ReaderLayout() {
  return (
    <>
      <Navbar />
      <main className="container-wide">
        <Outlet />
      </main>
    </>
  );
}
