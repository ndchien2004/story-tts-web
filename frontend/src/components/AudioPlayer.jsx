import { useEffect, useRef, useState } from "react";
import { audioApi } from "../api/endpoints";
import { Alert, Badge, Button, Field, Select } from "./ui";

const SPEED_OPTIONS = [
  { value: -2, label: "Rất chậm" },
  { value: -1, label: "Chậm" },
  { value: 0, label: "Bình thường" },
  { value: 1, label: "Nhanh" },
  { value: 2, label: "Rất nhanh" },
];

const SOURCE_LABEL = {
  UPLOAD: "🎙️ Bản thu sẵn",
  TTS: "🤖 Giọng AI",
};

/**
 * Chapter audio panel: native playback plus on-demand narration.
 *
 * Playback uses a plain `<audio controls>` element so seeking, volume and the
 * OS media keys behave exactly as users expect; the server serves byte ranges,
 * so dragging the scrubber does not download the whole file.
 */
export default function AudioPlayer({
  audio,
  voices,
  autoContinue,
  onToggleAutoContinue,
  onTrackEnded,
  hasNextChapter,
}) {
  const { tracks, activeTrack, setActiveTrack, generating, error, clearError, requestTts } = audio;

  const audioRef = useRef(null);
  const [voice, setVoice] = useState("banmai");
  const [speed, setSpeed] = useState(0);
  // Set when the user asks for narration, so playback can start by itself once
  // the track finishes generating.
  const [playWhenReady, setPlayWhenReady] = useState(false);

  useEffect(() => {
    if (!activeTrack || !playWhenReady) return;
    const element = audioRef.current;
    if (!element) return;

    // Browsers reject autoplay without a user gesture; the gesture here is the
    // click that requested narration, but a rejection is still harmless.
    element.play().catch(() => {});
    setPlayWhenReady(false);
  }, [activeTrack, playWhenReady]);

  async function handleGenerate() {
    clearError();
    setPlayWhenReady(true);
    const ready = await requestTts({ voice, speed });
    if (!ready) {
      // Still generating; the hook polls and `activeTrack` updates on success.
      return;
    }
  }

  const ttsAlreadyExists = tracks.some(
    (track) => track.source === "TTS" && track.voice === voice && track.speed === speed,
  );

  return (
    <section className="nb-card stack" style={{ gap: "1rem" }}>
      <div className="row-between">
        <h2 style={{ fontSize: "1.15rem" }}>Nghe chương này</h2>
        {activeTrack && <Badge tone="info">{SOURCE_LABEL[activeTrack.source]}</Badge>}
      </div>

      {error && (
        <Alert tone="error">
          {error}
          <div style={{ marginTop: "0.5rem" }}>
            <Button size="sm" onClick={clearError}>
              Đóng
            </Button>
          </div>
        </Alert>
      )}

      {activeTrack ? (
        <audio
          ref={audioRef}
          controls
          preload="metadata"
          src={audioApi.streamUrl(activeTrack.streamUrl)}
          onEnded={onTrackEnded}
          style={{ width: "100%" }}
        >
          Trình duyệt của bạn không hỗ trợ phát audio.
        </audio>
      ) : (
        <p className="muted">
          Chương này chưa có bản audio. Bấm “Nghe bằng AI” để hệ thống tự tạo giọng đọc.
        </p>
      )}

      {tracks.length > 1 && (
        <Field label="Chọn bản audio" htmlFor="track">
          <Select
            id="track"
            value={activeTrack?.id ?? ""}
            onChange={(event) =>
              setActiveTrack(tracks.find((t) => String(t.id) === event.target.value) ?? null)
            }
          >
            {tracks.map((track) => (
              <option key={track.id} value={track.id}>
                {SOURCE_LABEL[track.source]}
                {track.voice ? ` — ${track.voice}` : ""}
              </option>
            ))}
          </Select>
        </Field>
      )}

      <hr style={{ border: "none", borderTop: "2px dashed var(--outline)" }} />

      <div className="filter-bar">
        <Field label="Giọng đọc" htmlFor="voice">
          <Select
            id="voice"
            value={voice}
            disabled={generating}
            onChange={(event) => setVoice(event.target.value)}
          >
            {voices.map((option) => (
              <option key={option.code} value={option.code}>
                {option.name} ({option.gender} · {option.region})
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Tốc độ đọc" htmlFor="speed">
          <Select
            id="speed"
            value={speed}
            disabled={generating}
            onChange={(event) => setSpeed(Number(event.target.value))}
          >
            {SPEED_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <div className="nb-field">
          <span className="nb-label">&nbsp;</span>
          <Button variant="primary" loading={generating} onClick={handleGenerate}>
            {generating
              ? "Đang tạo audio…"
              : ttsAlreadyExists
                ? "Phát bản đã tạo"
                : "🤖 Nghe bằng AI"}
          </Button>
        </div>
      </div>

      {generating && (
        <Alert tone="info">
          Đang chuyển văn bản thành giọng nói. Chương dài có thể mất một vài phút — bạn có thể tiếp
          tục đọc trong lúc chờ.
        </Alert>
      )}

      <label className="row" style={{ gap: "0.5rem", cursor: "pointer" }}>
        <input
          type="checkbox"
          checked={autoContinue}
          onChange={onToggleAutoContinue}
          disabled={!hasNextChapter}
          style={{ width: "1.1rem", height: "1.1rem" }}
        />
        <span style={{ fontWeight: 700 }}>
          Nghe liên tục
          <span className="muted" style={{ fontWeight: 400 }}>
            {" "}
            — hết chương thì tự chuyển sang chương sau
            {!hasNextChapter && " (đây là chương cuối)"}
          </span>
        </span>
      </label>
    </section>
  );
}
