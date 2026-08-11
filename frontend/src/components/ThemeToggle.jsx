import { useTheme } from "../context/theme-context";
import { Button } from "./ui";

const icon = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2.2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

const SunIcon = () => (
  <svg {...icon}>
    <circle cx="12" cy="12" r="4.2" />
    <path d="M12 2.5v2.2M12 19.3v2.2M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6" />
  </svg>
);

const MoonIcon = () => (
  <svg {...icon}>
    <path d="M20.5 14.3A8.5 8.5 0 0 1 9.7 3.5a8.5 8.5 0 1 0 10.8 10.8" />
  </svg>
);

/**
 * Light/dark switch.
 *
 * The glyph shows the theme the click leads to — a sun while dark, a moon
 * while light — so the button reads as the way out of the current mode. The
 * accessible name spells that out, since a lone icon cannot.
 */
export default function ThemeToggle({ showLabel = false, block = false, size = "sm" }) {
  const { isDark, toggleTheme } = useTheme();

  const label = isDark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối";

  return (
    <Button
      size={size}
      block={block}
      className={showLabel ? "" : "nb-icon-btn"}
      aria-label={label}
      title={label}
      onClick={toggleTheme}
    >
      {isDark ? <SunIcon /> : <MoonIcon />}
      {showLabel && (isDark ? "Giao diện sáng" : "Giao diện tối")}
    </Button>
  );
}
