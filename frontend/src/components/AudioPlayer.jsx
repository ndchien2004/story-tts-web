import { useCallback, useEffect, useState } from "react";
import { audioApi } from "../api/endpoints";
import BgmMixerPanel from "./BgmMixerPanel";
import {
  AutoplayNote,
  RateControl,
  SeekBar,
  TransportButtons,
  VolumeControl,
} from "./PlayerTransport";
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

/**
 * Points the way the dock is about to move: up to open, down to close.
 *
 * <p>Cú lật nửa vòng do CSS lo, bám theo `aria-expanded` của chính cái nút —
 * lật bằng `style` thì hình đổi ngay tắp lự, còn tấm bảng dưới nó thì trượt
 * ra trong một phần tư giây, và hai thứ ấy lệch nhau nhìn thấy được.
 */
const CaretIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.4}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
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
 * Nghe liên tục: hết chương này thì sang chương sau.
 *
 * Một mũi tên chạy vào vạch cuối, chứ không phải hai mũi tên vòng — cái vòng
 * đã là nghĩa của "lặp lại bản nhạc" ở bảng nhạc nền, và hai thứ ấy đứng cách
 * nhau có một lần bấm.
 *
 * <p>Tắt thì có một gạch chéo đè lên, đúng như cái loa bị tắt tiếng ngay cạnh
 * đó. Trước kia trạng thái chỉ nói bằng màu, mà màu thì phải có cái để so: một
 * người mở trang lên và thấy nút xám không đọc ra được đấy là "đang tắt" hay
 * chỉ là một nút bình thường. Gạch chéo thì không cần so với gì cả.
 *
 * <p>Gạch ấy đi kèm một nét nền cùng màu với chỗ nó nằm, luồn ngay dưới, nên
 * chỗ nó cắt qua mấy nét kia vẫn hở ra một sợi và cả hình không dính thành một
 * đám.
 */
const ContinuousIcon = ({ on }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M4 8.5h9" />
    <path d="M4 15.5h5" />
    <path d="m13 12.5 3.5 3 3.5-3" />
    <path d="M16.5 15.5V4.5" />
    {!on && (
      <>
        <path className="nb-slash-bed" d="M4.4 3.6 19.6 20.4" strokeWidth={4.2} />
        <path d="M4.4 3.6 19.6 20.4" />
      </>
    )}
  </svg>
);

const DownloadIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M12 3.5v11" />
    <path d="m7.5 10.5 4.5 4.5 4.5-4.5" />
    <path d="M4.5 19.5h15" />
  </svg>
);

/* ------------------------------------------------------------------ */
/* Tải bản audio về máy                                                */
/* ------------------------------------------------------------------ */

/** Ký tự không đặt được vào tên file trên Windows, macOS hay Linux. */
const UNSAFE_FILENAME_CHARS = /[\\/:*?"<>|]+/g;

/**
 * Tên file cho bản tải về: tên truyện và tên chương, đúng thứ người tải sẽ đi
 * tìm trong thư mục Tải xuống ba hôm sau.
 */
function downloadName(storyTitle, chapterTitle, blobType) {
  const parts = [storyTitle, chapterTitle].filter(Boolean).join(" - ") || "chuong-truyen";
  const extension = blobType?.includes("wav") ? "wav" : "mp3";
  return `${parts.replace(UNSAFE_FILENAME_CHARS, " ").replace(/\s+/g, " ").trim()}.${extension}`;
}

/**
 * Nút tải bản audio về máy.
 *
 * <p>Chỉ hiện ra với bản do AI đọc. Bản thu sẵn trong kho là thứ ban quản trị
 * đặt vào trang để nghe tại chỗ, và một nút tải về bên cạnh nó là một lời mời
 * mang đi mà chưa ai đồng ý cho.
 *
 * <p>Việc tải chạy qua tầng API chứ không phải một thẻ `<a download>`: xem
 * {@link audioApi.download} về lý do.
 */
function DownloadButton({ chapterId, track, storyTitle, chapterTitle, onError }) {
  const [downloading, setDownloading] = useState(false);

  const save = useCallback(async () => {
    setDownloading(true);
    onError(null);
    try {
      const blob = await audioApi.download(chapterId, track.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = downloadName(storyTitle, chapterTitle, blob.type);
      document.body.appendChild(link);
      link.click();
      link.remove();
      // Trình duyệt đã cầm lấy dữ liệu ở lời gọi trên; thu lại địa chỉ tạm ngay
      // sau đó, kẻo cả file nằm lại trong bộ nhớ tới lúc rời trang.
      URL.revokeObjectURL(url);
    } catch (err) {
      onError(err.message ?? "Không tải được bản audio này.");
    } finally {
      setDownloading(false);
    }
  }, [chapterId, chapterTitle, onError, storyTitle, track.id]);

  return (
    <Button
      className="nb-icon-btn reader-dock-action"
      aria-label="Tải bản audio về máy"
      title="Tải bản audio về máy"
      loading={downloading}
      onClick={save}
    >
      {!downloading && <DownloadIcon />}
    </Button>
  );
}

/* ------------------------------------------------------------------ */
/* Chương chưa có bản audio                                            */
/* ------------------------------------------------------------------ */

/**
 * What the reader can press when a chapter has no track yet.
 *
 * Every branch here exists to avoid the same failure: a button that looks
 * available and then refuses. A server with no provider configured, a visitor
 * who cannot be charged against an allowance because they have not signed in, a
 * chapter too long to narrate, an allowance already spent — each is said plainly
 * before the press rather than after it.
 *
 * <p>Lời mời chia làm hai chỗ vì thanh dưới đáy chia làm hai: cái bấm được ở
 * lại trên thanh, còn những câu giải thích vì sao bấm được hay không nằm trong
 * phần mở ra ({@link NarrationDetails}) và được tóm lại thành một dòng ngay
 * dưới tên chương ({@link narrationSummary}).
 */
function NarrationAction({ status, isAuthenticated, chapterLength, generating, onRequest }) {
  // The first answer has not arrived; a button that may vanish is worse than a
  // moment of nothing.
  if (!status) return null;

  if (generating) {
    return (
      <div className="reader-dock-waiting" role="status">
        <span className="spinner" aria-hidden="true" />
        <span>Đang tạo audio…</span>
      </div>
    );
  }

  if (!status.enabled) {
    return (
      <div className="reader-dock-waiting">
        <HeadphonesIcon />
        <span>Chương này chưa có bản audio.</span>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <ButtonLink to="/dang-nhap" variant="primary">
        <SparkIcon />
        Đăng nhập để nghe bằng AI
      </ButtonLink>
    );
  }

  const tooLong = status.maxChars > 0 && chapterLength > status.maxChars;
  const outOfGoes = status.remainingToday === 0;

  return (
    <Button variant="primary" onClick={onRequest} disabled={tooLong || outOfGoes}>
      <SparkIcon />
      Nghe bằng AI
    </Button>
  );
}

/**
 * Một dòng dưới tên chương, thay cho tên bản audio khi chưa có bản nào.
 *
 * Thanh có thể đang thu gọn, nên lý do một cái nút bị mờ đi phải nói được trong
 * một dòng — câu đầy đủ nằm trong phần mở ra, nhưng người đọc không nên phải mở
 * ra mới biết vì sao mình bấm không được.
 */
function narrationSummary({ status, isAuthenticated, chapterLength, generating }) {
  if (!status) return null;
  if (generating) return "Đang tạo audio cho chương này…";
  if (!status.enabled) return "Chức năng tự tạo audio đang tạm tắt";
  if (!isAuthenticated) return "Cần đăng nhập để nghe bằng AI";
  if (status.maxChars > 0 && chapterLength > status.maxChars) return "Chương quá dài để tự tạo audio";
  if (status.remainingToday === 0) return "Đã hết lượt tạo audio hôm nay";
  if (status.remainingToday != null) {
    return `Còn ${status.remainingToday}/${status.dailyQuota} lượt tạo audio hôm nay`;
  }
  return "Chương này chưa có bản audio";
}

/**
 * Những câu dài của lời mời, trong phần mở ra.
 *
 * Con số lượt còn lại là lý do cái nút kia được phép tồn tại: dựng một bản audio
 * là một khoản chi thật, nên người bấm nên biết mình đang tiêu cái gì.
 */
function NarrationDetails({ status, isAuthenticated, chapterLength, generating, error }) {
  if (!status) return null;

  if (generating) {
    return (
      <p className="muted reader-dock-note" role="status">
        Đang tạo audio cho chương này… Việc này mất khoảng một đến hai phút. Bạn cứ đọc chữ bình
        thường, xong là audio tự phát.
      </p>
    );
  }

  if (!status.enabled) {
    return (
      <p className="muted reader-dock-note">
        Chương này chưa có bản audio, và chức năng tự tạo audio đang tạm tắt. Mời bạn đọc chữ, hoặc
        quay lại sau khi ban quản trị dựng sẵn bản audio.
      </p>
    );
  }

  if (!isAuthenticated) {
    return (
      <p className="muted reader-dock-note">
        Chương này chưa có bản audio, nhưng bạn có thể nhờ hệ thống đọc bằng AI — chức năng này cần
        đăng nhập.
      </p>
    );
  }

  const tooLong = status.maxChars > 0 && chapterLength > status.maxChars;
  const outOfGoes = status.remainingToday === 0;

  return (
    <>
      {error && <Alert tone="error">{error}</Alert>}

      {tooLong ? (
        <p className="muted reader-dock-note">
          Chương này dài {chapterLength.toLocaleString("vi-VN")} ký tự, vượt mức{" "}
          {status.maxChars.toLocaleString("vi-VN")} ký tự cho phép tự tạo audio. Mời bạn báo với ban
          quản trị để họ dựng sẵn bản audio cho chương.
        </p>
      ) : outOfGoes ? (
        <p className="muted reader-dock-note">
          Bạn đã dùng hết lượt tạo audio hôm nay. Mời bạn quay lại vào ngày mai — những chương đã có
          audio thì vẫn nghe được bình thường.
        </p>
      ) : (
        status.remainingToday != null && (
          <p className="muted reader-dock-note">
            Hôm nay bạn còn {status.remainingToday}/{status.dailyQuota} lượt tạo audio. Chương đã có
            audio thì nghe lại bao nhiêu lần cũng không tính lượt.
          </p>
        )
      )}
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Các công tắc trong phần mở ra                                       */
/* ------------------------------------------------------------------ */

/**
 * Continuous listening, as a button on the bar rather than a switch in the panel.
 *
 * Nó ở ngoài này vì nó là câu hỏi được hỏi đúng vào lúc chương sắp hết — lúc
 * hai tay đang bận, và mở một tấm bảng ra để tìm một cái công tắc là việc không
 * ai làm kịp. Trạng thái nói bằng hai thứ cùng lúc: bật thì nút mang màu nhấn,
 * tắt thì hình có một gạch chéo đè lên và nút chìm như mọi nút khác.
 *
 * <p>Người đang xem một chương chưa có audio cũng cần nó, nên nút này không đợi
 * có bản audio mới hiện ra.
 */
function ContinuousToggle({ checked, disabled, onChange }) {
  return (
    <Button
      className={`nb-icon-btn reader-dock-action ${checked ? "is-on" : ""}`}
      aria-pressed={checked}
      disabled={disabled}
      aria-label="Nghe liên tục"
      title={
        disabled
          ? "Nghe liên tục — đây là chương cuối của truyện."
          : checked
            ? "Nghe liên tục: đang bật. Hết chương sẽ tự chuyển sang chương sau."
            : "Nghe liên tục: đang tắt. Hết chương thì dừng lại."
      }
      onClick={onChange}
    >
      <ContinuousIcon on={checked} />
    </Button>
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
    <>
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
        <p className="muted reader-dock-note">
          Nội dung chương đã được sửa sau khi dựng bản audio này, nên phần bám chữ không còn khớp.
          Bản audio vẫn nghe bình thường.
        </p>
      )}
    </>
  );
}

/** Một khối trong phần mở ra: một cái tên nhỏ, rồi những thứ nó gom lại. */
function DockSection({ title, wide = false, children }) {
  return (
    <section className={`reader-dock-section ${wide ? "is-wide" : ""}`}>
      <h3 className="nb-label">{title}</h3>
      {children}
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Thanh nghe dưới đáy trang đọc                                       */
/* ------------------------------------------------------------------ */

/**
 * Chapter audio dock.
 *
 * A chapter with no recording can still be listened to: the reader asks for
 * narration and the server produces it. What keeps that from being a bill with
 * a button attached is that finished tracks are kept — a chapter is narrated
 * once, and everyone after that is served the stored file for free. Only a
 * genuinely new one spends one of the reader's few goes for the day, which is
 * why the count is shown next to the button rather than discovered by pressing it.
 *
 * <h3>Vì sao nó nằm dưới đáy chứ không ở cột bên</h3>
 * Phần nghe từng là một cột riêng bên phải trang đọc, và cái giá của cột ấy là
 * chiều ngang: nó lấy đi 21rem của trang chữ suốt buổi đọc để giữ sẵn những
 * công tắc phần lớn thời gian không ai đụng tới. Dưới đáy thì ngược lại — cái
 * tay với tới giữa chương nằm trên một dải cao chừng năm phân, còn tất cả những
 * thứ đặt một lần rồi thôi nằm sau một mũi tên:
 *
 * — trên thanh: tên truyện và bản đang nghe, lùi mười giây, phát, tua mười
 *   giây, thanh tua kèm hai mốc giờ, âm lượng, tốc độ đọc, nghe liên tục, nút
 *   tải bản AI về máy;
 * — sau mũi tên: chọn bản audio, bám chữ và tự cuộn, cả bảng nhạc nền.
 *
 * Ranh giới giữa hai chỗ ấy là một câu hỏi: thứ này có bị hỏi giữa lúc đang
 * nghe dở không? Âm lượng, tốc độ đọc và nghe liên tục thì có — chúng được hỏi
 * đúng vào lúc hai tay đang bận, nên chúng ở ngoài. Âm lượng nhạc nền hay việc
 * chọn bản thu nào thì không: đặt một lần rồi thôi.
 *
 * Không có thứ gì rơi mất giữa hai chỗ ấy: mọi thứ cột bên từng có đều còn, chỉ
 * là thứ hiếm dùng thì phải bấm một lần mới thấy.
 */
export default function AudioPlayer({
  audio,
  ttsStatus,
  isAuthenticated,
  chapterLength = 0,
  storyTitle,
  chapterTitle,
  chapterId,
  autoContinue,
  onToggleAutoContinue,
  hasNextChapter,
  engine,
  mixerState,
  bgm,
  karaoke,
}) {
  const { tracks, activeTrack, setActiveTrack, generating, error, requestTts } = audio;

  const [expanded, setExpanded] = useState(false);
  const [downloadError, setDownloadError] = useState(null);

  // Lỗi tải về thuộc về đúng một lần bấm trên đúng một bản audio; đổi bản hay
  // đổi chương thì nó hết chuyện để nói.
  useEffect(() => setDownloadError(null), [activeTrack?.id]);

  /*
   * Dòng dưới tên truyện nói một việc mỗi lúc, và đây là thứ tự giành chỗ:
   * hỏng trước, đang chờ sau, rồi mới tới việc thường ngày là đang nghe bản nào.
   */
  const statusLine =
    downloadError ??
    (activeTrack
      ? describeTrack(activeTrack)
      : narrationSummary({
          status: ttsStatus,
          isAuthenticated,
          chapterLength,
          generating,
        }));

  // Chỉ bản do AI đọc mới tải về được — xem {@link DownloadButton}.
  const downloadable = activeTrack?.source === "TTS";

  return (
    <div className="reader-dock">
      {/*
        Phần mở ra nằm trên thanh chứ không dưới, nên cái nút phát mà ngón tay
        đang đặt lên không xê dịch một ly khi mũi tên được bấm.

        Nó ở lại trong cây DOM cả lúc đóng, chỉ bị cụp xuống cao bằng không, vì
        một khối gắn vào rồi gỡ ra thì không có gì để mà trượt: trình duyệt chỉ
        chuyển tiếp được cái nó thấy ở cả hai đầu. Đóng lại thì `inert` cắt nó
        khỏi phím Tab và khỏi trình đọc màn hình, nên "không thấy" cũng đúng
        nghĩa "không với tới được", chứ không phải một tấm bảng tàng hình mà
        con trỏ vẫn lạc vào được.

        Bên trong không có gì tự chạy: cả bảng nhạc nền lẫn mấy công tắc đều
        chỉ vẽ theo trạng thái được truyền vào, nên giữ chúng dựng sẵn không
        tốn thêm một lượt gọi mạng nào.
      */}
      <div className={`reader-dock-drawer ${expanded ? "is-open" : ""}`} inert={!expanded}>
        <div className="reader-dock-drawer-clip">
          <div className="reader-dock-panel">
            {!activeTrack && (
              <DockSection title="Nghe chương này">
                <NarrationDetails
                  status={ttsStatus}
                  isAuthenticated={isAuthenticated}
                  chapterLength={chapterLength}
                  generating={generating}
                  error={error}
                />
              </DockSection>
            )}

            {activeTrack && (
              <DockSection title="Giọng đọc">
                <div className="reader-dock-tracks">
                  <Badge tone="info">{describeTrack(activeTrack)}</Badge>

                  {tracks.length > 1 && (
                    <Select
                      aria-label="Chọn bản audio"
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

                {downloadError && <Alert tone="error">{downloadError}</Alert>}
              </DockSection>
            )}

            {karaoke?.available && (
              <DockSection title="Bám chữ">
                <KaraokeSwitches karaoke={karaoke} />
              </DockSection>
            )}

            {/* Khối cao nhất trong bảng, nên nó được hai cột và tự chia đôi phần
                ruột: một bảng cao gấp đôi những khối bên cạnh là một bảng phải
                cuộn, mà cuộn trong một tấm bảng vừa mở ra là chỗ người ta bỏ sót
                nửa dưới. */}
            {bgm && (
              <DockSection title="Nhạc nền" wide>
                <BgmMixerPanel bgm={bgm} state={mixerState} />
              </DockSection>
            )}
          </div>
        </div>
      </div>

      <div className="reader-dock-bar">
        <div className="reader-dock-identity">
          <span className="reader-dock-mark" aria-hidden="true">
            <HeadphonesIcon />
          </span>

          <span className="reader-dock-names">
            <strong title={storyTitle}>{storyTitle}</strong>
            {statusLine && (
              <span
                className={`reader-dock-status ${downloadError ? "is-error" : ""}`}
                title={statusLine}
              >
                {statusLine}
              </span>
            )}
          </span>
        </div>

        {/* Giữa thanh: hoặc bộ nút phát của một bản đã có, hoặc lời mời dựng
            một bản. Cùng một chỗ, vì với người đọc thì đó cùng là "chỗ để nghe". */}
        <div className="reader-dock-center">
          {activeTrack ? (
            <>
              <TransportButtons engine={engine} state={mixerState} />
              <SeekBar engine={engine} state={mixerState} />
              <AutoplayNote state={mixerState} />
            </>
          ) : (
            <NarrationAction
              status={ttsStatus}
              isAuthenticated={isAuthenticated}
              chapterLength={chapterLength}
              generating={generating}
              onRequest={requestTts}
            />
          )}
        </div>

        {/*
          Bên phải: những thứ đổi giữa chương mà không đáng phải mở cả bảng ra.
          Âm lượng, tốc độ đọc và nghe liên tục nằm đây chứ không nằm trong phần
          mở ra — chúng được hỏi đúng vào lúc đang nghe dở.

          Âm lượng từng nằm trong tấm bảng, và chỗ ấy sai vì một lý do rất đời:
          tiếng to quá hay nhỏ quá là thứ người ta sửa ngay lập tức, không phải
          thứ đặt một lần lúc bắt đầu chương. Bắt mở một tấm bảng ra mới vặn
          được thì đến lúc vặn xong câu văn đã trôi qua rồi.
        */}
        <div className="reader-dock-actions">
          {activeTrack && (
            <VolumeControl engine={engine} state={mixerState} className="reader-dock-volume" />
          )}

          {activeTrack && <RateControl engine={engine} state={mixerState} />}

          <ContinuousToggle
            checked={autoContinue}
            disabled={!hasNextChapter}
            onChange={onToggleAutoContinue}
          />

          {downloadable && (
            <DownloadButton
              chapterId={chapterId}
              track={activeTrack}
              storyTitle={storyTitle}
              chapterTitle={chapterTitle}
              onError={setDownloadError}
            />
          )}

          <Button
            className="nb-icon-btn reader-dock-action reader-dock-expand"
            aria-expanded={expanded}
            aria-label={expanded ? "Thu gọn phần nghe" : "Mở rộng phần nghe"}
            title={expanded ? "Thu gọn" : "Chọn giọng đọc, nhạc nền, bám chữ…"}
            onClick={() => setExpanded((open) => !open)}
          >
            <CaretIcon />
          </Button>
        </div>
      </div>
    </div>
  );
}
