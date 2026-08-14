/**
 * Cắt văn bản chương thành chữ.
 *
 * Ranh giới là khoảng trắng, y hệt cách máy chủ gộp mốc thời gian từng ký tự
 * thành từng chữ (xem `WordAligner` phía backend). Hai bên cắt giống nhau là
 * điều kiện để hai bên khớp nhau — nếu chỗ này cắt "xưa," thành hai mảnh còn
 * bên kia giữ làm một, mọi chữ sau đó sẽ lệch đi một nhịp.
 *
 * Dấu câu vì thế đi liền với chữ nó bám vào, và không có bước "làm sạch" nào:
 * vị trí trả về phải chỉ đúng vào chuỗi văn bản đang hiển thị, vì đó là chuỗi
 * mà trang đọc sẽ vẽ ra thành các ô chữ tô sáng được.
 */

export interface TextToken {
  text: string;
  /** Chỉ số ký tự đầu tiên trong chuỗi gốc. */
  start: number;
  /** Chỉ số ngay sau ký tự cuối cùng (nửa mở). */
  end: number;
}

/** Dấu kết câu: chỗ mắt người đọc được phép nghỉ, nên cũng là chỗ ngắt dòng. */
const SENTENCE_END = /[.!?…:;]["'”’)»]*$/u;

/**
 * Khoảng trắng, kể cả những thứ không nhìn ra là khoảng trắng.
 *
 * Văn bản chương do người dán vào từ đủ mọi nguồn, nên khoảng trắng không ngắt
 * (U+00A0) và khoảng trắng rỗng (U+200B, U+FEFF) xuất hiện thường xuyên hơn
 * người ta tưởng. Không kể chúng vào đây thì chúng dính vào chữ bên cạnh, và
 * chữ ấy không còn khớp với chữ máy chủ gửi về nữa.
 */
const SPACE = /[\s ​﻿]/u;

export function tokenize(text: string): TextToken[] {
  const tokens: TextToken[] = [];
  const length = text.length;

  let index = 0;
  while (index < length) {
    // Bỏ qua khoảng trắng, nhưng không bỏ qua vị trí của nó: mọi khoảng trắng
    // vẫn nằm nguyên trong chuỗi gốc và sẽ được vẽ lại y như cũ.
    while (index < length && isSpace(text.charAt(index))) index += 1;
    if (index >= length) break;

    const start = index;
    while (index < length && !isSpace(text.charAt(index))) index += 1;

    tokens.push({ text: text.slice(start, index), start, end: index });
  }

  return tokens;
}

/**
 * Gán số thứ tự câu cho từng chữ.
 *
 * Một "câu" ở đây là đơn vị được giữ giữa màn hình lúc tự cuộn, nên nó cần
 * đúng cỡ một dòng mắt bám theo được: hết một dấu chấm là hết, và xuống dòng
 * cũng là hết — một dòng thoại hay một dòng thơ không có dấu chấm nào vẫn là
 * một dòng riêng.
 *
 * @param text   chuỗi gốc, để nhìn được khoảng trắng giữa hai chữ
 * @param tokens kết quả của {@link tokenize}
 * @returns mảng cùng độ dài với `tokens`, mỗi phần tử là số thứ tự câu
 */
export function assignLines(text: string, tokens: TextToken[]): number[] {
  const lines: number[] = new Array<number>(tokens.length).fill(0);
  let line = 0;

  for (let i = 1; i < tokens.length; i += 1) {
    const token = tokens[i];
    const previous = tokens[i - 1];
    if (!token || !previous) continue;

    const gap = text.slice(previous.end, token.start);
    if (gap.includes("\n") || SENTENCE_END.test(previous.text)) {
      line += 1;
    }
    lines[i] = line;
  }

  return lines;
}

function isSpace(character: string): boolean {
  return SPACE.test(character);
}
