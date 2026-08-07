import { NavLink, Outlet } from "react-router-dom";

const TABS = [
  { to: "/admin", label: "Truyện", end: true },
  { to: "/admin/thanh-vien", label: "Thành viên", end: false },
];

/** Shared header and tab bar for every admin screen. */
export default function AdminLayout() {
  return (
    <div className="stack" style={{ gap: "1.5rem" }}>
      <nav className="row" style={{ gap: "0.5rem" }}>
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) => `nb-btn ${isActive ? "nb-btn-primary" : ""}`}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </div>
  );
}
