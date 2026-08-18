import { useRef } from "react";
import type { ChangeEvent, CSSProperties } from "react";
import { formatTime } from "../audio/formatTime";
import type { MixerSnapshot } from "../audio/types";
import type { UseBgmResult } from "../audio/useBgm";
import { Alert, Button, Select, Switch } from "./ui";

/**
 * Nhạc nền: chọn bản, bật tắt và tua nó, chỉnh riêng âm lượng của nó.
 *
 * Ba điều được nói thẳng trên mặt giao diện, vì cả ba đều là câu hỏi người nghe
 * sẽ hỏi trong ba mươi giây đầu:
 *
 * — Nhạc có bộ nút của riêng nó. Mặc định nó chạy cùng giọng đọc và dừng cùng
 *   giọng đọc, nhưng cái bấm ở đây thắng: tắt nhạc giữa chương mà câu chuyện
 *   vẫn chạy tiếp, hoặc nghe riêng bản nhạc trong lúc câu chuyện đang dừng.
 * — Nhạc tự nhỏ lại khi có tiếng người. To đủ để cảm thấy trong lúc lặng thì
 *   cũng đủ để lấn lời, nên nó lùi lại và trở lại ở khoảng nghỉ.
 * — Nhạc trong ô chọn là do quản trị viên tải lên. Kho ấy có thể còn trống, và
 *   không phải bản nhạc nào cũng phát công khai được vì bản quyền — nên người
 *   nghe luôn mở được bản của chính họ, dù kho có gì hay không.
 */

const MusicIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M9 18V5l11-2v13" />
    <circle cx="6" cy="18" r="3" />
    <circle cx="17" cy="16" r="3" />
  </svg>
);

/* Cùng hình với nút phát của giọng đọc, nhỏ hơn một cỡ: cùng một việc, nhưng
   đây là bè đệm chứ không phải bè chính. */
const playIcon = {
  viewBox: "0 0 24 24",
  fill: "currentColor",
  stroke: "none",
  "aria-hidden": true,
} as const;

const PlayIcon = () => (
  <svg {...playIcon}>
    <path d="M7 4.8v14.4a.8.8 0 0 0 1.22.68l11.5-7.2a.8.8 0 0 0 0-1.36L8.22 4.12A.8.8 0 0 0 7 4.8" />
  </svg>
);

const PauseIcon = () => (
  <svg {...playIcon}>
    <rect x="6" y="4.5" width="4.2" height="15" rx="0.6" />
    <rect x="13.8" y="4.5" width="4.2" height="15" rx="0.6" />
  </svg>
);

export interface BgmMixerPanelProps {
  bgm: UseBgmResult;
  state: MixerSnapshot;
}

export default function BgmMixerPanel({ bgm, state }: BgmMixerPanelProps) {
  const fileRef = useRef<HTMLInputElement | null>(null);
  const { tracks, selected, loading } = bgm;
  const volumePercent = Math.round(state.bgm.volume * 100);

  const { status, currentTime, duration, intent } = state.bgm;
  const sounding = status === "playing";
  const hasTrack = selected !== null;
  const seekMax = duration || 0;
  const seekProgress = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    /*
     * Hai cột chứ không một chồng dọc.
     *
     * Bảng này nằm trong phần mở ra của dải nghe, cạnh những khối chỉ cao chừng
     * ba dòng; xếp dọc thì nó cao gấp đôi chúng, và cả tấm bảng phải mọc ra một
     * thanh cuộn chỉ vì một khối. Cột trái là bản nhạc nào và nó đang ở đâu,
     * cột phải là nghe nó ra sao — cùng một cách chia như mọi chỗ khác trong
     * dải: chọn cái gì, rồi chỉnh cái đó thế nào.
     */
    <div className="bgm-panel">
      <div className="bgm-panel-col">
        <div className="row-between">
          <span className="nb-label">
            <MusicIcon />
            Bản nhạc
          </span>

          {sounding && (
            <span className="bgm-live" role="status">
              đang phát
            </span>
          )}
        </div>

        {state.bgm.error && <Alert tone="error">{state.bgm.error}</Alert>}

        <Select
          aria-label="Chọn nhạc nền"
          value={selected?.id ?? ""}
          disabled={loading}
          onChange={(event: ChangeEvent<HTMLSelectElement>) => bgm.select(event.target.value || null)}
        >
          <option value="">Không có nhạc nền</option>
          {tracks.map((track) => (
            <option key={track.id} value={track.id}>
              {track.origin === "local" ? `${track.title} (từ máy bạn)` : track.title}
            </option>
          ))}
        </Select>

        {/* Không có bản nào trong kho thì nói vì sao, và mời lối đi khác — một ô
            chọn rỗng không giải thích gì là chỗ người ta tưởng trang bị hỏng. */}
        {!loading && tracks.length === 0 && (
          <p className="muted bgm-note">
            Kho nhạc nền của trang đang trống. Bạn vẫn có thể mở một bản nhạc từ máy mình để nghe
            cùng giọng đọc.
          </p>
        )}

        {/*
          Bộ nút của riêng bản nhạc: bật/tắt và tua, không đụng tới giọng đọc.

          Trước đây nhạc nền không có nút nào cả — nó chạy khi giọng đọc chạy, và
          cách duy nhất để tắt nó giữa chương là kéo âm lượng về 0 hoặc bỏ chọn cả
          bản nhạc. Cả hai đều là xoá lựa chọn chứ không phải tắt tiếng, và cả hai
          đều không có đường quay lại chỗ cũ trong bài.
        */}
        <div className="bgm-transport">
          <Button
            className="nb-icon-btn bgm-transport-play"
            disabled={!hasTrack}
            aria-label={sounding ? "Tạm dừng nhạc nền" : "Phát nhạc nền"}
            title={sounding ? "Tạm dừng nhạc nền" : "Phát nhạc nền"}
            onClick={bgm.toggle}
          >
            {sounding ? <PauseIcon /> : <PlayIcon />}
          </Button>

          <span className="nb-player-time">{formatTime(hasTrack ? currentTime : NaN)}</span>

          <input
            className="nb-range"
            type="range"
            min={0}
            max={seekMax}
            step={0.1}
            value={Math.min(currentTime, seekMax)}
            disabled={!hasTrack || duration <= 0}
            aria-label="Vị trí trong bản nhạc nền"
            aria-valuetext={`${formatTime(currentTime)} trên ${formatTime(duration)}`}
            style={{ "--range-fill": `${seekProgress}%` } as CSSProperties}
            onChange={(event) => bgm.seek(Number(event.target.value))}
          />

          <span className="nb-player-time">{formatTime(hasTrack ? duration : NaN)}</span>
        </div>

        {/* Chỉ nói khi có gì đó để nói: mặc định là "theo giọng đọc", và nhắc lại
            điều mặc định ở mỗi lần nhìn là tiếng ồn. */}
        {hasTrack && intent !== "follow" && (
          <p className="bgm-intent muted" role="status">
            {intent === "pause"
              ? "Bạn đã tắt nhạc nền. Chọn lại bản nhạc để nó chạy cùng giọng đọc như cũ."
              : "Nhạc nền đang chạy riêng, không phụ thuộc giọng đọc."}
          </p>
        )}

        <div>
          <Button size="sm" onClick={() => fileRef.current?.click()}>
            Mở nhạc từ máy bạn
          </Button>
          <input
            ref={fileRef}
            type="file"
            accept="audio/*"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) bgm.openLocalFile(file);
              // Chọn lại đúng tệp vừa chọn cũng phải có tác dụng, mà trình duyệt
              // chỉ bắn `change` khi giá trị đổi — nên xoá giá trị đi.
              event.target.value = "";
            }}
          />
        </div>
      </div>

      <div className="bgm-panel-col">
        <div className="bgm-volume">
          <label className="nb-label" htmlFor="bgm-volume">
            Âm lượng nhạc — {volumePercent}%
          </label>
          <input
            id="bgm-volume"
            className="nb-range"
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={state.bgm.volume}
            aria-label="Âm lượng nhạc nền"
            aria-valuetext={`${volumePercent} phần trăm`}
            style={{ "--range-fill": `${volumePercent}%` } as CSSProperties}
            onChange={(event) => bgm.setVolume(Number(event.target.value))}
          />
        </div>

        <Switch
          label="Nhường lời cho giọng đọc"
          hint="Nhạc tự nhỏ lại khi có tiếng người, to lại ở khoảng nghỉ."
          checked={state.bgm.duck}
          onChange={(event: ChangeEvent<HTMLInputElement>) => bgm.setDuck(event.target.checked)}
        />

        <Switch
          label="Lặp lại bản nhạc"
          hint="Hết bài thì quay lại từ đầu, để nhạc còn dài hơn chương."
          checked={state.bgm.loop}
          onChange={(event: ChangeEvent<HTMLInputElement>) => bgm.setLoop(event.target.checked)}
        />

        {/* Rút còn một dòng. Câu dài trước đây kể lại đúng những gì bộ nút bên
            trái và hai công tắc trên đã tự nói ra rồi; giữ lại phần duy nhất
            không nhìn thấy được — cái nết mặc định — và phần ghi công bản nhạc,
            thứ không phải của trang này để mà bỏ đi. */}
        <p className="muted bgm-note">
          Mặc định nhạc chạy và dừng theo giọng đọc.
          {selected?.credit ? ` ${selected.credit}` : ""}
        </p>
      </div>
    </div>
  );
}
