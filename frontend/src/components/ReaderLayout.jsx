import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

/**
 * Shell for the reading screens — a story's page and the chapter reader.
 *
 * Unlike the default layout there is no page padding and no footer: both screens
 * size themselves to the viewport and scroll inside their panes, so anything
 * below them would be unreachable.
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
