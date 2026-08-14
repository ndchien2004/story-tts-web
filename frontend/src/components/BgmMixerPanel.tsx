import { useRef } from "react";
import type { ChangeEvent, CSSProperties } from "react";
import type { MixerSnapshot } from "../audio/types";
import type { UseBgmResult } from "../audio/useBgm";
import { Alert, Button, Select, Switch } from "./ui";

/**
 * Nhạc nền: chọn bản, chỉnh riêng âm lượng của nó.
 *
 * Ba điều được nói thẳng trên mặt giao diện, vì cả ba đều là câu hỏi người nghe
 * sẽ hỏi trong ba mươi giây đầu:
 *
 * — Nhạc chỉ chạy khi giọng đọc chạy. Nhạc nền tiếp tục sau khi câu chuyện dừng
 *   lại là một thứ bị bỏ quên đang phát trong một tab nào đó.
 * — Nhạc tự nhỏ lại khi có tiếng người. To đủ để cảm thấy trong lúc lặng thì
 *   cũng đủ để lấn lời, nên nó lùi lại và trở lại ở khoảng nghỉ.
 * — Trang này không đi kèm bản nhạc nào. Nhạc có bản quyền, nên thư mục nhạc để
 *   trống cho người dựng trang tự bỏ vào — và trong lúc chờ, người nghe mở được
 *   bản của chính họ.
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

export interface BgmMixerPanelProps {
  bgm: UseBgmResult;
  state: MixerSnapshot;
}

export default function BgmMixerPanel({ bgm, state }: BgmMixerPanelProps) {
  const fileRef = useRef<HTMLInputElement | null>(null);
  const { tracks, selected, loading } = bgm;
  const volumePercent = Math.round(state.bgm.volume * 100);

  return (
    <div className="bgm-panel stack">
      <div className="row-between">
        <span className="nb-label">
          <MusicIcon />
          Nhạc nền
        </span>

        {state.bgm.status === "playing" && (
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
        onChange={(event: ChangeEvent<HTMLSelectElement>) =>
          bgm.select(event.target.value || null)}
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
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Trang chưa có sẵn bản nhạc nền nào. Bạn có thể mở một bản nhạc từ máy mình để nghe cùng
          giọng đọc.
        </p>
      )}

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

      <p className="muted" style={{ fontSize: "0.82rem" }}>
        Nhạc nền chạy cùng giọng đọc và dừng khi bạn tạm dừng.
        {selected?.credit ? ` ${selected.credit}` : ""}
      </p>
    </div>
  );
}
