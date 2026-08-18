import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import type { AudioMixerEngine } from "../audio/AudioMixerEngine";
import { formatTime } from "../audio/formatTime";
import type { MixerSnapshot } from "../audio/types";
import { Button } from "./ui";

const SKIP_SECONDS = 10;
const RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2];

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

/*
 * Mũi tên vòng mang sẵn con số, thay cho hình tam giác kèm vạch.
 *
 * Cùng một việc như trước, nhưng nói rõ lùi bao nhiêu. Hai nút này được bấm để
 * nghe lại một câu vừa trôi qua, và con số nằm ngay trong hình trả lời "lùi bao
 * lâu" mà không cần rê chuột chờ chú giải hiện ra.
 */
const SkipBackIcon = () => (
  <svg {...icon} strokeWidth={1.9}>
    <path d="M12 5.4a7.8 7.8 0 1 1-7.4 5.4" />
    <path d="M12 2 8.4 5.4 12 8.8" />
    <text
      x="12"
      y="16.6"
      textAnchor="middle"
      fontSize="8.2"
      fontWeight="700"
      fill="currentColor"
      stroke="none"
    >
      10
    </text>
  </svg>
);

const SkipForwardIcon = () => (
  <svg {...icon} strokeWidth={1.9}>
    <path d="M12 5.4a7.8 7.8 0 1 0 7.4 5.4" />
    <path d="m12 2 3.6 3.4L12 8.8" />
    <text
      x="12"
      y="16.6"
      textAnchor="middle"
      fontSize="8.2"
      fontWeight="700"
      fill="currentColor"
      stroke="none"
    >
      10
    </text>
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
 * theme and cannot be restyled, so every control here is rebuilt in the app own
 * language.
 *
 * <h3>Cái gì đổi khi bộ trộn ra đời</h3>
 * Thẻ `<audio>` không còn nằm trong khối JSX nào ở đây nữa: nó do
 * {@link AudioMixerEngine} giữ, vì đầu ra của nó phải đi vào một đồ thị Web
 * Audio dùng chung với nhạc nền. Tệp này vì thế thuần là mặt điều khiển — nó
 * đọc trạng thái từ ảnh chụp và gọi các phương thức của bộ trộn, không giữ
 * trạng thái phát nào của riêng mình. Nhờ vậy thanh tua và phần tô sáng chữ
 * luôn nói cùng một con số, thay vì mỗi bên tự đọc một cái đồng hồ.
 *
 * <h3>Vì sao là những mảnh rời chứ không một khối</h3>
 * Trình phát giờ nằm trong thanh dưới đáy trang đọc, và thanh ấy không xếp bộ
 * điều khiển thành một hàng: lùi — phát — tua và thanh kéo nằm chính giữa, còn
 * tốc độ đọc và âm lượng nằm nép bên phải cạnh những nút của riêng trang đọc.
 * Hai chỗ ấy cách nhau cả một khối trong cây DOM, nên không có cách xếp nào gói
 * được cả hai vào chung một khối cha.
 */
export interface PlayerControlProps {
  engine: AudioMixerEngine;
  state: MixerSnapshot;
}

const fill = (percent: number): CSSProperties =>
  ({ "--range-fill": `${percent}%` }) as CSSProperties;

/* ------------------------------------------------------------------ */
/* Autoplay                                                            */
/* ------------------------------------------------------------------ */

/**
 * Chính sách tự phát của trình duyệt, nói bằng tiếng người: không phải lỗi, chỉ
 * là chưa có cái bấm nào để tính là đồng ý.
 */
export function AutoplayNote({ state }: { state: MixerSnapshot }) {
  if (!state.blocked) return null;

  return (
    <p className="nb-player-note" role="status">
      Trình duyệt cần bạn bấm nút phát một lần trước khi phát tiếng.
    </p>
  );
}

/* ------------------------------------------------------------------ */
/* Thanh tua                                                           */
/* ------------------------------------------------------------------ */

/** Giờ đã trôi, thanh kéo, tổng thời lượng — các con số nằm hai bên thanh. */
export function SeekBar({ engine, state }: PlayerControlProps) {
  const { currentTime, duration } = state.narration;
  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
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
  );
}

/* ------------------------------------------------------------------ */
/* Lùi — phát — tua                                                    */
/* ------------------------------------------------------------------ */

/** Ba nút không bao giờ rời nhau, và là thứ tay với tới nhiều nhất giữa chương. */
export function TransportButtons({ engine, state }: PlayerControlProps) {
  const { buffering, status } = state.narration;
  const playing = status === "playing";

  return (
    <div className="nb-player-group">
      <Button
        className="nb-icon-btn nb-player-skip"
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
        className="nb-icon-btn nb-player-skip"
        aria-label={`Tua ${SKIP_SECONDS} giây`}
        title={`Tua ${SKIP_SECONDS} giây`}
        onClick={() => engine.skip(SKIP_SECONDS)}
      >
        <SkipForwardIcon />
      </Button>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Tốc độ đọc                                                          */
/* ------------------------------------------------------------------ */

/**
 * Playback rate.
 *
 * A native select spent most of its width on the dropdown arrow and pushed the
 * rest of the row out of shape, so the current rate is the button and the
 * choices only exist while the menu is open.
 *
 * <p>Cách bày ấy đáng giá gấp đôi khi nút nằm trên dải rút gọn: tốc độ đọc là
 * thứ người nghe đổi giữa chương, nhưng chỉ đổi một lần rồi thôi — nó xứng đáng
 * một chỗ trong tầm tay, không xứng đáng năm chỗ. Danh sách mở lên trên, vì
 * dưới nút là mép màn hình.
 */
export function RateControl({ engine, state }: PlayerControlProps) {
  const rateRef = useRef<HTMLDivElement | null>(null);
  const [rateOpen, setRateOpen] = useState(false);
  const { rate } = state.narration;

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

  return (
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
                  // Đổi tốc độ không đụng gì tới mốc thời gian: mốc tính theo
                  // giây của bản audio, mà `currentTime` cũng vậy — chạy nhanh
                  // gấp rưỡi thì cả hai nhanh gấp rưỡi cùng nhau.
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
  );
}

/* ------------------------------------------------------------------ */
/* Âm lượng giọng đọc                                                  */
/* ------------------------------------------------------------------ */

/**
 * Nút tắt tiếng và thanh âm lượng, đi liền nhau vì cùng nói về một thứ.
 *
 * <p>`className` để chỗ đặt tự nói cỡ của mình: trên dải dưới đáy trang đọc,
 * thanh kéo phải co vừa một góc bên phải, còn trong một tấm bảng mở ra thì nó
 * được cả chiều ngang.
 */
export function VolumeControl({
  engine,
  state,
  className = "",
}: PlayerControlProps & { className?: string }) {
  const { volume, muted } = state.narration;

  return (
    <div className={`nb-player-group nb-player-volume-group ${className}`}>
      <Button
        className="nb-icon-btn nb-player-mute"
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
  );
}
