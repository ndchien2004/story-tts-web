import { useEffect, useRef, useSyncExternalStore } from "react";
import { AudioMixerEngine, type AudioMixerCallbacks } from "./AudioMixerEngine";
import { readBgmPreferences } from "./bgmLibrary";
import type { MixerSnapshot } from "./types";

/**
 * Một bộ trộn cho mỗi màn hình đọc, và một cầu nối sang React.
 *
 * Bộ trộn là một máy trạng thái sống ngoài React: nó có bộ đếm giờ riêng, có
 * đồ thị âm thanh riêng, và nó phải sống sót qua mọi lần vẽ lại. React chỉ
 * *nhìn* nó, qua `useSyncExternalStore` — đúng công cụ dành cho việc đọc trạng
 * thái của một thứ ở ngoài, và là thứ giữ cho giao diện không bao giờ đọc phải
 * một ảnh chụp cũ hơn thực tế.
 */

/** Nơi cất âm lượng và tốc độ đọc, để buổi nghe sau không phải chỉnh lại. */
const STORAGE_KEY = "storytts.player.v1";

interface PlayerPreferences {
  volume: number;
  muted: boolean;
  rate: number;
}

const DEFAULT_PREFERENCES: PlayerPreferences = { volume: 1, muted: false, rate: 1 };

function readPreferences(): PlayerPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_PREFERENCES;

    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return DEFAULT_PREFERENCES;

    const value = parsed as Partial<PlayerPreferences>;
    return {
      volume: typeof value.volume === "number" && value.volume >= 0 && value.volume <= 1
        ? value.volume
        : DEFAULT_PREFERENCES.volume,
      muted: typeof value.muted === "boolean" ? value.muted : DEFAULT_PREFERENCES.muted,
      rate: typeof value.rate === "number" && value.rate >= 0.25 && value.rate <= 4
        ? value.rate
        : DEFAULT_PREFERENCES.rate,
    };
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

function writePreferences(preferences: PlayerPreferences): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  } catch {
    // Không ghi được thì buổi sau chỉnh lại; không đáng để làm hỏng buổi này.
  }
}

export interface UseAudioMixerResult {
  engine: AudioMixerEngine;
  state: MixerSnapshot;
}

function createEngine(): AudioMixerEngine {
  const player = readPreferences();
  const bgm = readBgmPreferences();

  return new AudioMixerEngine({
    narrationVolume: player.volume,
    narrationMuted: player.muted,
    narrationRate: player.rate,
    bgmVolume: bgm.volume,
    bgmLoop: bgm.loop,
    bgmDuck: bgm.duck,
  });
}

export function useAudioMixer(callbacks: AudioMixerCallbacks = {}): UseAudioMixerResult {
  const engineRef = useRef<AudioMixerEngine | null>(null);
  const pendingDispose = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Dựng đúng một lần, trong một ref chứ không trong state: bộ trộn không phải
  // dữ liệu để vẽ, và mỗi lần nó đổi thân phận là một lần cả trang phải nghĩ lại
  // xem mình đang nói chuyện với ai.
  if (engineRef.current === null) {
    engineRef.current = createEngine();
  }
  const engine = engineRef.current;

  /*
   * Dọn khi rời màn hình đọc — nhưng chậm một nhịp.
   *
   * Chế độ nghiêm ngặt lúc phát triển cố tình tháo rồi gắn lại mọi hiệu ứng
   * ngay lập tức, và một lệnh dọn chạy thẳng ở đó sẽ đóng `AudioContext` của
   * chính bộ trộn vừa dựng — sau đó mọi nút bấm đều im lặng không làm gì, vì
   * `dispose` cũng gỡ luôn danh sách người đang theo dõi trạng thái.
   *
   * Hoãn sang nhịp sau là đủ để phân biệt hai việc: gắn lại ngay thì lệnh dọn
   * bị huỷ ở đầu hiệu ứng, còn rời trang thật thì không có ai huỷ nó cả.
   *
   * Cách cũ ở đây — thấy bộ trộn đã bị dọn thì dựng cái mới và `setState` —
   * không sửa được điều đó mà còn tự nuôi mình: bộ trộn mới làm hiệu ứng chạy
   * lại, lần chạy lại dọn chính nó, rồi lại dựng cái mới nữa.
   */
  useEffect(() => {
    if (pendingDispose.current !== null) {
      clearTimeout(pendingDispose.current);
      pendingDispose.current = null;
    }

    return () => {
      pendingDispose.current = setTimeout(() => {
        pendingDispose.current = null;
        engine.dispose();
        if (engineRef.current === engine) engineRef.current = null;
      }, 0);
    };
  }, [engine]);

  /*
   * Các hàm gọi ngược đi qua một ref.
   *
   * Trang đọc dựng lại chúng ở mỗi lần vẽ, mà gắn lại vào bộ trộn ở mỗi lần vẽ
   * thì vô nghĩa. Ref giữ cho bộ trộn chỉ cần biết một bộ hàm duy nhất, mà bộ
   * hàm ấy vẫn luôn gọi tới phiên bản mới nhất.
   */
  const latest = useRef(callbacks);
  latest.current = callbacks;

  useEffect(() => {
    engine.setCallbacks({
      onEnded: () => latest.current.onEnded?.(),
      onTimeUpdate: (seconds) => latest.current.onTimeUpdate?.(seconds),
      onPause: (seconds) => latest.current.onPause?.(seconds),
    });
  }, [engine]);

  const state = useSyncExternalStore(engine.subscribe, engine.getSnapshot);

  // Ghi lại lựa chọn khi nó đổi, không phải ở mỗi lần vẽ.
  useEffect(() => {
    writePreferences({
      volume: state.narration.volume,
      muted: state.narration.muted,
      rate: state.narration.rate,
    });
  }, [state.narration.volume, state.narration.muted, state.narration.rate]);

  return { engine, state };
}

/** Một trang đọc, một bộ trộn. */
export default useAudioMixer;
