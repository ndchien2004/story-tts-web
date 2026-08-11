import { audioApi } from "../api/endpoints";
import PlayerTransport from "./PlayerTransport";
import { Badge, Select, Switch } from "./ui";

const SOURCE_LABEL = {
  UPLOAD: "Bản thu sẵn",
  TTS: "Giọng đọc máy",
};

function describeTrack(track) {
  return SOURCE_LABEL[track.source] ?? track.source;
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
 * Play only. Preparing narration belongs to the admin console — a reader-side
 * button meant one file on disk and one paid API call per curious visitor, and
 * nobody was left holding the list of what had been produced.
 *
 * So a chapter either has a track or it does not, and when it does not the
 * panel says so plainly rather than offering a button that creates one.
 */
export default function AudioPlayer({
  audio,
  autoContinue,
  onToggleAutoContinue,
  onTrackEnded,
  onAutoPlayed,
  autoPlay = false,
  hasNextChapter,
}) {
  const { tracks, activeTrack, setActiveTrack } = audio;

  return (
    <div className="stack" style={{ gap: "1rem" }}>
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
            // Only ever true when the previous chapter finished playing, so
            // opening a chapter directly never starts making noise on its own.
            autoPlay={autoPlay}
            onAutoPlayed={onAutoPlayed}
            onEnded={onTrackEnded}
          />
        </div>
      ) : (
        <div className="nb-player-empty">
          <HeadphonesIcon />
          <span>
            Chương này chưa có bản audio. Ban quản trị đang chuẩn bị, mời bạn đón chờ — trong lúc đó
            vẫn đọc chữ bình thường.
          </span>
        </div>
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
