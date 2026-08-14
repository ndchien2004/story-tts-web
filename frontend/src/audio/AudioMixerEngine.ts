import type {
  BgmState,
  BgmTrack,
  MixerMode,
  MixerSnapshot,
  NarrationState,
} from "./types";

/**
 * Bộ trộn: giọng đọc và nhạc nền, hai đường tiếng, một đầu ra.
 *
 * <h3>Vì sao là Web Audio chứ không phải hai thẻ `<audio>`</h3>
 * Hai thẻ audio thì mỗi thẻ có đồng hồ riêng và trình duyệt tự quyết định lúc
 * nào thức tỉnh cái nào. Trên điện thoại — nơi hệ điều hành ngắt và khôi phục
 * từng luồng tiếng theo ý nó — hai đồng hồ ấy trôi xa nhau, và cái mà người
 * nghe nhận ra là nhạc nền tự nhiên to lên đúng lúc giọng đọc đang nói. Ở đây
 * cả hai nguồn đi vào **một** `AudioContext`, mỗi nguồn qua `GainNode` của
 * riêng nó rồi trộn vào một đầu ra duy nhất: một đồng hồ, một đường ra, và âm
 * lượng đổi bằng đường dốc chứ không bằng bậc thang.
 *
 * <h3>Vì sao vẫn còn một thẻ `<audio>` bên trong</h3>
 * Chương audio dài hàng chục phút. Tải trọn file rồi mới giải mã thành
 * `AudioBuffer` nghĩa là người nghe ngồi chờ vài chục MB trước khi nghe được
 * chữ đầu tiên, và tua thì phải tải lại từ đầu. Thẻ `<audio>` biết phát dần và
 * biết dùng HTTP Range để tua — nên nó vẫn là thứ đọc file, chỉ có điều đầu ra
 * của nó được đưa vào đồ thị Web Audio qua `MediaElementAudioSourceNode` thay
 * vì ra thẳng loa. Nhạc nền thì ngược lại: file ngắn, cần lặp liền mạch không
 * có khe hở, nên nó được giải mã trọn vào bộ nhớ.
 *
 * <h3>Lối lùi, và vì sao bắt buộc phải có</h3>
 * `createMediaElementSource` trên một file khác nguồn gốc mà không có CORS sẽ
 * không báo lỗi — nó **im lặng** cho ra toàn số 0. Máy chủ audio của trang này
 * nằm ở một origin khác trình duyệt, nên đây không phải một khả năng lý thuyết.
 * Vì thế trước lần tải đầu tiên có một phép thử CORS hai byte; hỏng thì cả bộ
 * trộn chuyển sang chế độ `element`: âm lượng chỉnh thẳng trên thẻ audio, nhạc
 * nền chạy bằng một thẻ audio thứ hai. Mất phần trộn mượt, giữ lại phần nghe
 * được — đúng thứ tự ưu tiên.
 */

/** Nhạc nền hạ xuống còn ngần này khi giọng đọc đang nói. */
const DUCK_FACTOR = 0.35;

/** Mọi thay đổi âm lượng đều đi theo đường dốc dài ngần này, tính bằng giây. */
const VOLUME_RAMP_S = 0.12;

/** Nhạc nền vào và ra bằng một quãng chuyển dài hơn, để không có tiếng bụp. */
const BGM_FADE_S = 0.6;

/**
 * Ngoại suy đồng hồ tối đa ngần này giây.
 *
 * Giữa hai lần `currentTime` của thẻ audio nhích lên, phần tô sáng vẫn cần một
 * con số cho mỗi khung hình, nên nó được suy ra từ đồng hồ tường. Nhưng khi
 * tiếng ngừng vì đang chờ dữ liệu thì `currentTime` cũng ngừng — và ngoại suy
 * mãi sẽ đẩy phần tô sáng chạy trước giọng đọc. Trần này là chỗ nó dừng lại.
 */
const MAX_EXTRAPOLATION_S = 0.25;

/** Vượt quá ngần này thì coi như đã nghe hết bài, không phải chỗ để nghe tiếp. */
const RESUME_END_MARGIN_S = 5;

export interface AudioMixerCallbacks {
  /** Bản audio chạy hết. Dùng cho "nghe liên tục sang chương sau". */
  onEnded?: () => void;
  /** Vị trí nghe đã nhích lên, theo nhịp sự kiện của thẻ audio (vài lần mỗi giây). */
  onTimeUpdate?: (seconds: number) => void;
  /** Người nghe bấm dừng, hoặc rời trang giữa chừng — kèm vị trí chính xác. */
  onPause?: (seconds: number) => void;
}

/** Những lựa chọn được ghi nhớ giữa các lần mở chương, truyền vào lúc dựng. */
export interface AudioMixerInit {
  narrationVolume?: number;
  narrationMuted?: boolean;
  narrationRate?: number;
  bgmVolume?: number;
  bgmLoop?: boolean;
  bgmDuck?: boolean;
}

export interface LoadNarrationOptions {
  /** Giây; nghe tiếp từ chỗ lần trước bỏ dở. */
  startAt?: number;
  /** Phát ngay khi tải xong. Chỉ đúng khi có một thao tác của người dùng đứng sau. */
  autoPlay?: boolean;
}

function hasWebAudio(): boolean {
  return typeof window !== "undefined"
    && (typeof window.AudioContext !== "undefined"
      || typeof (window as unknown as { webkitAudioContext?: unknown }).webkitAudioContext
        !== "undefined");
}

function createContext(): AudioContext {
  const Ctor = window.AudioContext
    ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  return new Ctor();
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

const INITIAL_NARRATION: NarrationState = {
  status: "idle",
  currentTime: 0,
  duration: 0,
  rate: 1,
  volume: 1,
  muted: false,
  buffering: false,
  error: null,
};

const INITIAL_BGM: BgmState = {
  track: null,
  status: "idle",
  volume: 0.35,
  loop: true,
  duck: true,
  error: null,
};

/**
 * Phép thử CORS, nhớ theo origin.
 *
 * Tĩnh chứ không theo từng bộ trộn: câu trả lời là thuộc tính của máy chủ, và
 * chuyển chương thì dựng bộ trộn mới — hỏi lại mỗi lần chỉ tổ thêm một vòng
 * mạng trước khi phát được chữ đầu tiên.
 */
const corsProbes = new Map<string, Promise<boolean>>();

export class AudioMixerEngine {
  private state: MixerSnapshot;

  private readonly listeners = new Set<() => void>();

  private callbacks: AudioMixerCallbacks = {};

  private element: HTMLAudioElement | null = null;

  private context: AudioContext | null = null;

  private narrationSource: MediaElementAudioSourceNode | null = null;

  private narrationGain: GainNode | null = null;

  private bgmGain: GainNode | null = null;

  /* --- Nhạc nền, đường Web Audio --- */
  private bgmBuffer: AudioBuffer | null = null;

  private bgmSource: AudioBufferSourceNode | null = null;

  private bgmSourceGain: GainNode | null = null;

  /** Đã phát tới đâu trong bản nhạc, để dừng rồi chạy tiếp không nhảy về đầu. */
  private bgmOffset = 0;

  /** Mốc `context.currentTime` ứng với `bgmOffset = 0` của lần phát hiện tại. */
  private bgmStartedAt = 0;

  /* --- Nhạc nền, đường lùi bằng thẻ audio --- */
  private bgmElement: HTMLAudioElement | null = null;

  private readonly bufferCache = new Map<string, AudioBuffer>();

  /** Chống chạy đua: chỉ lần tải mới nhất mới được phép ghi vào trạng thái. */
  private loadToken = 0;

  private bgmToken = 0;

  /** Giây cần nhảy tới ngay khi biết được độ dài bản audio. */
  private resumeAt = 0;

  private resumeHonoured = true;

  private autoPlayPending = false;

  /* --- Ngoại suy đồng hồ cho phần tô sáng --- */
  private sampledMediaTime = 0;

  private sampledWallTime = 0;

  private disposed = false;

  constructor(initial: AudioMixerInit = {}) {
    this.state = {
      mode: "webaudio",
      contextState: hasWebAudio() ? "suspended" : "unavailable",
      blocked: false,
      narration: {
        ...INITIAL_NARRATION,
        volume: clamp01(initial.narrationVolume ?? INITIAL_NARRATION.volume),
        muted: initial.narrationMuted ?? INITIAL_NARRATION.muted,
        rate: initial.narrationRate ?? INITIAL_NARRATION.rate,
      },
      bgm: {
        ...INITIAL_BGM,
        volume: clamp01(initial.bgmVolume ?? INITIAL_BGM.volume),
        loop: initial.bgmLoop ?? INITIAL_BGM.loop,
        duck: initial.bgmDuck ?? INITIAL_BGM.duck,
      },
    };
  }

  /* ---------------------------------------------------------------- */
  /* Đăng ký theo dõi trạng thái                                       */
  /* ---------------------------------------------------------------- */

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): MixerSnapshot => this.state;

  setCallbacks(callbacks: AudioMixerCallbacks): void {
    this.callbacks = callbacks;
  }

  private emit(): void {
    for (const listener of this.listeners) listener();
  }

  private patchNarration(patch: Partial<NarrationState>): void {
    this.state = { ...this.state, narration: { ...this.state.narration, ...patch } };
    this.emit();
  }

  private patchBgm(patch: Partial<BgmState>): void {
    this.state = { ...this.state, bgm: { ...this.state.bgm, ...patch } };
    this.emit();
  }

  private patchRoot(patch: Partial<Omit<MixerSnapshot, "narration" | "bgm">>): void {
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  /* ---------------------------------------------------------------- */
  /* Đồ thị âm thanh                                                   */
  /* ---------------------------------------------------------------- */

  /**
   * Dựng đồ thị nếu chưa có, và nối thẻ audio vào đó.
   *
   * Gọi từ trong một thao tác của người dùng: `AudioContext` dựng ngoài thao tác
   * nào thì sinh ra ở trạng thái treo, và mọi trình duyệt đều ghi một dòng cảnh
   * báo vào console về việc ấy.
   */
  private ensureGraph(): void {
    if (this.disposed || this.state.mode !== "webaudio" || !hasWebAudio()) return;

    if (!this.context) {
      try {
        const context = createContext();
        const master = context.createGain();
        master.connect(context.destination);

        const narration = context.createGain();
        narration.connect(master);

        const bgm = context.createGain();
        bgm.connect(master);

        this.context = context;
        this.narrationGain = narration;
        this.bgmGain = bgm;
        this.patchRoot({ contextState: context.state as MixerSnapshot["contextState"] });
      } catch {
        // Không dựng được thì không có gì để lùi về ngoài chính thẻ audio.
        this.fallToElementMode();
        return;
      }
    }

    if (this.element && !this.narrationSource && this.context && this.narrationGain) {
      try {
        this.narrationSource = this.context.createMediaElementSource(this.element);
        this.narrationSource.connect(this.narrationGain);
        // Từ giây phút này, âm lượng do GainNode quyết định; để thẻ audio tự hạ
        // thêm một lần nữa là nhân hai lần cùng một con số.
        this.element.volume = 1;
        this.element.muted = false;
      } catch {
        this.fallToElementMode();
        return;
      }
    }

    this.applyNarrationVolume();
    this.applyBgmVolume(false);
  }

  /** Bỏ đồ thị, quay về chỉnh âm lượng thẳng trên thẻ audio. */
  private fallToElementMode(): void {
    this.narrationSource = null;
    this.patchRoot({ mode: "element" });
    this.applyNarrationVolume();
    this.applyBgmVolume(false);
  }

  private rampTo(param: AudioParam, value: number, seconds = VOLUME_RAMP_S): void {
    const context = this.context;
    if (!context) return;
    const now = context.currentTime;
    // Chốt giá trị hiện tại trước rồi mới dốc: không có nó, hai lần chỉnh nối
    // nhau sẽ nhảy về giá trị cũ trước khi đi tiếp.
    param.cancelScheduledValues(now);
    param.setValueAtTime(param.value, now);
    param.linearRampToValueAtTime(value, now + seconds);
  }

  /* ---------------------------------------------------------------- */
  /* Thẻ audio của giọng đọc                                           */
  /* ---------------------------------------------------------------- */

  /**
   * Thẻ audio đúng cho chế độ đang dùng, dựng lại nếu chế độ vừa đổi.
   *
   * Phải dựng lại chứ không sửa được: `crossOrigin` chỉ có tác dụng nếu đặt
   * trước khi gán `src`, và một thẻ đã từng bị `createMediaElementSource` bám
   * vào thì không bao giờ nhả ra nữa.
   */
  private ensureElement(mode: MixerMode): HTMLAudioElement {
    if (this.element && this.state.mode === mode) return this.element;

    if (this.element) {
      this.detachElement(this.element);
    }
    this.narrationSource = null;

    const element = new Audio();
    element.preload = "metadata";
    if (mode === "webaudio") {
      element.crossOrigin = "anonymous";
    }
    this.attachElement(element);

    this.element = element;
    if (this.state.mode !== mode) {
      this.patchRoot({ mode });
    }
    return element;
  }

  private attachElement(element: HTMLAudioElement): void {
    element.addEventListener("loadedmetadata", this.handleLoadedMetadata);
    element.addEventListener("durationchange", this.handleDurationChange);
    element.addEventListener("timeupdate", this.handleTimeUpdate);
    element.addEventListener("play", this.handlePlay);
    element.addEventListener("pause", this.handlePause);
    element.addEventListener("waiting", this.handleWaiting);
    element.addEventListener("playing", this.handlePlaying);
    element.addEventListener("canplay", this.handleCanPlay);
    element.addEventListener("ended", this.handleEnded);
    element.addEventListener("error", this.handleError);
  }

  private detachElement(element: HTMLAudioElement): void {
    element.removeEventListener("loadedmetadata", this.handleLoadedMetadata);
    element.removeEventListener("durationchange", this.handleDurationChange);
    element.removeEventListener("timeupdate", this.handleTimeUpdate);
    element.removeEventListener("play", this.handlePlay);
    element.removeEventListener("pause", this.handlePause);
    element.removeEventListener("waiting", this.handleWaiting);
    element.removeEventListener("playing", this.handlePlaying);
    element.removeEventListener("canplay", this.handleCanPlay);
    element.removeEventListener("ended", this.handleEnded);
    element.removeEventListener("error", this.handleError);

    element.pause();
    element.removeAttribute("src");
    element.load();
  }

  private handleLoadedMetadata = (): void => {
    const element = this.element;
    if (!element) return;

    const duration = Number.isFinite(element.duration) ? element.duration : 0;

    // Nghe tiếp từ chỗ bỏ dở — đúng một lần cho mỗi bản audio. Vài trình duyệt
    // bắn lại sự kiện này sau mỗi lần tua, và lần thứ hai sẽ kéo người nghe về
    // lại chỗ họ vừa rời đi.
    if (!this.resumeHonoured) {
      this.resumeHonoured = true;
      const target = this.resumeAt;
      if (target > 0 && (!duration || target < duration - RESUME_END_MARGIN_S)) {
        element.currentTime = target;
      }
    }

    element.playbackRate = this.state.narration.rate;
    this.patchNarration({
      duration,
      currentTime: element.currentTime,
      status: this.state.narration.status === "loading" ? "ready" : this.state.narration.status,
    });

    if (this.autoPlayPending) {
      this.autoPlayPending = false;
      void this.play();
    }
  };

  private handleDurationChange = (): void => {
    const element = this.element;
    if (!element) return;
    this.patchNarration({
      duration: Number.isFinite(element.duration) ? element.duration : 0,
    });
  };

  private handleTimeUpdate = (): void => {
    const element = this.element;
    if (!element) return;
    this.sample(element.currentTime);
    this.patchNarration({ currentTime: element.currentTime });
    this.callbacks.onTimeUpdate?.(element.currentTime);
  };

  private handlePlay = (): void => {
    this.patchNarration({ status: "playing", error: null });
    this.patchRoot({ blocked: false });
    this.applyBgmVolume();
    this.syncBgmPlayback();
  };

  private handlePause = (): void => {
    const element = this.element;
    // Kết thúc bản audio cũng bắn `pause`; giữ nguyên "ended" thì giao diện
    // không nhấp nháy qua trạng thái "đang tạm dừng" trên đường đi.
    if (this.state.narration.status !== "ended") {
      this.patchNarration({ status: "paused" });
    }
    this.applyBgmVolume();
    this.syncBgmPlayback();
    if (element) this.callbacks.onPause?.(element.currentTime);
  };

  private handleWaiting = (): void => this.patchNarration({ buffering: true });

  private handlePlaying = (): void => this.patchNarration({ buffering: false });

  private handleCanPlay = (): void => this.patchNarration({ buffering: false });

  private handleEnded = (): void => {
    this.patchNarration({ status: "ended", buffering: false });
    this.syncBgmPlayback();
    this.callbacks.onEnded?.();
  };

  private handleError = (): void => {
    this.patchNarration({
      status: "error",
      buffering: false,
      error: "Không tải được bản audio này. Vui lòng thử lại.",
    });
  };

  /* ---------------------------------------------------------------- */
  /* Giọng đọc: nạp và điều khiển                                      */
  /* ---------------------------------------------------------------- */

  /**
   * Đổi bản audio đang phát.
   *
   * `src` rỗng nghĩa là gỡ hẳn — chương không có bản nào để nghe.
   */
  async loadNarration(src: string | null, options: LoadNarrationOptions = {}): Promise<void> {
    if (this.disposed) return;

    const token = ++this.loadToken;

    if (!src) {
      if (this.element) {
        this.detachElement(this.element);
        this.element = null;
        this.narrationSource = null;
      }
      this.patchNarration({ ...INITIAL_NARRATION, volume: this.state.narration.volume,
        rate: this.state.narration.rate, muted: this.state.narration.muted });
      this.syncBgmPlayback();
      return;
    }

    this.patchNarration({
      status: "loading",
      currentTime: options.startAt ?? 0,
      duration: 0,
      buffering: false,
      error: null,
    });

    const useWebAudio = await this.canUseWebAudio(src);
    // Trong lúc chờ phép thử, người đọc có thể đã sang chương khác.
    if (this.disposed || token !== this.loadToken) return;

    const element = this.ensureElement(useWebAudio ? "webaudio" : "element");

    this.resumeAt = options.startAt ?? 0;
    this.resumeHonoured = false;
    this.autoPlayPending = Boolean(options.autoPlay);
    this.sample(this.resumeAt);

    element.src = src;
    element.playbackRate = this.state.narration.rate;
    this.applyNarrationVolume();
    element.load();
  }

  /**
   * Web Audio có nhận được file này không.
   *
   * Cùng nguồn gốc thì chắc chắn có. Khác nguồn gốc thì phải hỏi thật: hai byte
   * đầu tiên, đúng địa chỉ ấy, ở chế độ cors. Trả lời được nghĩa là máy chủ có
   * gắn header CORS, và thẻ audio mang `crossOrigin="anonymous"` sẽ không bị
   * đánh dấu nhiễm bẩn — đó là điều kiện duy nhất khiến `createMediaElementSource`
   * ra tiếng thật thay vì ra im lặng.
   */
  private async canUseWebAudio(src: string): Promise<boolean> {
    if (!hasWebAudio()) return false;

    let url: URL;
    try {
      url = new URL(src, window.location.href);
    } catch {
      return false;
    }
    if (url.origin === window.location.origin) return true;

    const cached = corsProbes.get(url.origin);
    if (cached) return cached;

    const probe = fetch(url.toString(), {
      method: "GET",
      headers: { Range: "bytes=0-1" },
      mode: "cors",
      credentials: "omit",
    })
      .then((response) => response.ok || response.status === 206)
      .catch(() => false);

    corsProbes.set(url.origin, probe);
    return probe;
  }

  async play(): Promise<void> {
    const element = this.element;
    if (!element || this.disposed) return;

    this.ensureGraph();

    /*
     * Bấm phát trước, mở khoá đồ thị sau.
     *
     * Trình duyệt chỉ coi một lệnh phát là "do người dùng yêu cầu" khi nó được
     * gọi ngay trong cùng nhịp với cái bấm. Chờ `context.resume()` xong rồi mới
     * gọi `play()` là đã sang nhịp khác — trên Safari và iOS, đó là chỗ lệnh
     * phát bị từ chối dù người ta vừa bấm đúng nút phát.
     */
    const started = element.play();

    if (this.context && this.context.state === "suspended") {
      void this.context.resume().then(
        () => this.patchRoot({
          contextState: (this.context?.state ?? "suspended") as MixerSnapshot["contextState"],
        }),
        () => {
          // Chưa mở được thì lần bấm sau thử lại; tiếng vẫn đang chạy qua đồ
          // thị treo và sẽ nghe được ngay khi nó mở.
        },
      );
    }

    try {
      await started;
    } catch (error) {
      const name = (error as DOMException | undefined)?.name;
      if (name === "NotAllowedError") {
        // Chính sách tự phát: không phải lỗi, chỉ là chưa có cái bấm nào.
        this.patchRoot({ blocked: true });
        this.patchNarration({ status: "paused" });
      } else if (name !== "AbortError") {
        this.patchNarration({
          status: "error",
          error: "Không phát được bản audio này. Vui lòng thử lại.",
        });
      }
    }
  }

  pause(): void {
    this.element?.pause();
  }

  async toggle(): Promise<void> {
    const element = this.element;
    if (!element) return;
    if (element.paused) {
      await this.play();
    } else {
      element.pause();
    }
  }

  /** Nhảy tới một mốc bất kỳ; số ngoài khoảng được kéo về trong khoảng. */
  seek(seconds: number): void {
    const element = this.element;
    if (!element) return;

    const limit = Number.isFinite(element.duration) ? element.duration : seconds;
    const target = Math.min(Math.max(seconds, 0), limit);

    element.currentTime = target;
    this.sample(target);
    this.patchNarration({ currentTime: target });
  }

  skip(delta: number): void {
    const element = this.element;
    if (!element) return;
    this.seek(element.currentTime + delta);
  }

  setRate(rate: number): void {
    const safe = Math.min(4, Math.max(0.25, rate));
    if (this.element) this.element.playbackRate = safe;
    // Đồng hồ ngoại suy chạy theo tốc độ, nên mốc mẫu phải lấy lại ngay tại đây.
    this.sample(this.element?.currentTime ?? this.state.narration.currentTime);
    this.patchNarration({ rate: safe });
  }

  setNarrationVolume(volume: number): void {
    this.patchNarration({ volume: clamp01(volume), muted: false });
    this.applyNarrationVolume();
  }

  setMuted(muted: boolean): void {
    this.patchNarration({ muted });
    this.applyNarrationVolume();
  }

  private applyNarrationVolume(): void {
    const { volume, muted } = this.state.narration;
    const target = muted ? 0 : volume;

    if (this.narrationSource && this.narrationGain && this.context) {
      this.rampTo(this.narrationGain.gain, target);
      if (this.element) {
        this.element.volume = 1;
        this.element.muted = false;
      }
      return;
    }

    if (this.element) {
      this.element.volume = target;
      this.element.muted = muted;
    }
  }

  /* ---------------------------------------------------------------- */
  /* Nhạc nền                                                          */
  /* ---------------------------------------------------------------- */

  /**
   * Đổi bản nhạc nền, hoặc tắt hẳn khi truyền null.
   *
   * Bản đang chạy được kéo nhỏ dần rồi mới dừng, bản mới lớn dần lên — đổi
   * thẳng thì có một tiếng bụp ngay giữa câu đang đọc.
   */
  async setBgmTrack(track: BgmTrack | null): Promise<void> {
    if (this.disposed) return;

    const token = ++this.bgmToken;
    this.stopBgm();
    this.bgmOffset = 0;

    if (!track) {
      this.bgmBuffer = null;
      this.patchBgm({ track: null, status: "idle", error: null });
      return;
    }

    this.patchBgm({ track, status: "loading", error: null });

    try {
      if (this.state.mode === "webaudio") {
        this.ensureGraph();
        const buffer = await this.decode(track.url);
        if (this.disposed || token !== this.bgmToken) return;
        this.bgmBuffer = buffer;
      } else {
        const element = new Audio(track.url);
        element.loop = this.state.bgm.loop;
        element.preload = "auto";
        if (this.bgmElement) {
          this.bgmElement.pause();
        }
        this.bgmElement = element;
      }
    } catch {
      if (this.disposed || token !== this.bgmToken) return;
      this.patchBgm({
        status: "error",
        error: "Không tải được bản nhạc nền này. Vui lòng chọn bản khác.",
      });
      return;
    }

    this.patchBgm({ status: "paused" });
    this.applyBgmVolume(false);
    this.syncBgmPlayback();
  }

  /**
   * Tải và giải mã một bản nhạc nền.
   *
   * Giải mã trọn vào bộ nhớ chứ không phát dần: một vòng lặp liền mạch cần biết
   * chính xác mẫu cuối cùng nối vào mẫu đầu tiên ở đâu, mà thẻ `<audio loop>`
   * thì để lại một khe im lặng ở mỗi vòng vì phần đệm của định dạng MP3. Bản
   * nhạc nền dài vài phút nên cái giá bộ nhớ là chấp nhận được — chương audio
   * hàng chục phút thì không, và đó là lý do giọng đọc đi đường khác.
   */
  private async decode(url: string): Promise<AudioBuffer> {
    const cached = this.bufferCache.get(url);
    if (cached) return cached;

    const context = this.context;
    if (!context) throw new Error("no audio context");

    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const bytes = await response.arrayBuffer();
    const buffer = await context.decodeAudioData(bytes);

    this.bufferCache.set(url, buffer);
    return buffer;
  }

  setBgmVolume(volume: number): void {
    this.patchBgm({ volume: clamp01(volume) });
    this.applyBgmVolume();
  }

  setBgmLoop(loop: boolean): void {
    this.patchBgm({ loop });
    if (this.bgmSource) this.bgmSource.loop = loop;
    if (this.bgmElement) this.bgmElement.loop = loop;
  }

  setDucking(duck: boolean): void {
    this.patchBgm({ duck });
    this.applyBgmVolume();
  }

  /**
   * Âm lượng nhạc nền thật sự nghe thấy = âm lượng đã chọn × hệ số nhường lời.
   *
   * Nhường lời (ducking) không phải hiệu ứng cho vui: nhạc nền đủ to để cảm
   * nhận được trong lúc im lặng thì đủ to để lấn giọng đọc, nên nó lùi lại
   * trong lúc có tiếng người và trở lại ở khoảng nghỉ.
   */
  private applyBgmVolume(smooth = true): void {
    const ducking = this.state.bgm.duck && this.state.narration.status === "playing";
    const target = this.state.bgm.volume * (ducking ? DUCK_FACTOR : 1);

    if (this.bgmGain && this.context) {
      this.rampTo(this.bgmGain.gain, target, smooth ? VOLUME_RAMP_S * 3 : 0.01);
    }
    if (this.bgmElement) {
      this.bgmElement.volume = target;
    }
  }

  /** Nhạc nền chạy khi và chỉ khi có bản được chọn và giọng đọc đang nói. */
  private syncBgmPlayback(): void {
    const shouldPlay = this.state.bgm.track !== null
      && this.state.narration.status === "playing";

    if (shouldPlay) {
      this.startBgm();
    } else {
      this.stopBgm();
    }
  }

  private startBgm(): void {
    if (this.state.mode !== "webaudio") {
      const element = this.bgmElement;
      if (!element) return;
      element.loop = this.state.bgm.loop;
      this.applyBgmVolume(false);
      void element.play().catch(() => {
        // Cùng một chính sách tự phát; giọng đọc đã báo rồi, không báo hai lần.
      });
      this.patchBgm({ status: "playing" });
      return;
    }

    const context = this.context;
    const buffer = this.bgmBuffer;
    const destination = this.bgmGain;
    if (!context || !buffer || !destination || this.bgmSource) return;

    const source = context.createBufferSource();
    source.buffer = buffer;
    source.loop = this.state.bgm.loop;

    const gain = context.createGain();
    gain.gain.setValueAtTime(0, context.currentTime);
    gain.gain.linearRampToValueAtTime(1, context.currentTime + BGM_FADE_S);

    source.connect(gain);
    gain.connect(destination);

    const offset = buffer.duration > 0 ? this.bgmOffset % buffer.duration : 0;
    source.start(0, offset);

    source.onended = () => {
      // Chỉ có ý nghĩa khi tắt lặp: hết bài thì coi như đã dừng, và lần sau bắt
      // đầu lại từ đầu bản nhạc.
      if (source === this.bgmSource) {
        this.bgmSource = null;
        this.bgmSourceGain = null;
        this.bgmOffset = 0;
        this.patchBgm({ status: "paused" });
      }
    };

    this.bgmSource = source;
    this.bgmSourceGain = gain;
    this.bgmStartedAt = context.currentTime - offset;
    this.patchBgm({ status: "playing" });
  }

  private stopBgm(): void {
    if (this.bgmElement) {
      this.bgmElement.pause();
    }

    const context = this.context;
    const source = this.bgmSource;
    const gain = this.bgmSourceGain;

    if (source && context) {
      // Nhớ chỗ đang phát trước đã, để lần sau chạy tiếp chứ không quay về đầu.
      const played = context.currentTime - this.bgmStartedAt;
      const length = source.buffer?.duration ?? 0;
      this.bgmOffset = length > 0 ? played % length : 0;

      if (gain) {
        this.rampTo(gain.gain, 0, BGM_FADE_S / 2);
      }
      source.onended = null;
      try {
        source.stop(context.currentTime + BGM_FADE_S / 2);
      } catch {
        // Đã dừng rồi thì thôi.
      }
    }

    this.bgmSource = null;
    this.bgmSourceGain = null;

    if (this.state.bgm.track && this.state.bgm.status === "playing") {
      this.patchBgm({ status: "paused" });
    }
  }

  /* ---------------------------------------------------------------- */
  /* Đồng hồ cho phần tô sáng                                          */
  /* ---------------------------------------------------------------- */

  private sample(mediaTime: number): void {
    this.sampledMediaTime = mediaTime;
    this.sampledWallTime = typeof performance === "undefined"
      ? Date.now()
      : performance.now();
  }

  /**
   * Vị trí nghe hiện tại, mịn tới từng khung hình.
   *
   * `currentTime` của thẻ audio chỉ nhích theo từng khối dữ liệu được giải mã,
   * nên đọc thẳng nó ở mỗi khung hình sẽ ra một con số đứng yên vài khung rồi
   * nhảy — và phần tô sáng nhảy theo. Giữa hai lần nhích, chỗ này suy ra bằng
   * đồng hồ tường nhân với tốc độ đọc, có trần để không chạy trước giọng đọc
   * khi tiếng đang phải chờ dữ liệu.
   */
  currentTimeNow(): number {
    const element = this.element;
    if (!element) return this.state.narration.currentTime;

    const media = element.currentTime;
    if (media !== this.sampledMediaTime) {
      this.sample(media);
      return media;
    }
    if (element.paused || element.ended) return media;

    const now = typeof performance === "undefined" ? Date.now() : performance.now();
    const elapsed = Math.min((now - this.sampledWallTime) / 1000, MAX_EXTRAPOLATION_S);
    const limit = Number.isFinite(element.duration) ? element.duration : Infinity;

    return Math.min(media + elapsed * element.playbackRate, limit);
  }

  isPlaying(): boolean {
    return this.state.narration.status === "playing";
  }

  /**
   * Mở khoá đường tiếng sau một thao tác của người dùng.
   *
   * Dành cho những cái bấm không phải nút phát — chọn nhạc nền chẳng hạn. Trình
   * duyệt tính mọi thao tác, nên tận dụng cái nào cũng được.
   */
  async resumeContext(): Promise<void> {
    this.ensureGraph();
    if (this.context && this.context.state === "suspended") {
      try {
        await this.context.resume();
        this.patchRoot({ contextState: this.context.state as MixerSnapshot["contextState"] });
      } catch {
        // Vẫn treo thì lần bấm phát sau sẽ thử lại.
      }
    }
  }

  /** Vị trí chính xác lúc này, cho việc ghi lại chỗ nghe dở khi rời trang. */
  exactPosition(): number {
    return this.element?.currentTime ?? this.state.narration.currentTime;
  }

  /**
   * Đã bị dọn hay chưa.
   *
   * Có mặt vì React ở chế độ nghiêm ngặt gắn rồi tháo rồi gắn lại mọi hiệu ứng
   * một lượt khi phát triển; lần tháo ấy dọn mất bộ trộn, và lần gắn thứ hai
   * cần biết điều đó để dựng cái mới thay vì nói chuyện với một cái đã đóng.
   */
  isDisposed(): boolean {
    return this.disposed;
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;

    this.stopBgm();

    if (this.element) {
      this.detachElement(this.element);
      this.element = null;
    }
    if (this.bgmElement) {
      this.bgmElement.pause();
      this.bgmElement.removeAttribute("src");
      this.bgmElement = null;
    }

    this.narrationSource = null;
    this.bgmBuffer = null;
    this.bufferCache.clear();
    this.listeners.clear();

    if (this.context) {
      void this.context.close().catch(() => {
        // Đóng hai lần thì trình duyệt kêu; không có gì để làm với lời kêu ấy.
      });
      this.context = null;
    }
  }
}
