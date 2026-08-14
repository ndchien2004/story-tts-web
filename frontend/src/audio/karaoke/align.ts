import type { KaraokeLine, KaraokeTimelineData, KaraokeWord, WordTimestamp } from "../types";
import { assignLines, tokenize, type TextToken } from "./tokenize";

/**
 * Dóng mốc thời gian của máy chủ vào đúng chữ đang hiển thị trên màn hình.
 *
 * <h3>Vì sao không dùng thẳng chỉ số ký tự</h3>
 * Máy chủ đã gửi kèm `charStart`/`charEnd`, và trong đại đa số trường hợp chúng
 * đúng tuyệt đối. Nhưng chuỗi đến được màn hình không luôn là chuỗi máy chủ đã
 * gửi đi đọc: tầng gọi API gấp mọi phản hồi về dạng Unicode NFC, chương có thể
 * đã được sửa một chữ sau khi bản audio dựng xong, và người biên tập dán vào
 * những ký tự vô hình mà nhà cung cấp giọng đọc lặng lẽ bỏ qua. Tin tuyệt đối
 * vào chỉ số ký tự thì chỉ cần lệch một ký tự là cả chương tô sáng sai một nhịp
 * — mà lệch một nhịp thì tệ hơn là không tô, vì nó khiến người đọc nghi ngờ
 * chính mắt mình.
 *
 * Nên chỉ số ký tự ở đây là **gợi ý**: thử trước, kiểm bằng chính chữ ấy, sai
 * thì dò tiếp vài chữ về phía trước. Con trỏ chỉ tiến, nên một chỗ lệch không
 * bao giờ kéo phần sau lệch theo.
 *
 * <h3>Ra một chữ cho mỗi ô chữ trên màn hình</h3>
 * Kết quả có đúng một phần tử cho mỗi chữ được vẽ ra, kể cả những chữ không dóng
 * được. Nhờ vậy chỉ số trong mảng cũng chính là chỉ số của thẻ span trong DOM,
 * và vòng lặp mỗi khung hình không phải tra cứu gì thêm. Chữ không dóng được
 * thì nhận mốc nội suy từ hai chữ khớp gần nhất — im lặng bỏ qua nó sẽ để lại
 * một lỗ tối giữa câu đang sáng.
 */

/** Dò tối đa ngần này chữ về phía trước khi gợi ý vị trí ký tự không khớp. */
const LOOKAHEAD = 12;

/** Dấu câu ở hai đầu chữ, bỏ đi khi so khớp nhưng giữ nguyên khi hiển thị. */
const EDGE_PUNCTUATION = /^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu;

/** Ước lượng thời gian đọc một ký tự, chỉ dùng cho phần đuôi không dóng được. */
const SECONDS_PER_CHARACTER = 0.06;

const EMPTY: KaraokeTimelineData = { words: [], lines: [], matchRatio: 0 };

export function alignTranscript(
  content: string,
  timestamps: readonly WordTimestamp[],
): KaraokeTimelineData {
  if (!content || timestamps.length === 0) return EMPTY;

  const tokens = tokenize(content);
  if (tokens.length === 0) return EMPTY;

  const lineOf = assignLines(content, tokens);
  const anchors = matchAnchors(tokens, timestamps);

  const matched = anchors.filter(Boolean).length;
  const words = buildWords(tokens, lineOf, anchors);

  return {
    words,
    lines: buildLines(words),
    matchRatio: matched / tokens.length,
  };
}

/** Mốc thời gian của một chữ đã khớp; `null` ở những chữ chưa khớp được. */
type Anchor = { start: number; end: number } | null;

/**
 * Ghép từng chữ máy chủ gửi về vào một ô chữ trên màn hình.
 *
 * Con trỏ `next` chỉ tiến: mỗi ô chữ nhận nhiều nhất một mốc, và mốc sau không
 * bao giờ rơi vào ô trước mốc trước — đó là thứ giữ cho một lần lệch không lan
 * ra thành cả chương lệch.
 */
function matchAnchors(tokens: TextToken[], timestamps: readonly WordTimestamp[]): Anchor[] {
  const anchors: Anchor[] = new Array<Anchor>(tokens.length).fill(null);
  let next = 0;

  for (const word of timestamps) {
    const key = normalize(word.word);
    if (!key) continue;

    let hit = -1;

    // Gợi ý của máy chủ, kiểm lại bằng chính chữ ấy.
    const hinted = tokenAt(tokens, word.charStart);
    if (hinted >= next && matches(tokens[hinted], key)) {
      hit = hinted;
    } else {
      const limit = Math.min(tokens.length, next + LOOKAHEAD);
      for (let i = next; i < limit; i += 1) {
        if (matches(tokens[i], key)) {
          hit = i;
          break;
        }
      }
    }

    if (hit < 0) continue;

    anchors[hit] = { start: word.start, end: Math.max(word.end, word.start) };
    next = hit + 1;
  }

  return anchors;
}

/**
 * Điền mốc cho những chữ không dóng được, rồi ép cả mảng chỉ tăng.
 *
 * Nội suy theo độ dài chữ chứ không chia đều: trong một quãng không dóng được,
 * chữ dài thường mất nhiều thời gian đọc hơn chữ ngắn, và đó là phép đoán rẻ
 * tiền nhưng đúng hơn hẳn phép chia đều.
 */
function buildWords(tokens: TextToken[], lineOf: number[], anchors: Anchor[]): KaraokeWord[] {
  const starts = new Array<number>(tokens.length).fill(0);
  const ends = new Array<number>(tokens.length).fill(0);

  let index = 0;
  let previousEnd = 0;

  while (index < tokens.length) {
    const anchor = anchors[index];
    if (anchor) {
      starts[index] = anchor.start;
      ends[index] = anchor.end;
      previousEnd = anchor.end;
      index += 1;
      continue;
    }

    // Một quãng liền các chữ chưa có mốc: tìm chữ có mốc kế tiếp làm điểm neo
    // bên phải, rồi rải quãng thời gian giữa hai neo theo độ dài từng chữ.
    let after = index;
    while (after < tokens.length && !anchors[after]) after += 1;

    const gapStart = previousEnd;
    const gapEnd = after < tokens.length
      ? (anchors[after]?.start ?? gapStart)
      : gapStart + estimateDuration(tokens, index, after);

    fillGap(tokens, starts, ends, index, after, gapStart, Math.max(gapEnd, gapStart));

    previousEnd = ends[after - 1] ?? previousEnd;
    index = after;
  }

  const words: KaraokeWord[] = new Array<KaraokeWord>(tokens.length);
  let floor = 0;

  for (let i = 0; i < tokens.length; i += 1) {
    const token = tokens[i];
    if (!token) continue;

    // Ép chỉ tăng: một mốc lùi lại sẽ khiến phép tìm nhị phân ở
    // `WordTimeline` trả lời sai, và người đọc thấy phần tô sáng nhảy ngược.
    const start = Math.max(starts[i] ?? floor, floor);
    const end = Math.max(ends[i] ?? start, start);
    floor = start;

    words[i] = {
      index: i,
      text: token.text,
      start,
      end,
      charStart: token.start,
      charEnd: token.end,
      lineIndex: lineOf[i] ?? 0,
    };
  }

  return words;
}

function fillGap(
  tokens: TextToken[],
  starts: number[],
  ends: number[],
  from: number,
  to: number,
  gapStart: number,
  gapEnd: number,
): void {
  let total = 0;
  for (let i = from; i < to; i += 1) total += weight(tokens[i]);
  if (total <= 0) total = 1;

  const span = gapEnd - gapStart;
  let cursor = gapStart;

  for (let i = from; i < to; i += 1) {
    const share = (weight(tokens[i]) / total) * span;
    starts[i] = cursor;
    ends[i] = cursor + share;
    cursor += share;
  }
}

function estimateDuration(tokens: TextToken[], from: number, to: number): number {
  let characters = 0;
  for (let i = from; i < to; i += 1) characters += weight(tokens[i]);
  return characters * SECONDS_PER_CHARACTER;
}

function weight(token: TextToken | undefined): number {
  return token ? Math.max(1, token.text.length) : 1;
}

/** Gom chữ thành câu, để phần tự cuộn có một đơn vị đủ lớn để bám theo. */
function buildLines(words: KaraokeWord[]): KaraokeLine[] {
  const lines: KaraokeLine[] = [];

  for (const word of words) {
    const current = lines[lines.length - 1];
    if (current && current.index === word.lineIndex) {
      current.lastWord = word.index;
      current.end = Math.max(current.end, word.end);
      continue;
    }

    lines.push({
      index: word.lineIndex,
      start: word.start,
      end: word.end,
      firstWord: word.index,
      lastWord: word.index,
    });
  }

  return lines;
}

/**
 * Ô chữ chứa vị trí ký tự này, tìm bằng phép chia đôi.
 *
 * Trả về ô gần nhất chứ không trả về "không có": gợi ý lệch vài ký tự vẫn là
 * một gợi ý đáng thử, và bên gọi kiểm lại bằng chính nội dung chữ.
 */
function tokenAt(tokens: TextToken[], charIndex: number): number {
  let low = 0;
  let high = tokens.length - 1;

  // Ô cuối cùng bắt đầu trước vị trí này; -1 khi vị trí nằm trước cả ô đầu tiên.
  let before = -1;

  while (low <= high) {
    const middle = (low + high) >> 1;
    const token = tokens[middle];
    if (!token) break;

    if (charIndex < token.start) {
      high = middle - 1;
    } else {
      if (charIndex < token.end) return middle;
      before = middle;
      low = middle + 1;
    }
  }

  // Rơi vào khoảng trắng giữa hai ô: ô đứng ngay sau mới là ô đang được nhắm tới.
  return Math.min(Math.max(before + 1, 0), tokens.length - 1);
}

function matches(token: TextToken | undefined, key: string): boolean {
  return token !== undefined && normalize(token.text) === key;
}

function normalize(value: string): string {
  return value.normalize("NFC").toLowerCase().replace(EDGE_PUNCTUATION, "");
}
