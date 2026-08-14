import { useEffect, useMemo, useState } from "react";
import { audioApi } from "../api/endpoints";
import { alignTranscript } from "./karaoke/align";
import { WordTimeline } from "./karaoke/WordTimeline";
import type { ChapterTranscript, WordTimestamp } from "./types";

/**
 * Lấy mốc thời gian của một bản audio và dóng nó vào nội dung chương.
 *
 * <h3>Một lời gọi cho cả chương</h3>
 * Toàn bộ mảng được tải một lần khi mở chương, rồi mọi việc dò tìm diễn ra ngay
 * trong trình duyệt. Hỏi máy chủ theo nhịp phát thì phần tô sáng sẽ giật đúng
 * theo chất lượng đường truyền — mà thứ đang cố đạt được ở đây chính là không
 * giật.
 *
 * <h3>Ngưỡng khớp</h3>
 * Chương có thể đã được sửa chữ sau khi bản audio dựng xong. Khi ấy mốc cũ vẫn
 * tải về được nhưng dóng vào không khớp, và tô sáng theo nó là chỉ sai chỗ suốt
 * cả chương. Nên có một ngưỡng: dưới ngưỡng thì coi như không có mốc, và trang
 * đọc nói thẳng là chương này không tô sáng theo được.
 */

/** Dưới ngần này chữ khớp được thì bản mốc ấy không thuộc về chương này nữa. */
const MIN_MATCH_RATIO = 0.6;

export interface UseTranscriptResult {
  timeline: WordTimeline | null;
  loading: boolean;
  /** Có mốc, nhưng mốc ấy không còn khớp với chữ đang hiển thị. */
  stale: boolean;
  error: string | null;
}

export function useTranscript(
  chapterId: string | number | null | undefined,
  audioId: number | null | undefined,
  content: string | null | undefined,
  enabled: boolean,
): UseTranscriptResult {
  const [transcript, setTranscript] = useState<ChapterTranscript | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    setTranscript(null);
    setError(null);

    if (!enabled || chapterId == null || audioId == null) {
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    audioApi
      .transcript(chapterId, audioId)
      .then((data: unknown) => {
        if (cancelled) return;
        setTranscript(parseTranscript(data));
      })
      .catch(() => {
        if (cancelled) return;
        // Không tô sáng được thì vẫn nghe được. Một dòng báo lỗi đỏ cho một
        // tính năng phụ trợ là làm hỏng buổi nghe vì một thứ không ai mất.
        setError("Không tải được phần bám chữ theo giọng đọc.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [audioId, chapterId, enabled]);

  /*
   * Dóng chữ là việc nặng nhất của cả tính năng — năm nghìn chữ, một lượt chuẩn
   * hoá Unicode cho mỗi chữ. Nó chỉ được chạy lại khi nội dung chương hoặc bản
   * mốc thật sự đổi, chứ không phải mỗi lần trang đọc vẽ lại.
   */
  const { timeline, stale } = useMemo(() => {
    if (!transcript || !content || transcript.timestamps.length === 0) {
      return { timeline: null, stale: false };
    }

    const data = alignTranscript(content, transcript.timestamps);
    if (data.matchRatio < MIN_MATCH_RATIO) {
      return { timeline: null, stale: true };
    }

    return { timeline: new WordTimeline(data), stale: false };
  }, [content, transcript]);

  return { timeline, loading, stale, error };
}

/**
 * Đọc câu trả lời của máy chủ thành thứ dùng được, hoặc thành rỗng.
 *
 * Kiểm từng trường thay vì ép kiểu: đây là dữ liệu từ ngoài vào, và một mốc
 * thời gian là `null` lọt được tới vòng lặp mỗi khung hình sẽ thành `NaN`, mà
 * `NaN` thì làm phép tìm nhị phân trả lời lung tung chứ không ném lỗi ở đâu cả.
 */
function parseTranscript(raw: unknown): ChapterTranscript | null {
  if (!raw || typeof raw !== "object") return null;

  const value = raw as Record<string, unknown>;
  const rows = Array.isArray(value.timestamps) ? value.timestamps : [];

  const timestamps: WordTimestamp[] = [];
  for (const row of rows) {
    const word = parseWord(row);
    if (word) timestamps.push(word);
  }

  return {
    storyId: typeof value.storyId === "number" ? value.storyId : null,
    chapterId: typeof value.chapterId === "number" ? value.chapterId : 0,
    audioId: typeof value.audioId === "number" ? value.audioId : 0,
    audioUrl: typeof value.audioUrl === "string" ? value.audioUrl : "",
    contentHash: typeof value.contentHash === "string" ? value.contentHash : null,
    wordCount: timestamps.length,
    timestamps,
  };
}

function parseWord(raw: unknown): WordTimestamp | null {
  if (!raw || typeof raw !== "object") return null;

  const value = raw as Record<string, unknown>;
  const word = typeof value.word === "string" ? value.word : "";
  const start = Number(value.start);
  const end = Number(value.end);

  if (!word || !Number.isFinite(start) || !Number.isFinite(end)) return null;

  const charStart = Number(value.charStart);
  const charEnd = Number(value.charEnd);

  return {
    word,
    start,
    end: Math.max(start, end),
    charStart: Number.isFinite(charStart) ? charStart : -1,
    charEnd: Number.isFinite(charEnd) ? charEnd : -1,
  };
}

export default useTranscript;
