import { useCallback, useEffect, useRef, useState } from "react";
import type { AudioMixerEngine } from "./AudioMixerEngine";
import {
  createLocalTrack,
  loadBgmCatalog,
  readBgmPreferences,
  releaseLocalTrack,
  writeBgmPreferences,
  type BgmPreferences,
} from "./bgmLibrary";
import type { BgmTrack } from "./types";

/**
 * Phần chọn nhạc nền: kho nhạc, lựa chọn đang dùng, và việc nhớ lấy lựa chọn ấy.
 *
 * Tách khỏi {@link useAudioMixer} vì hai thứ này trả lời hai câu hỏi khác nhau.
 * Bộ trộn trả lời "phát cái gì, to nhỏ ra sao"; chỗ này trả lời "có những bản
 * nào để chọn, và người này đã chọn bản nào". Cái sau cần mạng, cần bộ nhớ cục
 * bộ và cần một hộp thoại chọn tệp — không thứ nào trong đó thuộc về một đồ thị
 * âm thanh.
 */

export interface UseBgmResult {
  /** Bản đi kèm trang, cộng bản người nghe vừa mở từ máy (nếu có). */
  tracks: BgmTrack[];
  selected: BgmTrack | null;
  /** Đang tải danh sách; giao diện chờ thay vì hiện "không có bản nào". */
  loading: boolean;
  select: (trackId: string | null) => void;
  openLocalFile: (file: File) => void;
  /** Bật/tắt riêng bản nhạc, không đụng tới giọng đọc. */
  toggle: () => void;
  /** Nhảy tới giây thứ `seconds` của bản nhạc. */
  seek: (seconds: number) => void;
  setVolume: (volume: number) => void;
  setLoop: (loop: boolean) => void;
  setDuck: (duck: boolean) => void;
  preferences: BgmPreferences;
}

export function useBgm(engine: AudioMixerEngine): UseBgmResult {
  const [catalog, setCatalog] = useState<BgmTrack[]>([]);
  const [localTrack, setLocalTrack] = useState<BgmTrack | null>(null);
  const [loading, setLoading] = useState(true);
  const [preferences, setPreferences] = useState<BgmPreferences>(readBgmPreferences);
  const [selected, setSelected] = useState<BgmTrack | null>(null);

  /** Lựa chọn đã cất chỉ được khôi phục một lần, không phải mỗi lần danh sách đổi. */
  const restored = useRef(false);

  // Bộ trộn mới thì bản nhạc phải được nạp lại vào nó — đồ thị âm thanh cũ đã
  // đóng cùng bộ trộn cũ. Xảy ra ở chế độ nghiêm ngặt lúc phát triển.
  useEffect(() => {
    restored.current = false;
  }, [engine]);

  useEffect(() => {
    const controller = new AbortController();

    setLoading(true);
    loadBgmCatalog(controller.signal)
      .then((tracks) => {
        if (controller.signal.aborted) return;
        setCatalog(tracks);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, []);

  /*
   * Khôi phục bản đã chọn lần trước.
   *
   * Không tự phát: bộ trộn chỉ cho nhạc nền chạy khi giọng đọc đang chạy, mà
   * giọng đọc thì cần một cái bấm. Nên chỗ này chỉ nạp sẵn, để lúc người nghe
   * bấm phát thì nhạc đã ở đó rồi chứ không phải chờ tải.
   */
  useEffect(() => {
    if (restored.current || catalog.length === 0) return;
    restored.current = true;

    const wanted = preferences.trackId;
    if (!wanted) return;

    const track = catalog.find((candidate) => candidate.id === wanted);
    if (!track) return;

    setSelected(track);
    void engine.setBgmTrack(track);
  }, [catalog, engine, preferences.trackId]);

  /*
   * Bản mở từ máy sống bằng một object URL, và object URL giữ nguyên tệp trong
   * bộ nhớ cho tới khi được thu hồi. Thu hồi ở đây — một chỗ duy nhất — nên nó
   * đúng cho cả hai lối ra: đổi sang bản khác, và rời hẳn trang.
   */
  useEffect(() => () => releaseLocalTrack(localTrack), [localTrack]);

  const persist = useCallback((patch: Partial<BgmPreferences>) => {
    setPreferences((current) => {
      const next = { ...current, ...patch };
      writeBgmPreferences(next);
      return next;
    });
  }, []);

  const select = useCallback((trackId: string | null) => {
    const track = trackId === null
      ? null
      : [...catalog, ...(localTrack ? [localTrack] : [])]
        .find((candidate) => candidate.id === trackId) ?? null;

    setSelected(track);
    // Chọn nhạc cũng là một thao tác của người dùng, nên nó đủ tư cách mở khoá
    // đường tiếng — nhờ vậy nhạc nền không im lặng chờ tới lần bấm phát sau.
    void engine.resumeContext();
    void engine.setBgmTrack(track);

    // Bản mở từ máy không sống qua một lần tải lại trang, nên không ghi nhớ nó.
    persist({ trackId: track && track.origin === "catalog" ? track.id : null });
  }, [catalog, engine, localTrack, persist]);

  const openLocalFile = useCallback((file: File) => {
    const track = createLocalTrack(file);

    setLocalTrack(track);
    setSelected(track);

    void engine.resumeContext();
    void engine.setBgmTrack(track);
    persist({ trackId: null });
  }, [engine, persist]);

  /*
   * Bật/tắt và tua không được ghi nhớ.
   *
   * Chúng nói về buổi nghe này — "lúc này tôi không muốn nhạc", "cho tôi nghe
   * lại đoạn kia" — chứ không phải về cách người này thích nghe truyện. Mở
   * chương sau mà nhạc vẫn im vì một cái bấm từ hôm trước là một câu đố.
   */
  const toggle = useCallback(() => {
    engine.toggleBgm();
  }, [engine]);

  const seek = useCallback((seconds: number) => {
    engine.seekBgm(seconds);
  }, [engine]);

  const setVolume = useCallback((volume: number) => {
    engine.setBgmVolume(volume);
    persist({ volume });
  }, [engine, persist]);

  const setLoop = useCallback((loop: boolean) => {
    engine.setBgmLoop(loop);
    persist({ loop });
  }, [engine, persist]);

  const setDuck = useCallback((duck: boolean) => {
    engine.setDucking(duck);
    persist({ duck });
  }, [engine, persist]);

  return {
    tracks: localTrack ? [...catalog, localTrack] : catalog,
    selected,
    loading,
    select,
    openLocalFile,
    toggle,
    seek,
    setVolume,
    setLoop,
    setDuck,
    preferences,
  };
}

export default useBgm;
