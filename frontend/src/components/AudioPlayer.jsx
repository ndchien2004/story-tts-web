import { audioApi } from "../api/endpoints";
import PlayerTransport from "./PlayerTransport";
import { Alert, Badge, Button, ButtonLink, Select, Switch } from "./ui";

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

const SparkIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M12 3.2l1.8 4.9 4.9 1.8-4.9 1.8L12 16.6l-1.8-4.9L5.3 9.9l4.9-1.8z" />
    <path d="M18.5 15.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" />
  </svg>
);

/**
 * What stands in for the player when a chapter has no track yet.
 *
 * Every branch here exists to avoid the same failure: a button that looks
 * available and then refuses. A server with no provider configured, a visitor
 * who cannot be charged against an allowance because they have not signed in, a
 * chapter too long to narrate, an allowance already spent — each is said plainly
 * before the press rather than after it.
 */
function NarrationOffer({ status, isAuthenticated, chapterLength, generating, error, onRequest }) {
  // The first answer has not arrived; a button that may vanish is worse than a
  // moment of nothing.
  if (!status) return null;

  if (generating) {
    return (
      <div className="nb-player-empty">
        <span className="spinner" aria-hidden="true" />
        <span role="status">
          Đang tạo audio cho chương này… Việc này mất khoảng một đến hai phút. Bạn cứ đọc chữ bình
          thường, xong là audio tự phát.
        </span>
      </div>
    );
  }

  if (!status.enabled) {
    return (
      <div className="nb-player-empty">
        <HeadphonesIcon />
        <span>
          Chương này chưa có bản audio, và chức năng tự tạo audio đang tạm tắt. Mời bạn đọc chữ, hoặc
          quay lại sau khi ban quản trị dựng sẵn bản audio.
        </span>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="nb-player-empty">
        <HeadphonesIcon />
        <div className="stack" style={{ gap: "0.6rem", alignItems: "flex-start" }}>
          <span>
            Chương này chưa có bản audio, nhưng bạn có thể nhờ hệ thống đọc bằng AI — chức năng này
            cần đăng nhập.
          </span>
          <ButtonLink to="/dang-nhap" variant="primary" size="sm">
            Đăng nhập để nghe bằng AI
          </ButtonLink>
        </div>
      </div>
    );
  }

  const tooLong = status.maxChars > 0 && chapterLength > status.maxChars;
  const outOfGoes = status.remainingToday === 0;

  return (
    <div className="stack" style={{ gap: "0.75rem" }}>
      {error && <Alert tone="error">{error}</Alert>}

      <div className="nb-player-empty">
        <HeadphonesIcon />
        <span>Chương này chưa có bản audio — nhờ hệ thống đọc giúp bạn nhé.</span>
      </div>

      <Button
        variant="primary"
        className="nb-narration-request"
        onClick={onRequest}
        disabled={tooLong || outOfGoes}
      >
        <SparkIcon />
        Nghe bằng AI
      </Button>

      {/* Con số này là lý do nút trên có thể tồn tại: dựng một bản audio là một
          khoản chi thật, nên người bấm nên biết mình đang tiêu cái gì. */}
      {tooLong ? (
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Chương này dài {chapterLength.toLocaleString("vi-VN")} ký tự, vượt mức{" "}
          {status.maxChars.toLocaleString("vi-VN")} ký tự cho phép tự tạo audio. Mời bạn báo với ban
          quản trị để họ dựng sẵn bản audio cho chương.
        </p>
      ) : outOfGoes ? (
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Bạn đã dùng hết lượt tạo audio hôm nay. Mời bạn quay lại vào ngày mai — những chương đã có
          audio thì vẫn nghe được bình thường.
        </p>
      ) : (
        status.remainingToday != null && (
          <p className="muted" style={{ fontSize: "0.85rem" }}>
            Hôm nay bạn còn {status.remainingToday}/{status.dailyQuota} lượt tạo audio. Chương đã có
            audio thì nghe lại bao nhiêu lần cũng không tính lượt.
          </p>
        )
      )}
    </div>
  );
}

/**
 * Chapter audio panel.
 *
 * A chapter with no recording can still be listened to: the reader asks for
 * narration and the server produces it. What keeps that from being a bill with
 * a button attached is that finished tracks are kept — a chapter is narrated
 * once, and everyone after that is served the stored file for free. Only a
 * genuinely new one spends one of the reader's few goes for the day, which is
 * why the count is shown next to the button rather than discovered by pressing it.
 *
 * The panel therefore has four faces: a player, an offer, a wait, and — for a
 * server with narration switched off or a visitor who has not signed in — a
 * plain explanation of why there is nothing to press.
 */
export default function AudioPlayer({
  audio,
  ttsStatus,
  isAuthenticated,
  chapterLength = 0,
  autoContinue,
  onToggleAutoContinue,
  onTrackEnded,
  onAutoPlayed,
  autoPlay = false,
  hasNextChapter,
  initialPosition = 0,
  onPositionChange,
  onPause,
}) {
  const { tracks, activeTrack, setActiveTrack, generating, error, requestTts } = audio;

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

          {/*
            The transport is wrapped so it can be lifted out of the flow on a
            narrow screen and pinned to the bottom of the viewport, the way a
            music app does it. In the side column it reads as part of the panel;
            once the panel drops below a long chapter, a player you have to
            scroll to the end of the text to reach is a player you cannot use
            while reading. Same markup either way — only CSS moves it.
          */}
          <div className="reader-player">
            <PlayerTransport
              // Remounts on a track change so the element starts from a clean
              // state instead of inheriting the previous track's position.
              key={activeTrack.id}
              src={audioApi.streamUrl(activeTrack.streamUrl)}
              // Only ever true when the previous chapter finished playing, or
              // when the reader asked for this narration — opening a chapter
              // directly never starts making noise on its own.
              autoPlay={autoPlay}
              onAutoPlayed={onAutoPlayed}
              onEnded={onTrackEnded}
              initialPosition={initialPosition}
              onPositionChange={onPositionChange}
              onPause={onPause}
            />
          </div>
        </div>
      ) : (
        <NarrationOffer
          status={ttsStatus}
          isAuthenticated={isAuthenticated}
          chapterLength={chapterLength}
          generating={generating}
          error={error}
          onRequest={requestTts}
        />
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
