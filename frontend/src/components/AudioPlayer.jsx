import { useState } from "react";
import BgmMixerPanel from "./BgmMixerPanel";
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

/** Points the way the dock is about to move: up to open, down to close. */
const CaretIcon = ({ down }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.4}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    style={down ? { transform: "rotate(180deg)" } : undefined}
  >
    <path d="m5 15 7-7 7 7" />
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
 * Continuous listening.
 *
 * Declared once and placed twice — inside the dock when there is a track to
 * dock, and in the plain flow when there is not. Both spots need it: someone
 * looking at a chapter with no audio yet may well want the next one to follow
 * on before they ask for this one to be narrated.
 */
function ContinuousSwitch({ checked, disabled, onChange }) {
  return (
    <Switch
      label="Nghe liên tục"
      hint={disabled ? "Đây là chương cuối của truyện." : "Hết chương sẽ tự chuyển sang chương sau."}
      checked={checked}
      disabled={disabled}
      onChange={onChange}
    />
  );
}

/**
 * Bám chữ theo giọng đọc.
 *
 * Hai công tắc chứ không phải một, vì đó là hai mong muốn khác nhau: có người
 * muốn thấy chữ sáng lên nhưng tự cuộn lấy, và trên màn hình rộng thì cả chương
 * nằm gọn trong tầm mắt nên tự cuộn chỉ tổ làm trang chữ nhúc nhích.
 *
 * Cả khối biến mất khi bản audio đang nghe không có mốc thời gian — bản admin
 * thu sẵn chẳng hạn. Một công tắc bật lên rồi không có gì xảy ra là lời hứa suông.
 */
function KaraokeSwitches({ karaoke }) {
  if (!karaoke?.available) {
    return null;
  }

  return (
    <div className="stack" style={{ gap: "0.75rem" }}>
      <Switch
        label="Bám chữ theo giọng đọc"
        hint={
          karaoke.loading
            ? "Đang tải mốc thời gian của bản đọc…"
            : "Chữ sáng lên đúng lúc giọng đọc đọc tới."
        }
        checked={karaoke.enabled}
        onChange={(event) => karaoke.onToggle(event.target.checked)}
      />

      <Switch
        label="Tự cuộn theo"
        hint="Giữ dòng đang đọc ở giữa màn hình."
        checked={karaoke.autoScroll}
        disabled={!karaoke.enabled}
        onChange={(event) => karaoke.onToggleAutoScroll(event.target.checked)}
      />

      {karaoke.stale && (
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Nội dung chương đã được sửa sau khi dựng bản audio này, nên phần bám chữ không còn khớp.
          Bản audio vẫn nghe bình thường.
        </p>
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
  hasNextChapter,
  engine,
  mixerState,
  bgm,
  karaoke,
}) {
  const { tracks, activeTrack, setActiveTrack, generating, error, requestTts } = audio;

  /*
   * Whether the docked bar is showing everything it has.
   *
   * Only ever consulted on a narrow screen. In the side column the panel has
   * the room to show the lot at once, so the stylesheet ignores this and the
   * button that flips it is not drawn at all.
   */
  const [expanded, setExpanded] = useState(false);

  if (!activeTrack) {
    return (
      <div className="stack" style={{ gap: "1rem" }}>
        <NarrationOffer
          status={ttsStatus}
          isAuthenticated={isAuthenticated}
          chapterLength={chapterLength}
          generating={generating}
          error={error}
          onRequest={requestTts}
        />

        <ContinuousSwitch
          checked={autoContinue}
          disabled={!hasNextChapter}
          onChange={onToggleAutoContinue}
        />
      </div>
    );
  }

  return (
    /*
     * Everything about listening lives in one box.
     *
     * It has to: on a narrow screen this box leaves the flow and pins itself to
     * the bottom of the viewport, and anything left outside would be stranded
     * at the end of a long chapter where nobody can reach it mid-read. So the
     * track picker and the continuous-listening switch moved in here beside the
     * transport, and the bar decides how much of itself to show.
     */
    <div className={`reader-player ${expanded ? "is-expanded" : ""}`}>
      <div className="reader-player-bar">
        {/* Không còn `key` để dựng lại: bản audio nào đang phát là việc của bộ
            trộn, và nó đổi bản bằng cách nạp nguồn mới vào cùng một đồ thị âm
            thanh — dựng lại khối này chỉ làm mất luôn đồ thị ấy. */}
        <PlayerTransport engine={engine} state={mixerState} />

        {/* Hidden by the stylesheet on a wide screen, where there is nothing
            left folded away for it to unfold. */}
        <button
          type="button"
          className="reader-player-expand"
          aria-expanded={expanded}
          aria-label={expanded ? "Thu gọn trình phát" : "Mở rộng trình phát"}
          title={expanded ? "Thu gọn" : "Tốc độ đọc, nghe liên tục"}
          onClick={() => setExpanded((open) => !open)}
        >
          <CaretIcon down={expanded} />
        </button>
      </div>

      <div className="reader-player-more">
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

        <ContinuousSwitch
          checked={autoContinue}
          disabled={!hasNextChapter}
          onChange={onToggleAutoContinue}
        />

        <KaraokeSwitches karaoke={karaoke} />

        {bgm && <BgmMixerPanel bgm={bgm} state={mixerState} />}
      </div>
    </div>
  );
}
