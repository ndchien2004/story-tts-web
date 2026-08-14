import { bgmApi } from "../api/endpoints";
import type { BgmTrack } from "./types";

/**
 * Kho nhạc nền: bản quản trị viên tải lên, bản đi kèm bản build, và bản người
 * nghe tự mở từ máy họ.
 *
 * <h3>Ba nguồn, theo thứ tự ưu tiên</h3>
 * — **Máy chủ.** Quản trị viên tải nhạc lên trong bảng quản trị và mọi người
 *   nghe thấy ngay ở lần mở trang sau. Đây là nguồn chính: nó là nguồn duy nhất
 *   thêm được nhạc mà không phải build lại frontend.
 * — **`public/bgm/manifest.json`.** Cách cũ, giữ nguyên vì nó vẫn hợp lệ: ai đã
 *   bỏ sẵn vài bản nhạc vào bản build thì chúng vẫn hiện, xếp sau nhạc trên máy
 *   chủ. Không có tệp ấy — trường hợp thường gặp — thì bỏ qua trong im lặng.
 * — **Máy người nghe.** Vì bản quyền: không phải bản nhạc nào cũng phát công
 *   khai được. Bản mở từ máy thành một object URL sống trong tab đang mở, không
 *   rời khỏi máy họ, và mất khi tải lại trang.
 *
 * <h3>Vì sao không nguồn nào được phép làm hỏng phần còn lại</h3>
 * Nhạc nền là phần thêm vào. Máy chủ không trả lời, hay tệp manifest hỏng, đều
 * cho ra danh sách rỗng chứ không ném lỗi — một trang đọc truyện không có nhạc
 * nền vẫn là một trang đọc truyện hoàn chỉnh.
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

/** Một bản nhạc nền như máy chủ trả về (`BgmTrackDto`). */
interface ServerTrack {
  id?: unknown;
  title?: unknown;
  credit?: unknown;
  streamUrl?: unknown;
}

/**
 * Toàn bộ kho nhạc người nghe được chọn: máy chủ trước, bản đi kèm build sau.
 *
 * Hai nguồn được hỏi song song và hỏng độc lập nhau — máy chủ đang ngủ thì
 * nhạc trong bản build vẫn hiện, và ngược lại.
 */
export async function loadBgmCatalog(signal?: AbortSignal): Promise<BgmTrack[]> {
  const [fromServer, fromBundle] = await Promise.all([
    loadServerCatalog(signal),
    loadBundledCatalog(signal),
  ]);

  // Cùng một bản nhạc không thể nằm ở cả hai nguồn (id máy chủ có tiền tố
  // riêng), nên chỉ cần nối lại; máy chủ lên trước vì đó là danh sách được
  // sửa gần đây nhất.
  return [...fromServer, ...fromBundle];
}

/** Nhạc quản trị viên đã tải lên. */
async function loadServerCatalog(signal?: AbortSignal): Promise<BgmTrack[]> {
  try {
    const payload: unknown = await bgmApi.list(signal);
    if (!Array.isArray(payload)) return [];

    const tracks: BgmTrack[] = [];
    for (const raw of payload as ServerTrack[]) {
      const id = typeof raw.id === "number" || typeof raw.id === "string" ? String(raw.id) : "";
      const streamUrl = typeof raw.streamUrl === "string" ? raw.streamUrl : "";
      if (!id || !streamUrl) continue;

      const track: BgmTrack = {
        // Tiền tố để không đụng id của bản trong manifest, và để lựa chọn đã
        // ghi nhớ vẫn trỏ đúng chỗ sau khi hai nguồn được gộp lại.
        id: `server:${id}`,
        title: typeof raw.title === "string" && raw.title.trim() ? raw.title.trim() : `Bản nhạc ${id}`,
        url: bgmApi.streamUrl(streamUrl),
        origin: "catalog",
      };
      if (typeof raw.credit === "string" && raw.credit.trim()) {
        track.credit = raw.credit.trim();
      }
      tracks.push(track);
    }
    return tracks;
  } catch {
    return [];
  }
}

/**
 * Nhạc bỏ sẵn vào `public/bgm` từ trước khi có kho trên máy chủ.
 *
 * Không có tệp manifest — trường hợp thường gặp — thì trả về danh sách rỗng
 * chứ không báo lỗi.
 */
async function loadBundledCatalog(signal?: AbortSignal): Promise<BgmTrack[]> {
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
