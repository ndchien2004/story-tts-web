import { useEffect, useLayoutEffect, useRef, useState } from "react";
import useStoryAssistant, { QUICK_ACTIONS } from "../hooks/useStoryAssistant";
import { Alert, Button, ButtonLink } from "./ui";

/* ------------------------------------------------------------------ */
/* Icons                                                               */
/* ------------------------------------------------------------------ */

const icon = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

/* Cùng ngôi sao với nút "Nghe bằng AI" dưới thanh: cùng một nghĩa — chỗ nào có
   hình này là chỗ có máy làm hộ. */
const SparkIcon = () => (
  <svg {...icon}>
    <path d="M12 3.2l1.8 4.9 4.9 1.8-4.9 1.8L12 16.6l-1.8-4.9L5.3 9.9l4.9-1.8z" />
    <path d="M18.5 15.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" />
  </svg>
);

const CloseIcon = () => (
  <svg {...icon} strokeWidth={2.2}>
    <path d="m6 6 12 12M18 6 6 18" />
  </svg>
);

const SendIcon = () => (
  <svg {...icon} strokeWidth={2.1}>
    <path d="M4.5 12h13" />
    <path d="m12 6.5 5.5 5.5-5.5 5.5" />
  </svg>
);

const RefreshIcon = () => (
  <svg {...icon}>
    <path d="M20 12a8 8 0 1 1-2.3-5.6" />
    <path d="M20 3.5V8h-4.5" />
  </svg>
);

/* ------------------------------------------------------------------ */
/* Một lượt nói                                                        */
/* ------------------------------------------------------------------ */

/**
 * Một bong bóng chat.
 *
 * <p>Trả lời của trợ lý được vẽ bằng văn bản thuần, không qua bộ dựng Markdown
 * nào — và điều đó là cố ý ở hai đầu: chỉ thị hệ thống bảo mô hình đừng dùng
 * Markdown, còn ở đây thì `white-space: pre-wrap` giữ nguyên xuống dòng. Dựng
 * Markdown từ chuỗi do một mô hình sinh ra là mở một đường chèn HTML mà không
 * đổi lại được gì: câu trả lời dài nhất ở đây cũng chỉ là vài đoạn văn.
 */
function Bubble({ message }) {
  const mine = message.role === "user";

  return (
    <div className={`assistant-turn ${mine ? "is-user" : "is-ai"}`}>
      <p className="assistant-bubble">{message.content}</p>

      {message.truncated && (
        <p className="assistant-hint">
          Chương này rất dài nên trợ lý chỉ đọc được phần đầu và phần cuối.
        </p>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Màn hình lúc chưa hỏi gì                                            */
/* ------------------------------------------------------------------ */

/**
 * Lời chào và bốn câu gợi ý.
 *
 * <p>Các nút này chỉ là đường tắt để gõ sẵn một câu hỏi — không có một mẩu
 * logic AI nào ở phía trình duyệt. Chúng gửi đi đúng cái mà ô nhập gửi đi, nên
 * thêm bớt một gợi ý không đụng tới máy chủ.
 */
function EmptyState({ onPick, disabled }) {
  return (
    <div className="assistant-empty">
      <p className="assistant-greeting">
        Mình đã đọc chương bạn đang mở. Hỏi mình về nội dung chương nhé.
      </p>

      <div className="assistant-quick">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.label}
            type="button"
            className="assistant-quick-btn"
            disabled={disabled}
            onClick={() => onPick(action.prompt)}
          >
            {action.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Hộp trợ lý                                                          */
/* ------------------------------------------------------------------ */

/**
 * Trợ lý đọc truyện — hộp chat nhỏ ở góc dưới bên phải trang đọc.
 *
 * <h3>Nó nằm ở đâu, và vì sao chỗ ấy không cần một dòng JavaScript nào</h3>
 * Yêu cầu là "ngay phía trên thanh nghe, và đừng chồng lên nó" — mà thanh nghe
 * có chiều cao thay đổi: mở phần mở rộng ra là nó cao gấp mấy lần. Cách hiển
 * nhiên là đo chiều cao ấy rồi trừ đi, bằng ResizeObserver.
 *
 * <p>Không cần. Trên màn hình rộng, trang đọc là một cột co giãn gồm ba dải —
 * thanh trên, trang chữ, thanh nghe — và `.reader-grid` (dải giữa) <i>kết thúc
 * đúng ở chỗ thanh nghe bắt đầu</i>. Đặt hộp này vào trong `.reader-grid` với
 * `position: absolute; bottom: 0` là nó tự nằm sát trên thanh nghe, và tự dịch
 * lên khi thanh ấy cao lên. Đo đạc bằng CSS, không bằng JS.
 *
 * <p>Trên màn hình hẹp thì trang trở về cuộn bình thường và thanh nghe tự dán
 * vào đáy khung nhìn, nên hộp cũng chuyển sang `fixed` và chừa đúng khoảng
 * `--reader-dock-height` — con số mà trang chữ vốn đã chừa sẵn cho thanh ấy.
 *
 * <h3>Khi nào nó không xuất hiện</h3>
 * Máy chủ chưa có API key thì `available` là false và cả component trả về null.
 * Không có nút mờ, không có lời giải thích — một tính năng chưa bật thì không
 * cần chiếm một góc màn hình để nói rằng nó chưa bật.
 */
export default function StoryAssistant({ chapterId, isAuthenticated }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");

  const assistant = useStoryAssistant(chapterId);
  const { messages, loading, error, available, remainingToday, maxQuestionChars } = assistant;

  const listRef = useRef(null);
  const inputRef = useRef(null);

  /*
   * Cuộn xuống lượt mới nhất.
   *
   * `useLayoutEffect` chứ không phải `useEffect`: chạy sau khi trình duyệt đã
   * dựng xong bố cục nhưng trước khi nó vẽ, nên không ai kịp thấy khung cuộn
   * nhảy một cái.
   */
  useLayoutEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, loading]);

  /* Mở hộp ra là con trỏ nằm sẵn trong ô nhập — mở nó ra để gõ chứ để làm gì. */
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  /* Đổi chương thì bản nháp cũng hết nghĩa: nó là câu hỏi về chương vừa rời đi. */
  useEffect(() => setDraft(""), [chapterId]);

  // Chưa hỏi xong máy chủ (null), hoặc máy chủ không bật: không vẽ gì cả.
  if (!available) return null;

  const submit = (text) => {
    assistant.send(text);
    setDraft("");
  };

  /* ---- Thu gọn: chỉ còn một nút ---- */
  if (!open) {
    return (
      <div className="assistant-anchor">
        <button
          type="button"
          className="assistant-fab"
          aria-label="Mở trợ lý AI để hỏi về chương này"
          title="Hỏi AI về chương này"
          onClick={() => setOpen(true)}
        >
          <SparkIcon />
          <span>Hỏi AI</span>
        </button>
      </div>
    );
  }

  return (
    <div className="assistant-anchor">
      <section className="assistant-panel" aria-label="Trợ lý AI">
        <header className="assistant-head">
          <span className="assistant-title">
            <SparkIcon />
            Trợ lý đọc truyện
          </span>

          <span className="assistant-head-actions">
            {messages.length > 0 && (
              <button
                type="button"
                className="assistant-icon-btn"
                aria-label="Xoá cuộc trò chuyện này"
                title="Bắt đầu lại"
                onClick={assistant.reset}
              >
                <RefreshIcon />
              </button>
            )}

            <button
              type="button"
              className="assistant-icon-btn"
              aria-label="Thu gọn trợ lý AI"
              title="Thu gọn"
              onClick={() => setOpen(false)}
            >
              <CloseIcon />
            </button>
          </span>
        </header>

        {/*
          Chưa đăng nhập thì nói ngay ở đây, đừng để họ gõ xong một câu hỏi rồi
          mới nhận 401. Hộp vẫn mở được, vì thấy được nó tồn tại chính là lý do
          người ta chịu đăng nhập.
        */}
        {!isAuthenticated ? (
          <div className="assistant-gate">
            <p>Trợ lý AI cần bạn đăng nhập — mỗi câu hỏi được tính vào lượt riêng của bạn.</p>
            <ButtonLink to="/dang-nhap" variant="primary" size="sm">
              Đăng nhập
            </ButtonLink>
          </div>
        ) : (
          <>
            <div className="assistant-log" ref={listRef} aria-live="polite">
              {messages.length === 0 && !loading ? (
                <EmptyState onPick={submit} disabled={loading} />
              ) : (
                messages.map((message, index) => (
                  // Chỉ số làm khoá là đủ và đúng: danh sách này chỉ mọc thêm ở
                  // cuối, không bao giờ chèn giữa hay đảo chỗ.
                  <Bubble key={index} message={message} />
                ))
              )}

              {loading && (
                <div className="assistant-turn is-ai">
                  <p className="assistant-bubble assistant-thinking">
                    <span className="spinner" aria-hidden="true" />
                    Đang đọc chương…
                  </p>
                </div>
              )}
            </div>

            {error && (
              <div className="assistant-error">
                <Alert tone="error">{error}</Alert>
              </div>
            )}

            <form
              className="assistant-compose"
              onSubmit={(event) => {
                event.preventDefault();
                submit(draft);
              }}
            >
              <input
                ref={inputRef}
                className="nb-input assistant-input"
                type="text"
                value={draft}
                maxLength={maxQuestionChars}
                placeholder="Hỏi về chương này…"
                aria-label="Câu hỏi cho trợ lý AI"
                disabled={loading}
                onChange={(event) => setDraft(event.target.value)}
              />

              <Button
                type="submit"
                className="nb-icon-btn assistant-send"
                variant="primary"
                aria-label="Gửi câu hỏi"
                title="Gửi câu hỏi"
                // Ô rỗng không gửi được, và trong lúc đang chờ thì cũng không:
                // hai lần bấm là hai lượt bị tiêu cho một câu hỏi.
                disabled={loading || draft.trim().length === 0}
              >
                <SendIcon />
              </Button>
            </form>

            <p className="assistant-foot">
              {remainingToday != null
                ? `Còn ${remainingToday} lượt hỏi hôm nay · trả lời dựa trên chương đang đọc`
                : "Trả lời dựa trên chương bạn đang đọc"}
            </p>
          </>
        )}
      </section>
    </div>
  );
}
