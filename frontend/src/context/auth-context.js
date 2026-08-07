import { createContext, useContext } from "react";

/**
 * Auth context object and its hook.
 *
 * Kept apart from the provider component so the module exports only plain
 * values, which keeps React Fast Refresh working for the provider file.
 */
export const AuthContext = createContext(null);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside an AuthProvider");
  }
  return context;
}
