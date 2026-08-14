/**
 * Mọi hình dạng dữ liệu của phần nghe.
 *
 * Một tệp chứ không rải mỗi nơi một ít: bộ trộn âm thanh, phần tô sáng chữ và
 * các thành phần giao diện đều nói về cùng một buổi nghe, và chúng chỉ khớp
 * nhau chừng nào còn cùng nhìn vào một bộ định nghĩa.
 *
 * Đơn vị dùng chung, khỏi phải đoán:
 * — thời gian tính bằng **giây** (số thực), y như `HTMLMediaElement.currentTime`;
 * — âm lượng là số thực **0…1** tuyến tính, đúng thứ `GainNode.gain` nhận.
 */

/* ------------------------------------------------------------------ */
/* Dữ liệu từ máy chủ                                                  */
/* ------------------------------------------------------------------ */

/**
 * Một bản audio của chương, đúng như `AudioInfoDto` phía máy chủ.
 *
 * `hasTranscript` là thứ quyết định trang đọc có mời "đọc theo giọng đọc" hay
 * không: bản admin thu sẵn và những bản dựng trước khi có tính năng này đều
 * nghe được mà không tô sáng được.
 */
export interface AudioMetadata {
  id: number;
  chapterId: number;
  source: "UPLOAD" | "TTS";
  owner: "LIBRARY" | "SESSION";
  status: "PROCESSING" | "READY" | "FAILED";
  streamUrl: string | null;
  voice: string | null;
  speed: number | null;
  provider: string | null;
  durationSeconds: number | null;
  fileSize: number | null;
  errorMessage: string | null;
  hasTranscript: boolean;
}

/** Một chữ, và lúc giọng đọc đọc tới nó. */
export interface WordTimestamp {
  word: string;
  /** Giây, tính từ đầu file audio. */
  start: number;
  /** Giây; luôn `>= start`. */
  end: number;
  /** Chỉ số ký tự đầu tiên của chữ trong nội dung chương. */
  charStart: number;
  /** Chỉ số ngay sau ký tự cuối cùng (nửa mở, như `String.slice`). */
  charEnd: number;
}

/** Toàn bộ mốc thời gian của một bản audio. */
export interface ChapterTranscript {
  storyId: number | null;
  chapterId: number;
  audioId: number;
  audioUrl: string;
  /**
   * Dấu vân tay nội dung chương lúc bản audio được dựng.
   *
   * Chương sửa chữ sau đó thì mốc cũ trỏ vào những ký tự không còn ở đó nữa.
   * Không dùng để chặn — phần dóng chữ tự phát hiện lệch — nhưng có ích khi cần
   * biết vì sao một chương bỗng dưng tô sáng lệch.
   */
  contentHash: string | null;
  wordCount: number;
  timestamps: WordTimestamp[];
}

/* ------------------------------------------------------------------ */
/* Nhạc nền                                                            */
/* ------------------------------------------------------------------ */

/**
 * Một bản nhạc nền có thể chọn.
 *
 * `catalog` là bản đi kèm trang web, `local` là file người nghe tự mở từ máy
 * họ — bản `local` sống bằng một object URL, nên nó biến mất khi tải lại trang
 * và không bao giờ được ghi vào phần ghi nhớ lựa chọn.
 */
export interface BgmTrack {
  id: string;
  title: string;
  /** Đường dẫn phát được: URL tương đối của trang, hoặc một object URL. */
  url: string;
  /** Ghi công tác giả, nếu bản nhạc đòi hỏi. */
  credit?: string;
  origin: "catalog" | "local";
}

/* ------------------------------------------------------------------ */
/* Trạng thái trình phát                                               */
/* ------------------------------------------------------------------ */

/** Trình phát đang ở đâu trong vòng đời một bản audio. */
export type PlaybackStatus =
  | "idle"
  | "loading"
  | "ready"
  | "playing"
  | "paused"
  | "ended"
  | "error";

/**
 * Đường tiếng đang đi lối nào.
 *
 * `webaudio` là lối chính: cả hai nguồn tiếng đi qua `GainNode` của riêng nó rồi
 * trộn vào một đầu ra. `element` là lối lùi, dùng khi trình duyệt không cho phép
 * đưa file audio của máy chủ vào đồ thị Web Audio (xem `AudioMixerEngine`) —
 * lúc ấy âm lượng chỉnh thẳng trên thẻ `<audio>`, mất phần trộn mượt nhưng vẫn
 * nghe được cả hai nguồn.
 */
export type MixerMode = "webaudio" | "element";

export interface NarrationState {
  status: PlaybackStatus;
  /** Giây. Cập nhật theo sự kiện, không theo từng khung hình — phần tô sáng
   *  đọc thẳng từ engine để khỏi kéo React chạy 60 lần một giây. */
  currentTime: number;
  duration: number;
  /** Bội số tốc độ đọc: 1 là bình thường. */
  rate: number;
  volume: number;
  muted: boolean;
  /** Đang chờ dữ liệu về; nút phát hiện vòng xoay thay vì hình tam giác. */
  buffering: boolean;
  error: string | null;
}

export interface BgmState {
  track: BgmTrack | null;
  status: "idle" | "loading" | "playing" | "paused" | "error";
  volume: number;
  /** Hết bài thì quay lại từ đầu. Mặc định bật — nhạc nền mà dừng giữa chương
   *  thì phần còn lại của chương đột nhiên trống trải. */
  loop: boolean;
  /** Tự hạ nhạc nền xuống trong lúc có giọng đọc. */
  duck: boolean;
  error: string | null;
}

/**
 * Ảnh chụp trạng thái bộ trộn, để React đọc qua `useSyncExternalStore`.
 *
 * Bất biến: mỗi lần có gì đổi là một đối tượng mới, nên so sánh bằng tham chiếu
 * là đủ để biết có cần vẽ lại hay không.
 */
export interface MixerSnapshot {
  mode: MixerMode;
  /** `unavailable` nghĩa là trình duyệt không có Web Audio API. */
  contextState: "suspended" | "running" | "closed" | "unavailable";
  /**
   * Trình duyệt đã từ chối phát vì chưa có thao tác nào của người dùng.
   *
   * Không phải lỗi, mà là một chính sách — nên giao diện nói "bấm để nghe" chứ
   * không nói "đã có lỗi xảy ra".
   */
  blocked: boolean;
  narration: NarrationState;
  bgm: BgmState;
}

/* ------------------------------------------------------------------ */
/* Đồng bộ chữ theo giọng đọc                                          */
/* ------------------------------------------------------------------ */

/**
 * Một chữ đã được dóng vào đúng chỗ của nó trong văn bản đang hiển thị.
 *
 * Khác {@link WordTimestamp} ở chỗ: mốc kia nói về văn bản mà máy chủ đã gửi đi
 * đọc, còn cái này nói về đúng chuỗi ký tự trang đọc đang vẽ ra. Hai chuỗi ấy
 * gần như luôn giống nhau, và phần dóng chữ tồn tại cho chữ "gần như".
 */
export interface KaraokeWord {
  /** Thứ tự trong cả chương, cũng là chỉ số trong mảng và trong `data-w`. */
  index: number;
  text: string;
  start: number;
  end: number;
  /** Vị trí trong chuỗi văn bản đang hiển thị. */
  charStart: number;
  charEnd: number;
  /** Câu (dòng) chứa chữ này; dùng để cuộn và để tô nhạt cả câu đang đọc. */
  lineIndex: number;
}

/** Một câu: đơn vị mà mắt người đọc bám theo, và là thứ được giữ giữa màn hình. */
export interface KaraokeLine {
  index: number;
  start: number;
  end: number;
  /** Chỉ số chữ đầu và chữ cuối của câu, nửa mở. */
  firstWord: number;
  lastWord: number;
}

/** Kết quả dóng chữ: đủ để tô sáng, hoặc đủ để nói vì sao không tô được. */
export interface KaraokeTimelineData {
  words: KaraokeWord[];
  lines: KaraokeLine[];
  /**
   * Tỉ lệ chữ dóng khớp được vào văn bản, 0…1.
   *
   * Dưới ngưỡng thì bản mốc thời gian này thuộc về một phiên bản khác của
   * chương, và tô sáng theo nó là tô sai — xem `alignTranscript`.
   */
  matchRatio: number;
}

/** Vị trí hiện tại của giọng đọc trong văn bản. */
export interface KaraokePosition {
  /** Chỉ số chữ đang được đọc, hoặc -1 khi đang ở khoảng lặng. */
  wordIndex: number;
  lineIndex: number;
}
