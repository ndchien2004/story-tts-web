import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { Spinner } from "./ui";

/**
 * Blocks a branch of the router until the user is signed in.
 *
 * The current location is passed along so the login page can send the user back
 * where they came from.
 */
export function RequireAuth() {
  const { isAuthenticated, initialising } = useAuth();
  const location = useLocation();

  if (initialising) return <Spinner label="Đang kiểm tra phiên đăng nhập…" />;

  if (!isAuthenticated) {
    return <Navigate to="/dang-nhap" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

/**
 * Branch that only makes sense while signed out.
 *
 * Signing in or registering again is meaningless once a session exists, so
 * those pages send an authenticated visitor home instead of showing a form
 * that would replace their own session.
 *
 * "Home" is the console for an admin — the same rule the sign-in form applies
 * after a successful login, so reopening `/dang-nhap` with a live session lands
 * in the same place as signing in would have.
 */
export function RequireGuest() {
  const { isAuthenticated, isAdmin, initialising } = useAuth();

  if (initialising) return <Spinner label="Đang kiểm tra phiên đăng nhập…" />;

  if (isAuthenticated) return <Navigate to={isAdmin ? "/admin" : "/"} replace />;

  return <Outlet />;
}

/**
 * Admin-only branch.
 *
 * This is a convenience guard only: the API enforces the same rule server-side,
 * so hiding the routes here never becomes the sole line of defence.
 */
export function RequireAdmin() {
  const { isAuthenticated, isAdmin, initialising } = useAuth();
  const location = useLocation();

  if (initialising) return <Spinner label="Đang kiểm tra quyền truy cập…" />;

  if (!isAuthenticated) {
    return <Navigate to="/dang-nhap" replace state={{ from: location }} />;
  }
  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
