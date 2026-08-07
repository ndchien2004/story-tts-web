import { createContext, useContext } from "react";

/** Theme context object and its hook. See `auth-context.js` for why it is split. */
export const ThemeContext = createContext(null);

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used inside a ThemeProvider");
  }
  return context;
}
