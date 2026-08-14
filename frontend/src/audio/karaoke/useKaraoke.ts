import { useCallback, useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import type { AudioMixerEngine } from "../AudioMixerEngine";
import type { WordTimeline } from "./WordTimeline";

/**
 * Đưa phần tô sáng chạy theo giọng đọc, mỗi khung hình một lần.
 *
 * <h3>Vì sao là requestAnimationFrame chứ không phải sự kiện timeupdate</h3>
 * `timeupdate` bắn khoảng bốn lần mỗi giây, và bắn vào những lúc do trình duyệt
 * chọn chứ không phải lúc màn hình sắp vẽ. Tô sáng theo nó nghĩa là chữ sáng
 * lên trễ tới một phần tư giây so với tiếng, và trễ không đều — mắt bắt được
 * ngay điều đó, kể cả khi không gọi tên được nó. `requestAnimationFrame` thì
 * chạy đúng ngay trước mỗi lần vẽ, nên chữ đổi cùng nhịp với màn hình.
 *
 * <h3>Vì sao vòng lặp này không đụng tới React</h3>
 * Một chương có năm nghìn ô chữ. Gọi `setState` sáu mươi lần mỗi giây để đổi
 * một ô sẽ kéo cả cây React đi so sánh lại sáu mươi lần mỗi giây — và đó chính
 * là kiểu giật mà cả kiến trúc này sinh ra để tránh. Nên vòng lặp ở đây ghi
 * thẳng vào DOM: bỏ một lớp CSS khỏi ô cũ, thêm vào ô mới. Đúng vài thao tác
 * cho mỗi lần đổi chữ, không phụ thuộc chương dài bao nhiêu.
 *
 * Thứ duy nhất đi qua React là việc người đọc có tự cuộn tay hay không — vì đó
 * là thứ giao diện phải nói ra, và nó chỉ đổi vài lần trong một buổi đọc.
 */

/** Lớp CSS đánh dấu chữ đang được đọc và câu chứa nó. */
const WORD_ACTIVE = "is-speaking";
const LINE_ACTIVE = "is-current";

/** Chữ đã đọc qua được đánh dấu riêng, để cả đoạn có một vệt đã-đi-qua. */
const WORD_DONE = "is-spoken";

/**
 * Câu đang đọc nằm trong khoảng này của khung nhìn thì không cuộn.
 *
 * Cuộn ở mọi câu là một trang chữ không bao giờ đứng yên, và mắt không đọc nổi
 * một trang như thế. Chỉ khi dòng đang đọc trôi ra khỏi vùng thoải mái thì mới
 * kéo nó về giữa.
 */
const COMFORT_TOP = 0.28;
const COMFORT_BOTTOM = 0.72;

export interface UseKaraokeOptions {
  engine: AudioMixerEngine | null;
  timeline: WordTimeline | null;
  /** Khối chứa các ô chữ đã được đánh dấu `data-w` và `data-l`. */
  containerRef: RefObject<HTMLElement | null>;
  enabled: boolean;
  autoScroll: boolean;
}

export interface UseKaraokeResult {
  /**
   * Phần tự cuộn còn đang bám theo giọng đọc hay đã nhường cho tay người.
   *
   * Người đọc cuộn tay là họ đang muốn xem chỗ khác; kéo họ về ngay lập tức là
   * giành lấy quyền điều khiển từ tay họ. Nên khi ấy phần tự cuộn lùi lại, và
   * giao diện mọc ra một nút để quay về chỗ đang đọc khi nào họ muốn.
   */
  following: boolean;
  resumeFollowing: () => void;
}

export function useKaraoke({
  engine,
  timeline,
  containerRef,
  enabled,
  autoScroll,
}: UseKaraokeOptions): UseKaraokeResult {
  const [following, setFollowing] = useState(true);

  const wordNodes = useRef<HTMLElement[]>([]);
  const lineNodes = useRef<HTMLElement[]>([]);

  const activeWord = useRef(-1);
  const activeLine = useRef(-1);

  const frame = useRef<number | null>(null);
  const followingRef = useRef(true);

  followingRef.current = following;

  /* ---------------------------------------------------------------- */
  /* Bảng tra DOM                                                      */
  /* ---------------------------------------------------------------- */

  /*
   * Gom sẵn các nút DOM một lần cho mỗi chương.
   *
   * `querySelector` trong vòng lặp mỗi khung hình là cách chắc chắn nhất để
   * biến một việc rẻ thành một việc đắt. Ở đây cả hai bảng được dựng một lần,
   * ngay sau khi trình duyệt vẽ xong, và sau đó vòng lặp chỉ còn là tra mảng.
   */
  useEffect(() => {
    const container = containerRef.current;
    if (!container || !timeline) {
      wordNodes.current = [];
      lineNodes.current = [];
      return;
    }

    const words: HTMLElement[] = [];
    for (const node of container.querySelectorAll<HTMLElement>("[data-w]")) {
      const index = Number(node.dataset.w);
      if (Number.isInteger(index)) words[index] = node;
    }

    const lines: HTMLElement[] = [];
    for (const node of container.querySelectorAll<HTMLElement>("[data-l]")) {
      const index = Number(node.dataset.l);
      if (Number.isInteger(index)) lines[index] = node;
    }

    wordNodes.current = words;
    lineNodes.current = lines;
    activeWord.current = -1;
    activeLine.current = -1;
  }, [containerRef, timeline]);

  /* ---------------------------------------------------------------- */
  /* Cuộn                                                              */
  /* ---------------------------------------------------------------- */

  const scrollToLine = useCallback((lineIndex: number, force: boolean) => {
    const node = lineNodes.current[lineIndex];
    if (!node) return;

    const scroller = findScroller(node);
    const box = scroller
      ? scroller.getBoundingClientRect()
      : { top: 0, height: window.innerHeight };

    const rect = node.getBoundingClientRect();
    const offset = rect.top - box.top;
    const position = box.height > 0 ? (offset + rect.height / 2) / box.height : 0.5;

    // Vẫn nằm trong vùng thoải mái: để yên. Trang chữ chỉ nên nhúc nhích khi
    // dòng đang đọc thật sự sắp đi mất.
    if (!force && position >= COMFORT_TOP && position <= COMFORT_BOTTOM) return;

    const delta = offset - (box.height / 2 - rect.height / 2);
    const behavior: ScrollBehavior = prefersReducedMotion() ? "auto" : "smooth";

    if (scroller) {
      scroller.scrollTo({ top: scroller.scrollTop + delta, behavior });
    } else {
      window.scrollTo({ top: window.scrollY + delta, behavior });
    }
  }, []);

  const resumeFollowing = useCallback(() => {
    setFollowing(true);
    followingRef.current = true;
    if (activeLine.current >= 0) scrollToLine(activeLine.current, true);
  }, [scrollToLine]);

  /*
   * Tay người thắng máy.
   *
   * Nghe `wheel`, `touchmove` và các phím cuộn chứ không nghe `scroll`: sự kiện
   * `scroll` không nói được ai vừa cuộn, nên chính cú cuộn do phần này phát ra
   * cũng sẽ bị hiểu nhầm là người đọc vừa cuộn tay. Ba thứ kia thì chỉ có người
   * mới tạo ra được.
   */
  useEffect(() => {
    if (!enabled || !autoScroll) return undefined;

    const container = containerRef.current;
    const scroller: HTMLElement | Window = (container && findScroller(container)) ?? window;

    const yieldToReader = () => {
      if (!followingRef.current) return;
      followingRef.current = false;
      setFollowing(false);
    };

    const onKeyDown = (event: KeyboardEvent) => {
      const scrollKeys = ["ArrowUp", "ArrowDown", "PageUp", "PageDown", "Home", "End", " "];
      if (scrollKeys.includes(event.key)) yieldToReader();
    };

    scroller.addEventListener("wheel", yieldToReader, { passive: true });
    scroller.addEventListener("touchmove", yieldToReader, { passive: true });
    window.addEventListener("keydown", onKeyDown);

    return () => {
      scroller.removeEventListener("wheel", yieldToReader);
      scroller.removeEventListener("touchmove", yieldToReader);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [autoScroll, containerRef, enabled]);

  /* ---------------------------------------------------------------- */
  /* Vòng lặp                                                          */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    const words = wordNodes;
    const lines = lineNodes;

    if (!enabled || !engine || !timeline || timeline.isEmpty) {
      // Rời chế độ tô sáng thì phải dọn dấu vết, nếu không chữ cuối cùng sẽ
      // sáng mãi ở giữa một trang không còn ai đọc theo.
      clearMarks(words.current, lines.current, activeWord, activeLine);
      return undefined;
    }

    const step = () => {
      const time = engine.currentTimeNow();
      const { wordIndex, lineIndex } = timeline.locate(time, activeWord.current);

      if (wordIndex !== activeWord.current) {
        words.current[activeWord.current]?.classList.remove(WORD_ACTIVE);

        const next = words.current[wordIndex];
        if (next) {
          next.classList.add(WORD_ACTIVE);
          next.classList.remove(WORD_DONE);
        }

        /*
         * Vệt đã-đọc-qua.
         *
         * Bình thường mỗi khung hình chỉ động tới một ô. Vòng lặp ở đây là cho
         * cú tua: nhảy từ giữa chương ra sau thì cả quãng vừa bị bỏ qua phải
         * thành đã đọc, còn nhảy ngược lại thì cả quãng phía sau phải sạch trở
         * lại — nếu không, vệt ấy nói dối về chỗ giọng đọc đã đi qua.
         */
        if (wordIndex > activeWord.current) {
          for (let i = Math.max(activeWord.current, 0); i < wordIndex; i += 1) {
            words.current[i]?.classList.add(WORD_DONE);
          }
        } else {
          for (let i = wordIndex + 1; i <= activeWord.current; i += 1) {
            words.current[i]?.classList.remove(WORD_DONE);
          }
        }

        activeWord.current = wordIndex;
      }

      if (lineIndex !== activeLine.current) {
        lines.current[activeLine.current]?.classList.remove(LINE_ACTIVE);
        lines.current[lineIndex]?.classList.add(LINE_ACTIVE);
        activeLine.current = lineIndex;

        if (autoScroll && followingRef.current && lineIndex >= 0) {
          scrollToLine(lineIndex, false);
        }
      }
    };

    const loop = () => {
      step();
      frame.current = requestAnimationFrame(loop);
    };

    const start = () => {
      if (frame.current === null) frame.current = requestAnimationFrame(loop);
    };

    const stop = () => {
      if (frame.current !== null) {
        cancelAnimationFrame(frame.current);
        frame.current = null;
      }
    };

    /*
     * Chỉ chạy vòng lặp khi có tiếng.
     *
     * Lúc dừng thì mỗi lần trạng thái đổi — tua, đổi bản, nạp xong — vẫn cần
     * đúng một lần cập nhật để phần tô sáng nhảy theo về chỗ mới. Chạy cả vòng
     * lặp cho việc ấy là sáu mươi khung hình mỗi giây để vẽ lại một thứ không
     * đổi, và trên máy chạy pin thì đó là điều người dùng cảm nhận được.
     */
    const sync = () => {
      if (engine.isPlaying()) {
        start();
      } else {
        stop();
        step();
      }
    };

    const unsubscribe = engine.subscribe(sync);
    sync();

    return () => {
      unsubscribe();
      stop();
    };
  }, [autoScroll, enabled, engine, scrollToLine, timeline]);

  return { following, resumeFollowing };
}

function clearMarks(
  words: HTMLElement[],
  lines: HTMLElement[],
  activeWord: RefObject<number>,
  activeLine: RefObject<number>,
): void {
  words[activeWord.current]?.classList.remove(WORD_ACTIVE);
  lines[activeLine.current]?.classList.remove(LINE_ACTIVE);
  for (const node of words) node?.classList.remove(WORD_DONE);
  activeWord.current = -1;
  activeLine.current = -1;
}

/**
 * Khối thật sự cuộn được gần nhất, hoặc null khi đó là cả trang.
 *
 * Trang đọc có hai hình dạng: trên màn hình rộng, phần chữ là một cột có thanh
 * cuộn riêng; trên điện thoại, nó nằm trong dòng chảy của cả trang và thứ cuộn
 * là cửa sổ. Cùng một đoạn mã phải chạy được với cả hai, nên nó đi tìm thay vì
 * giả định.
 */
function findScroller(from: HTMLElement): HTMLElement | null {
  let node: HTMLElement | null = from.parentElement;

  while (node && node !== document.body && node !== document.documentElement) {
    const style = window.getComputedStyle(node);
    const scrolls = /(auto|scroll|overlay)/.test(style.overflowY);
    if (scrolls && node.scrollHeight > node.clientHeight + 1) return node;
    node = node.parentElement;
  }

  return null;
}

function prefersReducedMotion(): boolean {
  return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

export default useKaraoke;
