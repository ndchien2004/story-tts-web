import type { KaraokeLine, KaraokePosition, KaraokeTimelineData, KaraokeWord } from "../types";

/**
 * Tra "giây thứ n thì đang đọc chữ nào" — sáu mươi lần một giây.
 *
 * <h3>Vì sao không phải một vòng lặp đơn giản</h3>
 * Một chương dài có năm nghìn chữ. Duyệt cả mảng ở mỗi khung hình là ba trăm
 * nghìn phép so sánh mỗi giây, đủ để phần tô sáng bắt đầu giật đúng lúc người
 * ta muốn nó mượt nhất. Ở đây có hai đường:
 *
 * — Đường thường: giọng đọc chạy tiến, nên chữ tiếp theo hầu như luôn là chữ
 *   ngay sau chữ vừa rồi. Kiểm vài chữ quanh chỗ cũ là xong, và đó là toàn bộ
 *   công việc của gần như mọi khung hình.
 * — Đường tua: người nghe nhảy tới giữa chương, gợi ý cũ vô dụng, thì tìm bằng
 *   phép chia đôi — mười ba bước cho năm nghìn chữ.
 *
 * <h3>Khoảng lặng</h3>
 * Giữa hai chữ có dấu ngắt, có tiếng thở, có dấu chấm câu. Ở những giây ấy
 * không chữ nào đang được đọc, nhưng để phần tô sáng tắt đi rồi bật lại thì cả
 * đoạn văn nhấp nháy. Nên quy ước ở đây là: chữ được tô là chữ **gần nhất đã
 * bắt đầu** — nó sáng cho tới khi chữ sau nó sáng lên.
 */
export class WordTimeline {
  readonly words: readonly KaraokeWord[];

  readonly lines: readonly KaraokeLine[];

  readonly matchRatio: number;

  constructor(data: KaraokeTimelineData) {
    this.words = data.words;
    this.lines = data.lines;
    this.matchRatio = data.matchRatio;
  }

  get isEmpty(): boolean {
    return this.words.length === 0;
  }

  /** Giây cuối cùng có chữ để tô; 0 khi rỗng. */
  get duration(): number {
    return this.words[this.words.length - 1]?.end ?? 0;
  }

  /**
   * Chữ và câu đang được đọc tại giây `time`.
   *
   * @param hint chỉ số chữ của khung hình trước, để đi đường ngắn
   */
  locate(time: number, hint = -1): KaraokePosition {
    const words = this.words;
    if (words.length === 0) return { wordIndex: -1, lineIndex: -1 };

    const first = words[0];
    if (first && time < first.start) return { wordIndex: -1, lineIndex: -1 };

    let index = -1;

    // Đường thường: vẫn đúng chữ cũ, hoặc đã sang một trong vài chữ kế tiếp.
    if (hint >= 0 && hint < words.length) {
      const current = words[hint];
      if (current && time >= current.start) {
        index = hint;
        const limit = Math.min(words.length, hint + 4);
        for (let i = hint + 1; i < limit; i += 1) {
          const candidate = words[i];
          if (!candidate || time < candidate.start) break;
          index = i;
        }
        // Đi hết cửa sổ mà vẫn còn tiến được nghĩa là đây không phải bước tiến
        // bình thường (tua tới, hoặc tab vừa được đánh thức) — tìm lại từ đầu.
        if (index === Math.min(words.length, hint + 4) - 1 && index !== words.length - 1) {
          index = -1;
        }
      }
    }

    if (index < 0) index = this.search(time);
    if (index < 0) return { wordIndex: -1, lineIndex: -1 };

    return { wordIndex: index, lineIndex: words[index]?.lineIndex ?? -1 };
  }

  /** Chữ cuối cùng đã bắt đầu tại `time`, tìm bằng phép chia đôi. */
  private search(time: number): number {
    const words = this.words;
    let low = 0;
    let high = words.length - 1;
    let found = -1;

    while (low <= high) {
      const middle = (low + high) >> 1;
      const word = words[middle];
      if (!word) break;

      if (word.start <= time) {
        found = middle;
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }

    return found;
  }

  /** Giây mà một câu bắt đầu — dùng khi người đọc bấm vào một câu để nghe từ đó. */
  lineStart(lineIndex: number): number | null {
    const line = this.lines.find((candidate) => candidate.index === lineIndex);
    return line ? line.start : null;
  }

  /** Giây mà một chữ bắt đầu, cho việc bấm thẳng vào chữ để tua tới đó. */
  wordStart(wordIndex: number): number | null {
    return this.words[wordIndex]?.start ?? null;
  }
}
