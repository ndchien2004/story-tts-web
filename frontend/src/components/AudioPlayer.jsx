import { useCallback, useEffect, useState } from "react";
import { audioApi } from "../api/endpoints";
import PlayerTransport from "./PlayerTransport";
import { Alert, Badge, Button, Field, Select, Switch } from "./ui";

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

/** Ids the server reports on a generated track. */
const PROVIDER_LABEL = {
  fptai: "FPT.AI",
  elevenlabs: "ElevenLabs",
};

function describeTrack(track) {
  const source = SOURCE_LABEL[track.source] ?? track.source;
  const provider = PROVIDER_LABEL[track.provider];
  return provider ? `${source} · ${provider}` : source;
}

const HeadphonesIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M4 14v-2a8 8 0 0 1 16 0v2" />
    <rect x="2.5" y="13.5" width="4.5" height="7" rx="1.6" />
    <rect x="17" y="13.5" width="4.5" height="7" rx="1.6" />
  </svg>
);

/**
 * Chapter audio panel.
 *
 * Two concerns sit here and are kept visually apart: playing whatever track the
 * chapter already has, and asking the server to synthesise a new one. The
 * synthesis speed below is a property of the generated file, while the playback
 * rate on the transport is applied live in the browser.
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

  const [voice, setVoice] = useState("");
  const [speed, setSpeed] = useState(0);
  // Set when the user asks for narration, so playback can start by itself once
  // the track finishes generating.
  const [playWhenReady, setPlayWhenReady] = useState(false);

  // The voice list is provider-dependent, so the default is whatever the
  // server offers first rather than a hardcoded code.
  useEffect(() => {
    if (!voice && voices.length > 0) {
      setVoice(voices[0].code);
    }
  }, [voice, voices]);

  const handleAutoPlayed = useCallback(() => setPlayWhenReady(false), []);

  async function handleGenerate() {
    clearError();
    setPlayWhenReady(true);
    await requestTts({ voice, speed });
  }

  const ttsAlreadyExists = tracks.some(
    (track) => track.source === "TTS" && track.voice === voice && track.speed === speed,
  );

  // Voices arrive as one flat list; grouping them by provider makes the choice
  // and the fallback relationship obvious in the dropdown.
  const voiceGroups = Object.entries(
    voices.reduce((groups, option) => {
      const key = option.providerName ?? "Khác";
      (groups[key] ??= []).push(option);
      return groups;
    }, {}),
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
        <div className="stack" style={{ gap: "0.5rem" }}>
          <div className="row-between">
            <Badge tone="info">{describeTrack(activeTrack)}</Badge>
            {tracks.length > 1 && (
              <Select
                aria-label="Chọn bản audio"
                style={{ width: "auto", padding: "0.25rem 0.4rem" }}
                value={activeTrack.id}
                onChange={(event) =>
                  setActiveTrack(tracks.find((t) => String(t.id) === event.target.value) ?? null)
                }
              >
                {tracks.map((track) => (
                  <option key={track.id} value={track.id}>
                    {describeTrack(track)}
                  </option>
                ))}
              </Select>
            )}
          </div>

          <PlayerTransport
            // Remounts on a track change so the element starts from a clean
            // state instead of inheriting the previous track's position.
            key={activeTrack.id}
            src={audioApi.streamUrl(activeTrack.streamUrl)}
            autoPlay={playWhenReady}
            onAutoPlayed={handleAutoPlayed}
            onEnded={onTrackEnded}
          />
        </div>
      ) : (
        <div className="nb-player-empty">
          <HeadphonesIcon />
          <span>
            Chương này chưa có bản audio. Chọn giọng đọc rồi bấm “Nghe bằng AI” để hệ thống tạo
            giọng đọc.
          </span>
        </div>
      )}

      <div className="stack" style={{ gap: "0.7rem" }}>
        <Field
          label="Giọng đọc"
          htmlFor="voice"
          hint={
            voiceGroups.length > 1
              ? "Nếu nhà cung cấp đang chọn gặp sự cố, hệ thống tự chuyển sang nhà cung cấp dự phòng."
              : undefined
          }
        >
          <Select
            id="voice"
            value={voice}
            disabled={generating || voices.length === 0}
            onChange={(event) => setVoice(event.target.value)}
          >
            {voices.length === 0 && <option value="">Chưa có nhà cung cấp nào được cấu hình</option>}

            {voiceGroups.map(([providerName, options]) => (
              <optgroup key={providerName} label={providerName}>
                {options.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.name} — {option.gender}, {option.region}
                  </option>
                ))}
              </optgroup>
            ))}
          </Select>
        </Field>

        <Field label="Tốc độ đọc của bản thu" htmlFor="speed">
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
      </div>

      {generating && (
        <Alert tone="info">
          Đang chuyển văn bản thành giọng nói. Chương dài có thể mất một vài phút, bạn vẫn đọc tiếp
          được trong lúc chờ.
        </Alert>
      )}

      <Switch
        label="Nghe liên tục"
        hint={
          hasNextChapter
            ? "Hết chương sẽ tự chuyển sang chương sau."
            : "Đây là chương cuối của truyện."
        }
        checked={autoContinue}
        disabled={!hasNextChapter}
        onChange={onToggleAutoContinue}
      />
    </div>
  );
}
