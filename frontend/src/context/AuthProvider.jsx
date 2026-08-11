import { useCallback, useEffect, useMemo, useState } from "react";
import { authApi } from "../api/endpoints";
import { getStoredToken, setStoredToken } from "../api/client";
import { AuthContext } from "./auth-context";

/**
 * Holds the signed-in user and keeps the JWT in sync with local storage.
 *
 * On mount the stored token is re-validated against `/api/auth/me` rather than
 * trusted blindly, so a revoked or expired token is dropped immediately and the
 * VIP flag always reflects the server state.
 */
export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initialising, setInitialising] = useState(Boolean(getStoredToken()));

  useEffect(() => {
    if (!getStoredToken()) {
      setInitialising(false);
      return;
    }

    let cancelled = false;
    authApi
      .me()
      .then((data) => {
        if (!cancelled) setUser(data);
      })
      .catch(() => {
        setStoredToken(null);
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setInitialising(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const applySession = useCallback((session) => {
    setStoredToken(session.token);
    setUser(session.user);
    return session.user;
  }, []);

  const login = useCallback(
    async (credentials) => applySession(await authApi.login(credentials)),
    [applySession],
  );

  const register = useCallback(
    async (payload) => applySession(await authApi.register(payload)),
    [applySession],
  );

  /**
   * One call covers both signing in and signing up: the server creates the
   * account the first time a Google identity turns up, so the browser has no
   * way of telling the two apart and does not need to.
   */
  const loginWithGoogle = useCallback(
    async (credential) => applySession(await authApi.google(credential)),
    [applySession],
  );

  const logout = useCallback(() => {
    setStoredToken(null);
    setUser(null);
  }, []);

  /** Re-fetch the profile, e.g. after an admin changes the VIP flag. */
  const refresh = useCallback(async () => {
    if (!getStoredToken()) return null;
    const data = await authApi.me();
    setUser(data);
    return data;
  }, []);

  const value = useMemo(
    () => ({
      user,
      initialising,
      isAuthenticated: user !== null,
      isAdmin: user?.role === "ADMIN",
      isVip: Boolean(user?.vip),
      login,
      register,
      loginWithGoogle,
      logout,
      refresh,
    }),
    [user, initialising, login, register, loginWithGoogle, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
