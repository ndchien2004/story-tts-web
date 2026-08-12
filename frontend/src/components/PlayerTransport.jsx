import { useEffect, useRef, useState } from "react";
import { Button } from "./ui";

const SKIP_SECONDS = 10;
const RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2];

/** A saved position this close to the end counts as finished, not as a place to resume. */
const RESUME_END_MARGIN_S = 5;

/** `m:ss`, or `h:mm:ss` once the track passes an hour. */
function formatTime(value) {
  if (!Number.isFinite(value) || value < 0) return "--:--";

  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (n) => String(n).padStart(2, "0");

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
};

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

const VolumeIcon = ({ muted }) => (
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
 * The `<audio>` element is kept for decoding and byte-range streaming only —
 * its own `controls` widget is drawn by the browser, ignores the theme and
 * cannot be restyled, so every control here is rebuilt in the app's own
 * language. Seeking still hits the server's range support, so dragging the
 * scrubber does not download the whole file.
 *
 * @param autoPlay start playing as soon as the track can play
 * @param onAutoPlayed called once the auto-start has been honoured
 * @param initialPosition seconds to resume from, 0 to start at the beginning
 * @param onPositionChange called as playback advances, for saving the position
 * @param onPause called with the exact position when playback stops
 */
export default function PlayerTransport({
  src,
  autoPlay = false,
  onAutoPlayed,
  onEnded,
  initialPosition = 0,
  onPositionChange,
  onPause,
}) {
  const audioRef = useRef(null);

  // Resuming happens once per track. Without the latch, a `loadedmetadata` that
  // fires again (some browsers do, after a seek) would drag the listener back to
  // where they started.
  const resumedRef = useRef(false);

  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [rate, setRate] = useState(1);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [buffering, setBuffering] = useState(false);

  const rateRef = useRef(null);
  const [rateOpen, setRateOpen] = useState(false);

  // The rate menu closes on the next click anywhere outside it, and on Escape.
  useEffect(() => {
    if (!rateOpen) return undefined;

    const onPointerDown = (event) => {
      if (!rateRef.current?.contains(event.target)) setRateOpen(false);
    };
    const onKeyDown = (event) => {
      if (event.key === "Escape") setRateOpen(false);
    };

    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [rateOpen]);

  // A new track means a fresh timeline, but the chosen rate and volume are
  // preferences and deliberately survive the switch.
  useEffect(() => {
    setCurrentTime(0);
    setDuration(0);
    setPlaying(false);
    resumedRef.current = false;
  }, [src]);

  /**
   * Keeps the position when the listener leaves mid-track.
   *
   * Navigating away tears the element down without a `pause` event, so without
   * this the last stretch since the throttled save would be lost — which is
   * exactly the stretch someone who just closed the tab cares about.
   */
  useEffect(() => {
    const element = audioRef.current;
    return () => {
      const seconds = element?.currentTime ?? 0;
      if (seconds > 0) onPause?.(seconds);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src]);

  useEffect(() => {
    const element = audioRef.current;
    if (element) element.playbackRate = rate;
  }, [rate, src]);

  useEffect(() => {
    const element = audioRef.current;
    if (!element) return;
    element.volume = volume;
    element.muted = muted;
  }, [volume, muted]);

  /**
   * Honours an auto-start request for a track that is already loaded.
   *
   * When the source is new, `loadedmetadata` handles it instead — that event
   * does not fire again for a track the element already holds, which is the
   * case when the user replays a cached narration.
   */
  useEffect(() => {
    const element = audioRef.current;
    if (!autoPlay || !element || element.readyState < 1) return;

    // Autoplay without a gesture is refused by browsers; the gesture here is
    // the click that asked for narration, and a refusal is harmless anyway.
    element.play().catch(() => {});
    onAutoPlayed?.();
  }, [autoPlay, onAutoPlayed, src]);

  function togglePlay() {
    const element = audioRef.current;
    if (!element) return;
    if (element.paused) {
      element.play().catch(() => {});
    } else {
      element.pause();
    }
  }

  function skip(delta) {
    const element = audioRef.current;
    if (!element) return;
    const limit = Number.isFinite(element.duration) ? element.duration : currentTime + delta;
    element.currentTime = Math.min(Math.max(element.currentTime + delta, 0), limit);
  }

  function seek(value) {
    const element = audioRef.current;
    if (!element) return;
    element.currentTime = value;
    setCurrentTime(value);
  }

  function handleLoadedMetadata() {
    const element = audioRef.current;
    const length = Number.isFinite(element.duration) ? element.duration : 0;
    setDuration(length);
    element.playbackRate = rate;

    // Pick up where the listener left off. A position within a few seconds of
    // the end means they finished it, and dropping them there again would be a
    // track that ends the moment it starts.
    if (!resumedRef.current && initialPosition > 0) {
      resumedRef.current = true;
      if (!length || initialPosition < length - RESUME_END_MARGIN_S) {
        element.currentTime = initialPosition;
        setCurrentTime(initialPosition);
      }
    }

    if (autoPlay) {
      element.play().catch(() => {});
      onAutoPlayed?.();
    }
  }

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="nb-player">
      <audio
        ref={audioRef}
        src={src}
        preload="metadata"
        onLoadedMetadata={handleLoadedMetadata}
        onDurationChange={(event) =>
          setDuration(Number.isFinite(event.target.duration) ? event.target.duration : 0)
        }
        onTimeUpdate={(event) => {
          setCurrentTime(event.target.currentTime);
          onPositionChange?.(event.target.currentTime);
        }}
        onPlay={() => setPlaying(true)}
        onPause={(event) => {
          setPlaying(false);
          // Stopping is the moment the position is worth keeping exactly, rather
          // than at whatever the throttled interval last happened to catch.
          onPause?.(event.target.currentTime);
        }}
        onWaiting={() => setBuffering(true)}
        onPlaying={() => setBuffering(false)}
        onCanPlay={() => setBuffering(false)}
        onEnded={() => {
          setPlaying(false);
          onEnded?.();
        }}
      />

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
          style={{ "--range-fill": `${progress}%` }}
          onChange={(event) => seek(Number(event.target.value))}
        />

        <span className="nb-player-time">{formatTime(duration)}</span>
      </div>

      <div className="nb-player-controls">
        <div className="nb-player-group">
          <Button
            className="nb-icon-btn"
            aria-label={`Lùi ${SKIP_SECONDS} giây`}
            title={`Lùi ${SKIP_SECONDS} giây`}
            onClick={() => skip(-SKIP_SECONDS)}
          >
            <SkipBackIcon />
          </Button>

          <Button
            className="nb-player-play"
            variant="primary"
            aria-label={playing ? "Tạm dừng" : "Phát"}
            onClick={togglePlay}
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
            onClick={() => skip(SKIP_SECONDS)}
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
                        setRate(option);
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
            onClick={() => setMuted((value) => !value)}
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
            aria-label="Âm lượng"
            style={{ "--range-fill": `${(muted ? 0 : volume) * 100}%` }}
            onChange={(event) => {
              setVolume(Number(event.target.value));
              setMuted(false);
            }}
          />
        </div>
      </div>
    </div>
  );
}
