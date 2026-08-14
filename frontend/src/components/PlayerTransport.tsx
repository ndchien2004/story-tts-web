import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import type { AudioMixerEngine } from "../audio/AudioMixerEngine";
import type { MixerSnapshot } from "../audio/types";
import { Button } from "./ui";

const SKIP_SECONDS = 10;
const RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2];

/** `m:ss`, or `h:mm:ss` once the track passes an hour. */
function formatTime(value: number): string {
  if (!Number.isFinite(value) || value < 0) return "--:--";

  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (n: number) => String(n).padStart(2, "0");

  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}

/* ------------------------------------------------------------------ */
/* Icons                                                               */
/* ------------------------------------------------------------------ */

const icon = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2.2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const;

const PlayIcon = () => (
  <svg {...icon} fill="currentColor" stroke="none">
    <path d="M7 4.8v14.4a.8.8 0 0 0 1.22.68l11.5-7.2a.8.8 0 0 0 0-1.36L8.22 4.12A.8.8 0 0 0 7 4.8" />
  </svg>
);

const PauseIcon = () => (
  <svg {...icon} fill="currentColor" stroke="none">
    <rect x="6" y="4.5" width="4.2" height="15" rx="0.6" />
    <rect x="13.8" y="4.5" width="4.2" height="15" rx="0.6" />
  </svg>
);

const SkipBackIcon = () => (
  <svg {...icon}>
    <path d="M11.5 7.5H6.2V2.8" />
    <path d="M6.6 8.4a7.5 7.5 0 1 1-1.1 6" />
  </svg>
);

const SkipForwardIcon = () => (
  <svg {...icon}>
    <path d="M12.5 7.5h5.3V2.8" />
    <path d="M17.4 8.4a7.5 7.5 0 1 0 1.1 6" />
  </svg>
);

const VolumeIcon = ({ muted }: { muted: boolean }) => (
  <svg {...icon}>
    <path d="M4 9.5h3.2L11.5 6v12L7.2 14.5H4z" />
    {muted ? (
      <path d="m15.5 9.8 4.4 4.4m0-4.4-4.4 4.4" />
    ) : (
      <path d="M15.4 9.4a3.7 3.7 0 0 1 0 5.2M18 7a7.2 7.2 0 0 1 0 10" />
    )}
  </svg>
);

/**
 * Playback controls for one audio track.
 *
 * The native `<audio controls>` widget is drawn by the browser, ignores the
 * theme and cannot be restyled, so every control here is rebuilt in the app's
 * own language.
 *
 * <h3>Cái gì đổi khi bộ trộn ra đời</h3>
 * Thẻ `<audio>` không còn nằm trong khối JSX này nữa: nó do
 * {@link AudioMixerEngine} giữ, vì đầu ra của nó phải đi vào một đồ thị Web
 * Audio dùng chung với nhạc nền. Khối này vì thế thuần là mặt điều khiển — nó
 * đọc trạng thái từ ảnh chụp và gọi các phương thức của bộ trộn, không giữ
 * trạng thái phát nào của riêng mình. Nhờ vậy thanh tua và phần tô sáng chữ
 * luôn nói cùng một con số, thay vì mỗi bên tự đọc một cái đồng hồ.
 */
export interface PlayerTransportProps {
  engine: AudioMixerEngine;
  state: MixerSnapshot;
}

export default function PlayerTransport({ engine, state }: PlayerTransportProps) {
  const rateRef = useRef<HTMLDivElement | null>(null);
  const [rateOpen, setRateOpen] = useState(false);

  const { currentTime, duration, rate, volume, muted, buffering, status } = state.narration;
  const playing = status === "playing";

  // The rate menu closes on the next click anywhere outside it, and on Escape.
  useEffect(() => {
    if (!rateOpen) return undefined;

    const onPointerDown = (event: PointerEvent) => {
      if (!rateRef.current?.contains(event.target as Node)) setRateOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setRateOpen(false);
    };

    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [rateOpen]);

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;
  const fill = (percent: number): CSSProperties =>
    ({ "--range-fill": `${percent}%` }) as CSSProperties;

  return (
    <div className="nb-player">
      {/* Chính sách tự phát của trình duyệt, nói bằng tiếng người: không phải
          lỗi, chỉ là chưa có cái bấm nào để tính là đồng ý. */}
      {state.blocked && (
        <p className="nb-player-note" role="status">
          Trình duyệt cần bạn bấm nút phát một lần trước khi phát tiếng.
        </p>
      )}

      <div className="nb-player-seek">
        <span className="nb-player-time">{formatTime(currentTime)}</span>

        <input
          className="nb-range"
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={Math.min(currentTime, duration || 0)}
          disabled={!duration}
          aria-label="Vị trí phát"
          aria-valuetext={`${formatTime(currentTime)} trên ${formatTime(duration)}`}
          style={fill(progress)}
          onChange={(event) => engine.seek(Number(event.target.value))}
        />

        <span className="nb-player-time">{formatTime(duration)}</span>
      </div>

      <div className="nb-player-controls">
        <div className="nb-player-group">
          <Button
            className="nb-icon-btn"
            aria-label={`Lùi ${SKIP_SECONDS} giây`}
            title={`Lùi ${SKIP_SECONDS} giây`}
            onClick={() => engine.skip(-SKIP_SECONDS)}
          >
            <SkipBackIcon />
          </Button>

          <Button
            className="nb-player-play"
            variant="primary"
            aria-label={playing ? "Tạm dừng" : "Phát"}
            onClick={() => void engine.toggle()}
          >
            {buffering ? (
              <span className="spinner" aria-hidden="true" />
            ) : playing ? (
              <PauseIcon />
            ) : (
              <PlayIcon />
            )}
          </Button>

          <Button
            className="nb-icon-btn"
            aria-label={`Tua ${SKIP_SECONDS} giây`}
            title={`Tua ${SKIP_SECONDS} giây`}
            onClick={() => engine.skip(SKIP_SECONDS)}
          >
            <SkipForwardIcon />
          </Button>
        </div>

        <div className="nb-player-group nb-player-group-end">
          {/* The button is the current rate; the choices only take up room
              while the menu is open. */}
          <div className="nb-rate" ref={rateRef}>
            <Button
              className="nb-rate-btn"
              size="sm"
              aria-haspopup="true"
              aria-expanded={rateOpen}
              aria-label={`Tốc độ phát: ${rate}×`}
              title="Tốc độ phát"
              onClick={() => setRateOpen((open) => !open)}
            >
              {rate}×
            </Button>

            {rateOpen && (
              <ul className="nb-menu" role="menu" aria-label="Tốc độ phát">
                {RATE_OPTIONS.map((option) => (
                  <li key={option}>
                    <button
                      type="button"
                      role="menuitemradio"
                      aria-checked={rate === option}
                      onClick={() => {
                        // Đổi tốc độ không đụng gì tới mốc thời gian: mốc tính
                        // theo giây của bản audio, mà `currentTime` cũng vậy —
                        // chạy nhanh gấp rưỡi thì cả hai nhanh gấp rưỡi cùng nhau.
                        engine.setRate(option);
                        setRateOpen(false);
                      }}
                    >
                      {option}×
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Button
            className="nb-icon-btn"
            aria-label={muted ? "Bật tiếng" : "Tắt tiếng"}
            title={muted ? "Bật tiếng" : "Tắt tiếng"}
            onClick={() => engine.setMuted(!muted)}
          >
            <VolumeIcon muted={muted || volume === 0} />
          </Button>

          <input
            className="nb-range nb-player-volume"
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={muted ? 0 : volume}
            aria-label="Âm lượng giọng đọc"
            style={fill((muted ? 0 : volume) * 100)}
            onChange={(event) => engine.setNarrationVolume(Number(event.target.value))}
          />
        </div>
      </div>
    </div>
  );
}
