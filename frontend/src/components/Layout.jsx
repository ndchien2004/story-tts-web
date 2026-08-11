import { Outlet } from "react-router-dom";
import Footer from "./Footer";
import Navbar from "./Navbar";

/** App shell: sticky navigation, routed page content and a footer. */
export default function Layout() {
  return (
    <>
      <Navbar />
      <main className="container page">
        <Outlet />
      </main>
      <Footer />
    </>
  );
}
