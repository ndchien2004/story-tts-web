import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

/**
 * Shell for the reading screen.
 *
 * Unlike the default layout there is no page padding and no footer: the reader
 * sizes itself to the viewport and scrolls inside its panes, so anything below
 * it would be unreachable.
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
