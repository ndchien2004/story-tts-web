import { useCallback, useEffect, useRef, useState } from "react";
import { assistantApi } from "../api/endpoints";

/**
 * Số lượt cũ gửi kèm mỗi câu hỏi.
 *
 * <p>Máy chủ cũng cắt (xem `app.ai.max-history-turns`), nên đây không phải hàng
 * rào bảo vệ — nó là phép lịch sự: không gửi đi thứ chắc chắn sẽ bị vứt. Sáu
 * lượt là ba lượt hỏi đáp, đủ để "tại sao anh ta lại làm vậy?" còn hiểu được
 * "anh ta" là ai.
 */
const HISTORY_TURNS = 6;

/** Bốn câu mở màn, để người mở hộp ra không phải nghĩ nên gõ gì. */
export const QUICK_ACTIONS = [
  { label: "Tóm tắt chương", prompt: "Tóm tắt chương này giúp tôi." },
  { label: "Nhân vật chính", prompt: "Chương này có những nhân vật nào, và họ làm gì?" },
  { label: "Chuyện gì đã xảy ra?", prompt: "Kể lại diễn biến chính của chương này." },
  { label: "Kết chương ra sao?", prompt: "Đoạn cuối chương xảy ra chuyện gì?" },
];

/**
 * Trạng thái của hộp trợ lý AI cho đúng một chương.
 *
 * <h3>Điều quan trọng nhất: đổi chương là xoá sạch</h3>
 * Hộp chat sống lâu hơn chương đang mở — người đọc bấm "chương sau" thì component
 * này không bị gỡ đi, chỉ có `chapterId` đổi. Nếu không xoá, câu hỏi "tóm tắt
 * chương này" ở chương 11 sẽ được trả lời kèm nguyên cuộc hội thoại về chương
 * 10, và câu trả lời sẽ sai một cách rất khó nhận ra: nó vẫn trôi chảy, vẫn
 * đúng ngữ pháp, chỉ là nói về chương khác.
 *
 * <p>Máy chủ không giúp được ở đây, vì máy chủ không có trí nhớ nào để mà lệch:
 * mỗi lời gọi tự tra lại nội dung chương từ `chapterId` được gửi lên. Chỗ duy
 * nhất trạng thái cũ đọng lại là `messages` trong trình duyệt, nên chỗ duy nhất
 * sửa được cũng là đây.
 */
export default function useStoryAssistant(chapterId) {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(null);

  /*
   * Lịch sử gửi lên máy chủ đọc từ ref chứ không từ state.
   *
   * `send` được gọi từ một trình xử lý sự kiện và chạy bất đồng bộ; đọc
   * `messages` trong đó là đọc bản chụp của lần render đã tạo ra hàm ấy. Bấm
   * gửi hai lần thật nhanh thì lần thứ hai gửi đi một lịch sử thiếu mất lượt
   * đầu. Ref thì luôn là bản mới nhất.
   */
  const historyRef = useRef([]);

  /* ---- Đổi chương: xoá sạch, không giữ lại gì ---- */
  useEffect(() => {
    setMessages([]);
    setError(null);
    setLoading(false);
    historyRef.current = [];
  }, [chapterId]);

  /* ---- Trợ lý có dùng được không, và còn bao nhiêu lượt ---- */
  useEffect(() => {
    let cancelled = false;

    assistantApi
      .status()
      .then((data) => {
        if (!cancelled) setStatus(data);
      })
      // Hỏi trạng thái mà hỏng thì coi như không có tính năng. Không báo lỗi:
      // người đọc chưa hỏi gì cả, và một hộp báo lỗi về một thứ họ chưa đụng
      // tới là tiếng ồn.
      .catch(() => {
        if (!cancelled) setStatus({ enabled: false });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const send = useCallback(
    async (rawText) => {
      const text = rawText?.trim();
      if (!text || loading) return;

      const question = { role: "user", content: text };
      setMessages((previous) => [...previous, question]);
      setError(null);
      setLoading(true);

      // Chụp lại lịch sử TRƯỚC câu hỏi này: câu hỏi mới đi ở trường `message`
      // riêng, nên gửi kèm nó trong `history` nữa là gửi hai lần.
      const history = historyRef.current.slice(-HISTORY_TURNS);
      historyRef.current = [...historyRef.current, question];

      try {
        const reply = await assistantApi.ask({ chapterId, message: text, history });

        const answer = { role: "assistant", content: reply.message, truncated: reply.truncated };
        historyRef.current = [...historyRef.current, answer];
        setMessages((previous) => [...previous, answer]);

        // Con số về cùng câu trả lời, không phải một lời gọi riêng.
        setStatus((previous) =>
          previous ? { ...previous, remainingToday: reply.remainingToday } : previous,
        );
      } catch (err) {
        // Câu hỏi ở lại trên màn hình. Nó là thứ người đọc vừa gõ, và xoá đi
        // nghĩa là bắt họ gõ lại chỉ vì máy chủ hỏng.
        setError(err.message ?? "Trợ lý AI hiện không phản hồi. Vui lòng thử lại.");

        // Nhưng nó rời khỏi lịch sử gửi đi: một câu chưa từng được trả lời thì
        // không phải một lượt hội thoại, và giữ nó lại sẽ làm lệch cặp
        // hỏi–đáp mà mô hình đọc.
        historyRef.current = historyRef.current.slice(0, -1);
      } finally {
        setLoading(false);
      }
    },
    [chapterId, loading],
  );

  /** Xoá cuộc đang có mà vẫn ở nguyên chương — người đọc tự bấm. */
  const reset = useCallback(() => {
    setMessages([]);
    setError(null);
    historyRef.current = [];
  }, []);

  return {
    messages,
    loading,
    error,
    /** null nghĩa là chưa hỏi xong máy chủ — chưa biết nên vẽ gì. */
    available: status?.enabled ?? null,
    remainingToday: status?.remainingToday ?? null,
    maxQuestionChars: status?.maxQuestionChars ?? 500,
    send,
    reset,
  };
}
