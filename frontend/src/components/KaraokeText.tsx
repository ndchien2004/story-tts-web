import { useCallback, useMemo } from "react";
import type { MouseEvent, ReactNode, RefObject } from "react";
import type { WordTimeline } from "../audio/karaoke/WordTimeline";

/**
 * Nội dung chương, chia thành từng ô chữ tô sáng được.
 *
 * <h3>Giữ nguyên từng khoảng trắng</h3>
 * Khối này không dựng lại văn bản từ danh sách chữ — nó cắt chính chuỗi gốc.
 * Mọi khoảng trắng, mọi lần xuống dòng, mọi dòng trống giữa hai đoạn đều được
 * cắt ra và đặt lại nguyên vẹn giữa các ô chữ. Nhờ vậy `white-space: pre-wrap`
 * của trang đọc vẫn vẽ ra đúng cái nó vẫn vẽ, và bật hay tắt phần bám chữ không
 * làm chương nhảy một dòng nào.
 *
 * <h3>Vì sao chỉ dựng một lần</h3>
 * Một chương dài ra chừng năm nghìn thẻ span. Dựng lại chúng ở mỗi lần vẽ là
 * thứ duy nhất trong cả tính năng này đủ nặng để thấy được bằng mắt, nên cây
 * phần tử được ghi nhớ theo nội dung chương và bản mốc thời gian. Việc tô sáng
 * thì không đi qua React chút nào — `useKaraoke` ghi thẳng lớp CSS vào DOM.
 */

export interface KaraokeTextProps {
  content: string;
  /** Null nghĩa là chương này không bám chữ được: vẽ như văn bản thường. */
  timeline: WordTimeline | null;
  innerRef: RefObject<HTMLDivElement | null>;
  /** Bấm vào một chữ thì nghe từ chữ ấy. */
  onSeekToWord?: (wordIndex: number) => void;
  className?: string;
}

export default function KaraokeText({
  content,
  timeline,
  innerRef,
  onSeekToWord,
  className = "",
}: KaraokeTextProps) {
  const nodes = useMemo(() => buildNodes(content, timeline), [content, timeline]);

  /*
   * Một trình xử lý cho cả chương.
   *
   * Gắn `onClick` lên từng ô chữ là năm nghìn hàm và năm nghìn lần đăng ký sự
   * kiện cho một việc mà sự kiện nổi bọt đã làm sẵn.
   */
  const handleClick = useCallback((event: MouseEvent<HTMLDivElement>) => {
    if (!onSeekToWord) return;

    const target = (event.target as HTMLElement).closest<HTMLElement>("[data-w]");
    if (!target) return;

    const index = Number(target.dataset.w);
    if (Number.isInteger(index)) onSeekToWord(index);
  }, [onSeekToWord]);

  const interactive = Boolean(timeline && onSeekToWord);

  return (
    <div
      ref={innerRef}
      className={`reader-content ${timeline ? "is-karaoke" : ""} ${className}`.trim()}
      onClick={interactive ? handleClick : undefined}
      title={interactive ? "Bấm vào một chữ để nghe từ chỗ đó" : undefined}
    >
      {nodes}
    </div>
  );
}

function buildNodes(content: string, timeline: WordTimeline | null): ReactNode {
  if (!timeline || timeline.isEmpty) return content;

  const words = timeline.words;
  const parts: ReactNode[] = [];
  let cursor = 0;

  for (const line of timeline.lines) {
    const first = words[line.firstWord];
    const last = words[line.lastWord];
    if (!first || !last) continue;

    // Khoảng giữa câu trước và câu này — dấu xuống dòng, dòng trống, thụt đầu
    // dòng. Đặt ngoài thẻ câu để việc tô nền một câu không nuốt luôn cả chỗ
    // trống trước nó.
    if (cursor < first.charStart) {
      parts.push(content.slice(cursor, first.charStart));
    }

    const inner: ReactNode[] = [];
    let inside = first.charStart;

    for (let i = line.firstWord; i <= line.lastWord; i += 1) {
      const word = words[i];
      if (!word) continue;

      if (inside < word.charStart) inner.push(content.slice(inside, word.charStart));

      inner.push(
        <span key={i} className="k-word" data-w={i}>
          {word.text}
        </span>,
      );
      inside = word.charEnd;
    }

    parts.push(
      <span key={`line-${line.index}`} className="k-line" data-l={line.index}>
        {inner}
      </span>,
    );
    cursor = last.charEnd;
  }

  if (cursor < content.length) parts.push(content.slice(cursor));

  return parts;
}
