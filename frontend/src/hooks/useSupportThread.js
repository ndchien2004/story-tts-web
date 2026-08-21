import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { adminSupportApi, supportApi } from "../api/endpoints";
import {
  CONNECTED,
  CONNECTING,
  DISCONNECTED,
  ERROR,
  RECONNECTING,
  UNAVAILABLE,
  useSupportSocket,
} from "../context/support-socket-context";

/**
 * Một luồng hỗ trợ, đủ để một màn hình chat dựng lên từ nó.
 *
 * <h3>Kết nối không thuộc về hook này</h3>
 * Nó đi mượn cái socket dùng chung của cả tab — xem `SupportSocketProvider`.
 * Bản đầu tự mở lấy một kết nối, và đó là một lỗi có hậu quả cụ thể: kết nối
 * chỉ sống khi có khung chat đang mở, nên trang quản trị ngồi nhìn danh sách
 * mà chưa chọn hội thoại nào thì không nhận được gì cho tới khi bấm F5.
 *
 * <p>Hook này giờ chỉ còn lo phần *dữ liệu của một luồng*: tải, gộp, gửi, đánh
 * dấu đã đọc.
 *
 * <h3>Máy chủ là nguồn sự thật; chỗ này chỉ là bản sao gần đây nhất</h3>
 * Không có phép cộng trừ nào chạy một mình ở đây. Số chưa đọc, trạng thái
 * luồng, mốc "đã xem" của bên kia — cả ba đều đến từ máy chủ, kèm theo mỗi
 * khung tin và mỗi lượt đồng bộ. Tự cộng thêm một sẽ lệch dần theo từng khung
 * tin bị bỏ lỡ, và không có gì kéo nó về.
 *
 * <h3>Ba đường đồng bộ, và vì sao cần cả ba</h3>
 * <pre>
 *   mở màn hình        → tải trang mới nhất → phần đã có từ trước
 *   WebSocket nối được → tải trang mới nhất → phần bỏ lỡ lúc mất mạng
 *   tab quay lại       → tải trang mới nhất → phần bỏ lỡ lúc trình duyệt ngủ
 * </pre>
 *
 * <h3>Vì sao nối lại thì tải *trang mới nhất*, không xin "phần sau con trỏ"</h3>
 * Đây là chỗ vá một khoảng hở có thật. Số thứ tự tin nhắn do cơ sở dữ liệu cấp
 * lúc ghi, không phải lúc commit. Hai giao dịch song song vì thế có thể commit
 * ngược thứ tự id: tin 11 commit trước tin 10. Một trình duyệt đồng bộ đúng vào
 * khoảnh khắc ấy sẽ thấy 11 mà không thấy 10 — và nếu nó ghi nhớ "đã tới 11"
 * rồi từ đó chỉ xin `after=11`, thì tin số 10 <b>vĩnh viễn</b> không bao giờ về.
 *
 * <p>Tải lại trang cuối không có vấn đề ấy: nó đọc trạng thái đã commit tại một
 * thời điểm *sau* khoảnh khắc hở, nên nó thấy cả hai. Gộp theo id và bỏ trùng
 * khiến việc tải lại vài chục tin đã có không gây ra gì.
 *
 * <h3>Trạng thái của một tin nhắn</h3>
 * <pre>
 *   PENDING → đã vẽ lên màn hình, chưa có lời báo nhận
 *   SENT    → máy chủ đã ghi xuống cơ sở dữ liệu và đã commit
 *   FAILED  → bị từ chối, hoặc gửi không đi được; bấm để thử lại
 * </pre>
 *
 * Thử lại giữ nguyên `clientMessageId`. Đó là toàn bộ cơ chế chống trùng: máy
 * chủ nhận ra lần thử thứ hai và trả về đúng tin đã ghi thay vì ghi thêm một
 * tin nữa.
 *
 * @param {object} options
 * @param {"user"|"admin"} options.mode phía nào đang xem — quyết định gọi API
 *        nào và tin nào là "của tôi". Nó *không* quyết định quyền: việc ấy do
 *        máy chủ làm, và một `mode` bịa ra chỉ dẫn tới một lời từ chối.
 * @param {number|null} options.conversationId chỉ dùng ở `admin`; ở `user` thì
 *        luồng được suy từ quyền sở hữu và tham số này bị bỏ qua.
 * @param {boolean} options.enabled tắt hẳn hook khi màn hình chưa cần tới nó.
 */
export function useSupportThread({ mode = "user", conversationId = null, enabled = true } = {}) {
  const isAdmin = mode === "admin";
  const mySide = isAdmin ? "ADMIN" : "USER";

  const socket = useSupportSocket();

  const [conversation, setConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState(null);

  /*
   * Màn hình nào đang được phục vụ, đọc được từ bên trong những hàm bất đồng bộ.
   *
   * Một response rời máy chủ trước khi người dùng chuyển sang luồng khác vẫn có
   * thể về sau đó. So khóa này ngay trước khi ghi vào state là thứ chặn nó vẽ
   * hội thoại của người này lên màn hình đang mở hội thoại của người kia.
   */
  const scopeRef = useRef(null);
  const scope = enabled && (!isAdmin || conversationId != null)
    ? `${mode}:${conversationId ?? "me"}`
    : null;
  scopeRef.current = scope;

  const messagesRef = useRef([]);
  messagesRef.current = messages;

  /* ---------------- Trợ lý AI ---------------- */

  // Một bản sao trong ref, không phải trong danh sách phụ thuộc của `deliver`.
  //
  // Lý do: chế độ của luồng đổi *giữa chừng* — người đọc bấm "Chat với tư vấn
  // viên" trong lúc một lượt gửi đang bay. Một `useCallback` chốt giá trị cũ
  // vào bao đóng sẽ gửi lượt kế tiếp qua đúng đường vừa bị đóng lại. Ref thì
  // luôn đọc ra giá trị của lúc gọi.
  const conversationRef = useRef(null);
  conversationRef.current = conversation;

  // Trợ lý đang soạn. Tách khỏi `sending` của ô soạn tin: cái kia đo một lượt
  // POST vài chục mili giây, cái này đo một lời gọi Gemini có thể tới ba mươi
  // giây — và đó là quãng bắt buộc phải nhìn thấy được, nếu không thì ô chat
  // trông như đã chết.
  const [thinking, setThinking] = useState(false);

  // Câu giải thích khi trợ lý không trả lời được, và lời mời chuyển tiếp.
  //
  // Không phải một tin nhắn trong luồng: nó không được ghi vào cơ sở dữ liệu,
  // nên ghép nó vào danh sách tin sẽ là một dòng biến mất sau khi tải lại
  // trang. Nó sống đúng một phiên, ở ngay trên ô soạn tin.
  const [assistantNotice, setAssistantNotice] = useState(null);
  const [suggestHandoff, setSuggestHandoff] = useState(false);

  /* ---------------------------------------------------------------- */
  /* Gộp tin nhắn                                                      */
  /* ---------------------------------------------------------------- */

  /**
   * Gộp một mẻ tin vào danh sách đang có.
   *
   * Ba quy tắc, theo thứ tự, và cả ba đều cần thiết:
   *
   *   1. cùng `id` → thay tại chỗ. Đây là chỗ chặn một tin hiện hai lần khi
   *      khung tin đẩy và lượt đồng bộ cùng mang nó về — chuyện xảy ra ở *mọi*
   *      lần nối lại, không phải một trường hợp hiếm.
   *   2. cùng `clientMessageId` và cùng phía với mình → thay cái đang PENDING.
   *      Đây là lúc bong bóng lạc quan biến thành tin thật, và là lý do nó
   *      không bị nối thêm vào cuối thành một bản sao.
   *   3. còn lại → chèn vào, rồi sắp lại theo id.
   */
  const mergeMessages = useCallback((incoming) => {
    setMessages((current) => merge(current, incoming, mySide));
  }, [mySide]);

  /* ---------------------------------------------------------------- */
  /* Đồng bộ                                                           */
  /* ---------------------------------------------------------------- */

  const fetchSlice = useCallback(
    (params) => (isAdmin
      ? adminSupportApi.messages(conversationId, params)
      : supportApi.thread(params)),
    [isAdmin, conversationId],
  );

  /** Tải trang mới nhất và gộp vào. Xem ghi chú ở đầu tệp. */
  const resync = useCallback(async () => {
    const mine = scopeRef.current;
    if (!mine) return;

    setLoading(true);
    try {
      const slice = await fetchSlice({});
      if (scopeRef.current !== mine) return;

      setConversation(slice.conversation);
      mergeMessages(slice.messages ?? []);
      setHasMore(slice.hasMore ?? false);
      setError(null);
    } catch (ex) {
      if (scopeRef.current !== mine) return;
      // Màn hình giữ nguyên những gì đang có: một lượt đồng bộ hỏng không đáng
      // xóa sạch hội thoại người ta đang đọc. Lượt kế tiếp sửa nó.
      setError(ex?.message ?? "Không tải được cuộc trò chuyện.");
    } finally {
      if (scopeRef.current === mine) setLoading(false);
    }
  }, [fetchSlice, mergeMessages]);

  /** Cuộn lên: một trang tin cũ hơn, nối vào đầu danh sách. */
  const loadOlder = useCallback(async () => {
    const mine = scopeRef.current;
    if (!mine || loadingOlder || !hasMore) return;

    const oldest = messagesRef.current.find((m) => m.id != null);
    if (!oldest) return;

    setLoadingOlder(true);
    try {
      const slice = await fetchSlice({ before: oldest.id });
      if (scopeRef.current !== mine) return;
      mergeMessages(slice.messages ?? []);
      setHasMore(slice.hasMore ?? false);
    } catch {
      // Nút "xem thêm" vẫn còn đó; bấm lại là thử lại. Không có gì để dọn.
    } finally {
      if (scopeRef.current === mine) setLoadingOlder(false);
    }
  }, [fetchSlice, hasMore, loadingOlder, mergeMessages]);

  /* ---------------------------------------------------------------- */
  /* Mở màn hình, nối lại, tab quay lại                                */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!scope) {
      setConversation(null);
      setMessages([]);
      setHasMore(false);
      setError(null);
      return;
    }
    // Xóa trước khi tải: chuyển sang luồng khác thì hội thoại cũ không được
    // phép nán lại trên màn hình dù chỉ một khung hình.
    setMessages([]);
    setConversation(null);
    resync();
  }, [scope, resync]);

  /*
   * Đường thông trở lại là một lần đồng bộ.
   *
   * Khoảng giữa lúc mất kết nối và lúc nối lại chính là khoảng mà những tin bị
   * bỏ lỡ nằm trong đó. `live` chuyển false → true là dấu hiệu duy nhất báo
   * rằng khoảng ấy vừa khép lại.
   */
  const live = socket?.live ?? false;
  const wasLive = useRef(false);
  useEffect(() => {
    // Chỉ ở lúc CHUYỂN từ mất kết nối sang có, không phải mỗi lần cờ live được
    // đọc lại. Thiếu phép so này thì mở một hội thoại trong lúc socket vốn đã
    // thông sẽ gọi hai lượt đồng bộ giống hệt nhau: một của hiệu ứng mở màn
    // hình, một của hiệu ứng này.
    const reconnected = live && !wasLive.current;
    wasLive.current = live;
    if (scope && reconnected) resync();
  }, [scope, live, resync]);

  useEffect(() => {
    if (!scope || typeof document === "undefined") return undefined;
    const onVisible = () => {
      if (document.visibilityState === "visible") resync();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [scope, resync]);

  /* ---------------------------------------------------------------- */
  /* Khung tin đi xuống                                                */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!scope || !socket) return undefined;

    return socket.subscribe((frame) => {
      switch (frame.type) {
        case "message:new": {
          const payload = frame.payload ?? {};
          if (!payload.message) break;

          // Kết nối dùng chung mang khung tin của MỌI luồng — danh sách hộp thư
          // của quản trị viên cần chúng. Khung chat thì chỉ lấy phần của luồng
          // nó đang vẽ.
          if (isAdmin && payload.message.conversationId !== conversationId) break;

          mergeMessages([payload.message]);
          const state = isAdmin ? payload.inbox?.conversation : payload.conversation;
          if (state) setConversation(state);
          break;
        }

        case "message:ack": {
          const ack = frame.payload ?? {};
          if (isAdmin && ack.conversationId !== conversationId) break;
          setMessages((current) => applyAck(current, ack));
          break;
        }

        case "message:read": {
          const read = frame.payload ?? {};
          if (isAdmin && read.conversationId !== conversationId) break;
          setConversation((current) => applyRead(current, read, mySide));
          break;
        }

        case "error": {
          const failure = frame.payload ?? {};
          if (failure.clientMessageId) {
            // Lỗi thuộc về một lượt gửi cụ thể: đánh dấu đúng bong bóng ấy là
            // thất bại thay vì hiện một thông báo chung chung mà người dùng
            // không biết nó nói về câu nào.
            setMessages((current) => markFailed(current, failure.clientMessageId, failure.message));
          } else {
            setError(failure.message ?? "Đã có lỗi xảy ra.");
          }
          break;
        }

        default:
          // Máy chủ mới hơn trình duyệt: bỏ qua loại khung tin chưa biết thay
          // vì hỏng. Đó là toàn bộ lý do có trường `type` ở ngoài cùng.
          break;
      }
    });
  }, [scope, socket, isAdmin, conversationId, mergeMessages, mySide]);

  /* ---------------------------------------------------------------- */
  /* Gửi                                                               */
  /* ---------------------------------------------------------------- */

  /**
   * Gửi qua WebSocket nếu đường đang thông, ngược lại qua REST.
   *
   * Hai đường cho ra cùng một kết quả vì chúng gọi vào cùng một chỗ ở máy chủ,
   * và vì việc chống trùng nằm ở ràng buộc của cơ sở dữ liệu chứ không ở tầng
   * vận chuyển. Nên một tin gửi hụt qua WebSocket rồi thử lại qua REST — hoặc
   * ngược lại — vẫn chỉ thành một tin nhắn.
   */
  /**
   * Một lượt hỏi trợ lý.
   *
   * <p>Cố ý KHÔNG đi qua WebSocket, khác hẳn lượt gửi thường: máy chủ phải chờ
   * Gemini rồi mới trả lời, và giữ một luồng xử lý socket đứng chờ quãng ấy là
   * chuyện không làm. Đổi lại, câu trả lời về theo *hai* đường — trong phản hồi
   * HTTP này, và trong một khung `message:new` cho các tab khác — nên `merge`
   * phải chịu được việc thấy cùng một tin hai lần. Nó chịu được: gộp theo `id`.
   */
  const askAssistant = useCallback(async (clientMessageId, content) => {
    setThinking(true);
    setAssistantNotice(null);
    try {
      const result = await supportApi.askAssistant({ clientMessageId, content });
      if (scopeRef.current == null) return;

      // Câu hỏi trước, câu trả lời sau, trong một lần gộp: hai lần setState
      // liên tiếp sẽ vẽ một khung hình có câu hỏi mà chưa có câu trả lời, và
      // mắt bắt được cái nháy ấy.
      mergeMessages([result.message, result.reply].filter(Boolean));
      if (result.conversation) setConversation(result.conversation);
      setAssistantNotice(result.notice ?? null);
      setSuggestHandoff(Boolean(result.suggestHandoff));
    } catch (ex) {
      // 409 ở đây nghĩa là luồng đã rời khỏi tay trợ lý trong lúc lượt này bay
      // — người đọc bấm chuyển tư vấn viên ở một tab khác, hoặc một quản trị
      // viên vừa nhảy vào. Máy chủ là nguồn sự thật, nên đọc lại nó thay vì
      // đoán: một lần đồng bộ đổi luôn cả chế độ lẫn ô soạn tin.
      resync();
      throw ex;
    } finally {
      if (scopeRef.current != null) setThinking(false);
    }
  }, [mergeMessages, resync]);

  const deliver = useCallback(async (clientMessageId, content) => {
    // Luồng đang do trợ lý phụ trách thì lượt gửi là một lượt hỏi. Đọc từ ref,
    // không từ bao đóng — xem ghi chú ở `conversationRef`.
    if (!isAdmin && conversationRef.current?.assistantMode === "AI") {
      return askAssistant(clientMessageId, content);
    }

    const sentOverSocket = socket?.send({
      type: "message:send",
      clientMessageId,
      content,
      // Người đọc không gửi trường này và máy chủ cũng không đọc tới nó trên
      // nhánh của họ — luồng được suy từ quyền sở hữu.
      conversationId: isAdmin ? conversationId : undefined,
    });
    if (sentOverSocket) return;

    const result = isAdmin
      ? await adminSupportApi.reply(conversationId, { clientMessageId, content })
      : await supportApi.send({ clientMessageId, content });

    if (scopeRef.current == null) return;
    mergeMessages([result.message]);
    if (result.conversation) setConversation(result.conversation);
  }, [socket, isAdmin, conversationId, mergeMessages, askAssistant]);

  const send = useCallback(async (raw) => {
    const content = (raw ?? "").trim();
    if (!content) return false;

    const clientMessageId = newClientMessageId();
    setMessages((current) => [...current, optimistic(clientMessageId, content, mySide)]);

    try {
      await deliver(clientMessageId, content);
      return true;
    } catch (ex) {
      setMessages((current) => markFailed(current, clientMessageId, ex?.message));
      return false;
    }
  }, [deliver, mySide]);

  /**
   * Gửi lại một tin đã thất bại, giữ nguyên định danh của nó.
   *
   * Giữ nguyên là điều kiện, không phải tiện lợi: sinh một định danh mới sẽ
   * biến một lần thử lại thành một tin nhắn thứ hai đúng vào trường hợp mà
   * chống trùng sinh ra để lo — máy chủ đã ghi xong nhưng lời báo nhận không về
   * tới nơi.
   */
  const retry = useCallback(async (clientMessageId) => {
    const target = messagesRef.current.find((m) => m.clientMessageId === clientMessageId);
    if (!target || target.status !== FAILED) return false;

    setMessages((current) => current.map((m) => (
      m.clientMessageId === clientMessageId ? { ...m, status: PENDING, error: null } : m
    )));

    try {
      await deliver(clientMessageId, target.content);
      return true;
    } catch (ex) {
      setMessages((current) => markFailed(current, clientMessageId, ex?.message));
      return false;
    }
  }, [deliver]);

  /* ---------------------------------------------------------------- */
  /* Đã đọc                                                            */
  /* ---------------------------------------------------------------- */

  const lastReadable = useMemo(() => {
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].id != null) return messages[i].id;
    }
    return null;
  }, [messages]);

  const sentReadMark = useRef(0);

  /**
   * Báo lên máy chủ rằng phía này đã xem tới đâu.
   *
   * Chỉ gửi khi mốc thật sự tiến lên — `sentReadMark` chặn một vòng lặp: máy
   * chủ trả lời bằng một khung tin `message:read`, khung tin ấy cập nhật
   * `conversation`, và nếu không có phép so này thì việc ấy lại kích hoạt một
   * lượt báo nữa.
   */
  const markRead = useCallback(() => {
    if (!scopeRef.current || lastReadable == null) return;
    if (lastReadable <= sentReadMark.current) return;
    if ((conversation?.lastReadMessageId ?? 0) >= lastReadable) return;

    sentReadMark.current = lastReadable;

    const sentOverSocket = socket?.send({
      type: "message:read",
      lastMessageId: lastReadable,
      conversationId: isAdmin ? conversationId : undefined,
    });
    if (sentOverSocket) return;

    const request = isAdmin
      ? adminSupportApi.markRead(conversationId, lastReadable)
      : supportApi.markRead(lastReadable);
    request
      .then((state) => {
        if (scopeRef.current) setConversation(state);
      })
      .catch(() => {
        // Cho phép thử lại ở lần sau: mốc chưa tới nơi thì không được coi là đã
        // tới. Con số chưa đọc của lần đồng bộ kế tiếp sẽ kéo mọi thứ về đúng.
        sentReadMark.current = 0;
      });
  }, [lastReadable, conversation?.lastReadMessageId, socket, isAdmin, conversationId]);

  // Đổi luồng thì quên mốc cũ đi, nếu không thì luồng mới sẽ không bao giờ được
  // báo đã đọc cho tới khi có tin nào có id lớn hơn mốc của luồng trước.
  useEffect(() => {
    sentReadMark.current = 0;
  }, [scope]);

  /**
   * Người đọc chọn "Chat với AI".
   *
   * Máy chủ trả về cả một trang tin nhắn, nên một lần gọi là đủ để đổi màn
   * hình — không có bước "đổi chế độ rồi tải lại" nào nhìn thấy được.
   */
  const startAssistant = useCallback(async () => {
    const mine = scopeRef.current;
    if (!mine) return false;
    setLoading(true);
    try {
      const slice = await supportApi.startAssistant();
      if (scopeRef.current !== mine) return false;
      setConversation(slice.conversation);
      mergeMessages(slice.messages ?? []);
      setHasMore(slice.hasMore ?? false);
      setAssistantNotice(null);
      setSuggestHandoff(false);
      setError(null);
      return true;
    } catch (ex) {
      if (scopeRef.current === mine) {
        setError(ex?.message ?? "Không mở được cuộc trò chuyện với trợ lý.");
      }
      return false;
    } finally {
      if (scopeRef.current === mine) setLoading(false);
    }
  }, [mergeMessages]);

  /**
   * Người đọc chọn "Chat với tư vấn viên".
   *
   * Bất biến ở phía máy chủ, nên bấm hai lần không sinh hai phiếu — bên này
   * không cần một cái cờ "đang gửi" cho đúng, chỉ cần cho đẹp. Xem
   * `SupportStore.requestHandoff`.
   */
  const handoff = useCallback(async (reason) => {
    const mine = scopeRef.current;
    if (!mine) return false;
    try {
      const slice = await supportApi.handoff(reason);
      if (scopeRef.current !== mine) return false;
      setConversation(slice.conversation);
      mergeMessages(slice.messages ?? []);
      setHasMore(slice.hasMore ?? false);
      // Lời mời chuyển tiếp đã được nhận. Để nó nằm lại dưới ô soạn tin sau khi
      // người ta đã bấm là mời họ bấm lần nữa cho một việc đã xong.
      setAssistantNotice(null);
      setSuggestHandoff(false);
      return true;
    } catch (ex) {
      if (scopeRef.current === mine) {
        setError(ex?.message ?? "Không chuyển được cho tư vấn viên.");
      }
      return false;
    }
  }, [mergeMessages]);

  const dismissNotice = useCallback(() => setAssistantNotice(null), []);

  return {
    connection: socket?.connection ?? DISCONNECTED,
    live,
    conversation,
    messages,
    hasMore,
    loading,
    loadingOlder,
    error,
    limits: socket?.limits ?? { maxMessageLength: DEFAULT_MAX_LENGTH },
    send,
    retry,
    loadOlder,
    markRead,
    refresh: resync,

    /* Trợ lý AI */
    assistantMode: conversation?.assistantMode ?? null,
    thinking,
    assistantNotice,
    suggestHandoff,
    startAssistant,
    handoff,
    dismissNotice,
  };
}

/* ------------------------------------------------------------------ */
/* Hằng số                                                             */
/* ------------------------------------------------------------------ */

/* Tái xuất để màn hình chat chỉ phải nhập từ một chỗ. */
export { CONNECTED, CONNECTING, DISCONNECTED, ERROR, RECONNECTING, UNAVAILABLE };

export const PENDING = "PENDING";
export const SENT = "SENT";
export const FAILED = "FAILED";

const DEFAULT_MAX_LENGTH = 2000;

/* ------------------------------------------------------------------ */
/* Thao tác trên danh sách                                             */
/* ------------------------------------------------------------------ */

/**
 * Định danh của một lần bấm gửi.
 *
 * `crypto.randomUUID` có ở mọi trình duyệt hiện đại chạy trên HTTPS, nhưng
 * *không* có trên HTTP ở một số trình duyệt — kể cả localhost trên vài phiên
 * bản. Đường lui phải tồn tại, và nó chỉ cần đủ để hai lần bấm không trùng nhau
 * trong một phiên; máy chủ đã có khóa gồm cả người gửi và luồng.
 */
function newClientMessageId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function optimistic(clientMessageId, content, side) {
  return {
    id: null,
    clientMessageId,
    senderRole: side,
    type: "TEXT",
    content,
    // Mốc tạm để bong bóng có giờ hiện ra ngay. Nó bị thay bằng mốc của máy chủ
    // khi lời báo nhận về — đồng hồ máy khách không bao giờ quyết định thứ tự.
    createdAt: new Date().toISOString(),
    status: PENDING,
  };
}

/** Xem ghi chú ở `mergeMessages`. */
function merge(current, incoming, mySide) {
  if (!incoming || incoming.length === 0) return current;

  const next = [...current];

  for (const message of incoming) {
    const byId = message.id == null
      ? -1
      : next.findIndex((m) => m.id != null && m.id === message.id);
    if (byId >= 0) {
      next[byId] = { ...message, status: SENT };
      continue;
    }

    const byClientId = next.findIndex((m) => (
      m.id == null
      && m.clientMessageId === message.clientMessageId
      && m.senderRole === mySide
      && message.senderRole === mySide
    ));
    if (byClientId >= 0) {
      next[byClientId] = { ...message, status: SENT };
      continue;
    }

    next.push({ ...message, status: SENT });
  }

  return sortByServerOrder(next);
}

/**
 * Sắp theo thứ tự của máy chủ, với những tin chưa có id ở cuối.
 *
 * Thứ tự nằm ở `id`, không ở `createdAt`: mốc thời gian có thể trùng nhau tới
 * từng micro giây, và mốc của một tin đang chờ là mốc do máy này đặt.
 */
function sortByServerOrder(list) {
  return [...list].sort((a, b) => {
    if (a.id == null && b.id == null) return 0;
    if (a.id == null) return 1;
    if (b.id == null) return -1;
    return a.id - b.id;
  });
}

/**
 * Áp một lời báo nhận lên bong bóng tương ứng.
 *
 * `DUPLICATE` được xử lý y hệt `ACCEPTED`, và đó là chủ ý: cả hai đều nghĩa là
 * câu ấy đã nằm trong cơ sở dữ liệu đúng một lần. Vẽ nó khác đi sẽ là báo cho
 * người dùng một chuyện chỉ có nghĩa với lập trình viên.
 */
function applyAck(current, ack) {
  if (!ack?.clientMessageId) return current;
  return sortByServerOrder(current.map((m) => (
    m.clientMessageId === ack.clientMessageId
      ? {
        ...m,
        id: ack.messageId ?? m.id,
        createdAt: ack.createdAt ?? m.createdAt,
        status: SENT,
        error: null,
      }
      : m
  )));
}

function markFailed(current, clientMessageId, message) {
  return current.map((m) => (
    m.clientMessageId === clientMessageId && m.id == null
      ? { ...m, status: FAILED, error: message ?? null }
      : m
  ));
}

/**
 * Một phía vừa đọc tới đâu.
 *
 * Cùng một khung tin đi tới cả hai bên, và mỗi bên đọc ra phần của mình bằng
 * cách so `reader` với vai trò của chính nó — xem `SupportRealtime` ở máy chủ.
 */
function applyRead(conversation, read, mySide) {
  if (!conversation) return conversation;
  if (read.reader === mySide) {
    return {
      ...conversation,
      lastReadMessageId: read.lastReadMessageId,
      unread: read.readerUnread ?? conversation.unread,
    };
  }
  return { ...conversation, peerLastReadMessageId: read.lastReadMessageId };
}
