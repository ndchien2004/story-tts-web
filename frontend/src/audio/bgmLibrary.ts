import type { BgmTrack } from "./types";

/**
 * Kho nhạc nền: bản đi kèm trang web, và bản người nghe tự mở từ máy họ.
 *
 * <h3>Vì sao danh sách nằm ở một tệp manifest chứ không nằm trong mã nguồn</h3>
 * Nhạc nền là tài sản, không phải mã. Người dựng trang cần thêm bớt được vài
 * bản nhạc mà không phải dịch lại cả trang web, và cần thay được cả danh sách
 * trên máy chủ đã chạy. Nên mã nguồn chỉ biết đường dẫn tới `bgm/manifest.json`;
 * còn trong đó có gì là việc của thư mục `public/bgm`.
 *
 * <h3>Vì sao vẫn cho mở file từ máy</h3>
 * Vì bản quyền. Không phải bản nhạc nào cũng phát công khai được, nên trang này
 * không đi kèm sẵn bài nào cả — người dựng tự bỏ vào. Cho tới lúc ấy, và cả sau
 * đó, người nghe vẫn mở được bản nhạc của chính họ: nó thành một object URL
 * sống trong tab đang mở, không rời khỏi máy họ, và mất khi tải lại trang.
 */

/** Nơi cất lựa chọn, để lần mở chương sau không phải chọn lại. */
const STORAGE_KEY = "storytts.bgm.v1";

export interface BgmPreferences {
  /** Id bản nhạc trong kho; null là không bật nhạc nền. */
  trackId: string | null;
  volume: number;
  loop: boolean;
  duck: boolean;
}

export const DEFAULT_BGM_PREFERENCES: BgmPreferences = {
  trackId: null,
  volume: 0.3,
  loop: true,
  duck: true,
};

interface ManifestEntry {
  id?: unknown;
  title?: unknown;
  file?: unknown;
  credit?: unknown;
}

/**
 * Đọc danh sách nhạc nền đi kèm trang.
 *
 * Không có tệp manifest, hoặc tệp hỏng, đều trả về danh sách rỗng chứ không
 * báo lỗi: nhạc nền là phần thêm vào, và một trang đọc truyện không có nhạc nền
 * vẫn là một trang đọc truyện hoàn chỉnh.
 */
export async function loadBgmCatalog(signal?: AbortSignal): Promise<BgmTrack[]> {
  const base = import.meta.env.BASE_URL || "/";
  const manifestUrl = `${base}bgm/manifest.json`;

  try {
    const options: RequestInit = signal ? { signal } : {};
    const response = await fetch(manifestUrl, options);
    if (!response.ok) return [];

    const payload: unknown = await response.json();
    const entries = Array.isArray(payload)
      ? payload
      : (payload as { tracks?: unknown })?.tracks;

    if (!Array.isArray(entries)) return [];

    const tracks: BgmTrack[] = [];
    for (const raw of entries as ManifestEntry[]) {
      const track = toTrack(raw, base);
      if (track) tracks.push(track);
    }
    return tracks;
  } catch {
    return [];
  }
}

function toTrack(entry: ManifestEntry, base: string): BgmTrack | null {
  const id = typeof entry.id === "string" ? entry.id.trim() : "";
  const file = typeof entry.file === "string" ? entry.file.trim() : "";
  if (!id || !file) return null;

  const title = typeof entry.title === "string" && entry.title.trim()
    ? entry.title.trim()
    : id;

  // Đường dẫn tuyệt đối trong manifest thì dùng nguyên; còn lại hiểu là tên tệp
  // nằm ngay trong thư mục nhạc nền.
  const url = /^(https?:)?\/\//.test(file) || file.startsWith("/")
    ? file
    : `${base}bgm/${file}`;

  const track: BgmTrack = { id, title, url, origin: "catalog" };
  if (typeof entry.credit === "string" && entry.credit.trim()) {
    track.credit = entry.credit.trim();
  }
  return track;
}

/**
 * Biến một tệp trên máy người dùng thành bản nhạc nền phát được.
 *
 * Người gọi giữ trách nhiệm gọi {@link releaseLocalTrack} khi bỏ bản này đi:
 * object URL giữ nguyên tệp trong bộ nhớ chừng nào chưa được thu hồi.
 */
export function createLocalTrack(file: File): BgmTrack {
  return {
    id: `local:${file.name}:${file.size}`,
    title: file.name.replace(/\.[^.]+$/, ""),
    url: URL.createObjectURL(file),
    origin: "local",
  };
}

export function releaseLocalTrack(track: BgmTrack | null): void {
  if (track?.origin === "local") URL.revokeObjectURL(track.url);
}

export function readBgmPreferences(): BgmPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_BGM_PREFERENCES;

    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return DEFAULT_BGM_PREFERENCES;

    const value = parsed as Partial<BgmPreferences>;
    return {
      // Bản mở từ máy không sống qua một lần tải lại trang, nên đừng ghi nhớ nó:
      // lần sau chỉ còn một cái id trỏ vào hư không.
      trackId: typeof value.trackId === "string" && !value.trackId.startsWith("local:")
        ? value.trackId
        : null,
      volume: typeof value.volume === "number" && value.volume >= 0 && value.volume <= 1
        ? value.volume
        : DEFAULT_BGM_PREFERENCES.volume,
      loop: typeof value.loop === "boolean" ? value.loop : DEFAULT_BGM_PREFERENCES.loop,
      duck: typeof value.duck === "boolean" ? value.duck : DEFAULT_BGM_PREFERENCES.duck,
    };
  } catch {
    return DEFAULT_BGM_PREFERENCES;
  }
}

export function writeBgmPreferences(preferences: BgmPreferences): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  } catch {
    // Trình duyệt ở chế độ riêng tư có thể từ chối ghi. Mất phần ghi nhớ lựa
    // chọn thì không sao; ném lỗi ra giữa lúc người ta đang kéo thanh âm lượng
    // mới là chuyện lớn.
  }
}
