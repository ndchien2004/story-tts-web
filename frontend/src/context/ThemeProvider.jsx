import { useCallback, useEffect, useMemo, useState } from "react";
import { ThemeContext } from "./theme-context";

const THEME_KEY = "storytts.theme";

/**
 * Versioned on purpose.
 *
 * The size is written to storage on every mount, not only when the reader
 * touches the control, so by now every visitor has a value saved — including
 * those who never chose one. Keeping the old key would mean the new default
 * below reaches nobody. The suffix resets that once; whatever the reader picks
 * afterwards sticks as before.
 */
const FONT_SIZE_KEY = "storytts.readerFontSize.v2";

const MIN_FONT_SIZE = 0.95;
const MAX_FONT_SIZE = 1.75;
const FONT_SIZE_STEP = 0.1;

/** The reading pane opens at the largest size on offer. */
const DEFAULT_FONT_SIZE = MAX_FONT_SIZE;

function clampFontSize(value) {
  return Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, Number(value.toFixed(3))));
}

function readInitialTheme() {
  const stored = localStorage.getItem(THEME_KEY);
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function readInitialFontSize() {
  const stored = Number.parseFloat(localStorage.getItem(FONT_SIZE_KEY));
  return Number.isFinite(stored) ? clampFontSize(stored) : DEFAULT_FONT_SIZE;
}

/** Owns the colour theme and the reader's font-size preference. */
export default function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(readInitialTheme);
  const [readerFontSize, setReaderFontSize] = useState(readInitialFontSize);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.style.setProperty("--reader-font-size", `${readerFontSize}rem`);
    localStorage.setItem(FONT_SIZE_KEY, String(readerFontSize));
  }, [readerFontSize]);

  const toggleTheme = useCallback(
    () => setTheme((current) => (current === "dark" ? "light" : "dark")),
    [],
  );

  const increaseFontSize = useCallback(
    () => setReaderFontSize((size) => clampFontSize(size + FONT_SIZE_STEP)),
    [],
  );

  const decreaseFontSize = useCallback(
    () => setReaderFontSize((size) => clampFontSize(size - FONT_SIZE_STEP)),
    [],
  );

  const value = useMemo(
    () => ({
      theme,
      isDark: theme === "dark",
      toggleTheme,
      readerFontSize,
      increaseFontSize,
      decreaseFontSize,
      canIncrease: readerFontSize < MAX_FONT_SIZE,
      canDecrease: readerFontSize > MIN_FONT_SIZE,
    }),
    [theme, toggleTheme, readerFontSize, increaseFontSize, decreaseFontSize],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
