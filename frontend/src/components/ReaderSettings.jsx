import { useTheme } from "../context/theme-context";
import { Button } from "./ui";

/** Text size and theme controls for the reading pane. */
export default function ReaderSettings() {
  const { increaseFontSize, decreaseFontSize, canIncrease, canDecrease, isDark, toggleTheme } =
    useTheme();

  return (
    <div className="stack" style={{ gap: "0.6rem" }}>
      <span className="nb-label">Hiển thị</span>

      <div className="row" style={{ gap: "0.4rem" }}>
        <Button size="sm" onClick={decreaseFontSize} disabled={!canDecrease}>
          Chữ nhỏ
        </Button>
        <Button size="sm" onClick={increaseFontSize} disabled={!canIncrease}>
          Chữ lớn
        </Button>
        <Button size="sm" onClick={toggleTheme}>
          {isDark ? "Nền sáng" : "Nền tối"}
        </Button>
      </div>
    </div>
  );
}
