import { useTheme } from "../context/theme-context";
import { Button } from "./ui";

/**
 * Text size control for the reading pane.
 *
 * It sits in the corner of the text it changes, so the effect of a click is in
 * view when you make it. That leaves room for one label and two buttons and no
 * more — hence `A−` / `A+` rather than the words, with the full wording kept on
 * the accessible name where a screen reader can still reach it.
 *
 * Light and dark are deliberately not here: that switch lives in the
 * navigation bar, which stays visible while reading.
 */
export default function ReaderSettings() {
  const { increaseFontSize, decreaseFontSize, canIncrease, canDecrease } = useTheme();

  return (
    <div className="reader-settings">
      <span className="nb-label">Cỡ chữ</span>

      <div className="reader-settings-buttons">
        <Button
          size="sm"
          className="reader-settings-btn"
          aria-label="Giảm cỡ chữ"
          title="Giảm cỡ chữ"
          onClick={decreaseFontSize}
          disabled={!canDecrease}
        >
          A−
        </Button>
        <Button
          size="sm"
          className="reader-settings-btn"
          aria-label="Tăng cỡ chữ"
          title="Tăng cỡ chữ"
          onClick={increaseFontSize}
          disabled={!canIncrease}
        >
          A+
        </Button>
      </div>
    </div>
  );
}
