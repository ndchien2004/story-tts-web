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
  UPLOAD: "Bản thu sẵn",
  TTS: "Giọng AI",
};

/**
 * Chapter audio panel.
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
    await requestTts({ voice, speed });
  }

  const ttsAlreadyExists = tracks.some(
    (track) => track.source === "TTS" && track.voice === voice && track.speed === speed,
  );

  return (
    <div className="stack" style={{ gap: "1rem" }}>
      {error && (
        <Alert tone="error">
          {error}
          <div>
            <Button size="sm" onClick={clearError}>
              Đóng
            </Button>
          </div>
        </Alert>
      )}

      {activeTrack ? (
        <div className="stack" style={{ gap: "0.6rem" }}>
          <Badge tone="info">{SOURCE_LABEL[activeTrack.source]}</Badge>
          <audio
            ref={audioRef}
            className="audio-element"
            controls
            preload="metadata"
            src={audioApi.streamUrl(activeTrack.streamUrl)}
            onEnded={onTrackEnded}
          >
            Trình duyệt của bạn không hỗ trợ phát audio.
          </audio>
        </div>
      ) : (
        <p className="muted">
          Chương này chưa có bản audio. Chọn giọng đọc rồi bấm “Nghe bằng AI” để hệ thống tạo giọng
          đọc cho chương.
        </p>
      )}

      {tracks.length > 1 && (
        <Field label="Bản audio" htmlFor="track">
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

      <Field label="Giọng đọc" htmlFor="voice">
        <Select
          id="voice"
          value={voice}
          disabled={generating}
          onChange={(event) => setVoice(event.target.value)}
        >
          {voices.map((option) => (
            <option key={option.code} value={option.code}>
              {option.name} — {option.gender}, {option.region}
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

      <Button variant="primary" block loading={generating} onClick={handleGenerate}>
        {generating ? "Đang tạo audio…" : ttsAlreadyExists ? "Phát bản đã tạo" : "Nghe bằng AI"}
      </Button>

      {generating && (
        <Alert tone="info">
          Đang chuyển văn bản thành giọng nói. Chương dài có thể mất một vài phút, bạn vẫn đọc tiếp
          được trong lúc chờ.
        </Alert>
      )}

      <label className="row" style={{ gap: "0.5rem", cursor: "pointer", alignItems: "flex-start" }}>
        <input
          type="checkbox"
          className="nb-checkbox"
          checked={autoContinue}
          onChange={onToggleAutoContinue}
          disabled={!hasNextChapter}
          style={{ marginTop: "0.25rem" }}
        />
        <span style={{ flex: "1 1 0", minWidth: 0 }}>
          <strong>Nghe liên tục</strong>
          <br />
          <span className="muted" style={{ fontSize: "0.85rem" }}>
            {hasNextChapter
              ? "Hết chương sẽ tự chuyển sang chương sau."
              : "Đây là chương cuối của truyện."}
          </span>
        </span>
      </label>
    </div>
  );
}
