import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isSessionTerminated } from "../api/client";
import { supportApi } from "../api/endpoints";
import { useAuth } from "./auth-context";
import {
  CONNECTED,
  CONNECTING,
  DISCONNECTED,
  ERROR,
  RECONNECTING,
  SupportSocketContext,
} from "./support-socket-context";

/**
 * Giữ đúng một kết nối WebSocket hỗ trợ cho cả tab, và phát khung tin cho ai
 * đăng ký nghe.
 *
 * <h3>Vòng đời treo vào phiên đăng nhập</h3>
 * Toàn bộ trạng thái treo vào `user.id`. Đăng xuất thì hiệu ứng dọn dẹp đóng
 * kết nối; người khác đăng nhập vào cùng trình duyệt thì khóa đổi nên mọi thứ
 * được dựng lại từ đầu. Không có đường nào để khung tin của người trước tới tay
 * người sau.
 *
 * <h3>Nó không giữ dữ liệu nghiệp vụ nào</h3>
 * Không tin nhắn, không số chưa đọc, không trạng thái luồng. Đó là chủ ý: ba
 * bên nghe nó cần ba lát cắt khác nhau của cùng dòng khung tin, và một kho
 * chung ở đây sẽ phải là hợp của cả ba — tức là một chỗ nữa để lệch với máy
 * chủ. Nguồn sự thật vẫn là cơ sở dữ liệu, và mỗi bên tự đồng bộ phần của mình
 * qua REST.
 *
 * <p>Thứ duy nhất nó giữ là trạng thái đường truyền và mấy con số trần mà khung
 * tin mở màn mang xuống.
 */
export default function SupportSocketProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  const [connection, setConnection] = useState(DISCONNECTED);
  const [limits, setLimits] = useState({ maxMessageLength: DEFAULT_MAX_LENGTH });

  const socketRef = useRef(null);

  /*
   * Danh sách người nghe, giữ trong một ref chứ không trong state.
   *
   * Đăng ký và hủy đăng ký xảy ra trong hiệu ứng của các thành phần con, và
   * mỗi lần ấy mà kéo theo một lần vẽ lại của nhà cung cấp thì cả cây bên dưới
   * vẽ lại theo — với một thứ nằm ở góc mọi trang thì đó là cái giá không đáng.
   */
  const listenersRef = useRef(new Set());

  /**
   * Nghe mọi khung tin đi xuống. Trả về hàm hủy đăng ký.
   *
   * <p>Không lọc gì ở đây: bên nghe biết rõ nó quan tâm khung tin nào hơn là
   * nhà cung cấp có thể đoán. Khung tin của một luồng khác vẫn tới trang quản
   * trị, vì danh sách hộp thư *cần* nó.
   */
  const subscribe = useCallback((listener) => {
    listenersRef.current.add(listener);
    return () => listenersRef.current.delete(listener);
  }, []);

  /**
   * Gửi một khung tin lên, nếu đường đang thông.
   *
   * @returns false khi chưa nối được — bên gọi lùi về REST. Đó là đường lui
   *          thật, không phải một nhánh lỗi: hai đường gọi vào cùng một chỗ ở
   *          máy chủ và chống trùng nằm ở ràng buộc cơ sở dữ liệu, nên một tin
   *          gửi hụt qua WebSocket rồi thử lại qua REST vẫn chỉ thành một tin.
   */
  const send = useCallback((frame) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    socket.send(JSON.stringify(frame));
    return true;
  }, []);

  /* ---------------------------------------------------------------- */
  /* Kết nối                                                           */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (!userId) {
      setConnection(DISCONNECTED);
      return undefined;
    }
    if (typeof window === "undefined" || typeof window.WebSocket === "undefined") {
      // Vài webview nhúng không có WebSocket. Ở đó hộp thư vẫn đầy đủ và vẫn
      // gửi được — bằng REST — chỉ là không thấy tin của bên kia ngay lập tức.
      return undefined;
    }

    let cancelled = false;
    let retryTimer = null;
    let attempt = 0;

    const connect = async () => {
      if (cancelled || isSessionTerminated()) return;

      setConnection(attempt === 0 ? CONNECTING : RECONNECTING);

      let url;
      try {
        url = await supportApi.socketUrl();
      } catch {
        scheduleRetry();
        return;
      }
      if (cancelled) return;

      let socket;
      try {
        socket = new WebSocket(url);
      } catch {
        scheduleRetry();
        return;
      }
      socketRef.current = socket;

      socket.onopen = () => {
        if (cancelled) return;
        attempt = 0;
        setConnection(CONNECTED);
      };

      socket.onmessage = (event) => {
        if (cancelled) return;
        const frame = parse(event.data);
        if (!frame) return;

        if (frame.type === "connection:ready" && frame.payload?.maxMessageLength) {
          setLimits({ maxMessageLength: frame.payload.maxMessageLength });
        }

        // Mỗi người nghe được bọc riêng: một bên ném lỗi không được kéo theo
        // những bên còn lại, và càng không được làm chết cả kết nối.
        for (const listener of listenersRef.current) {
          try {
            listener(frame);
          } catch (ex) {
            console.error("Bộ nghe khung tin hỗ trợ ném lỗi", ex);
          }
        }
      };

      socket.onerror = () => {
        // Không làm gì ở đây: `onclose` luôn theo sau, và xử lý ở hai chỗ sẽ
        // đặt hai lịch nối lại cho cùng một lần đứt.
      };

      socket.onclose = (event) => {
        socketRef.current = null;
        if (cancelled) return;

        if (event.code === CLOSE_ACCESS_REVOKED) {
          // Tài khoản bị khóa hoặc quyền vừa đổi. Nối lại chỉ dẫn tới cùng một
          // lời từ chối, mãi mãi. Việc đăng xuất do `SessionGuard` lo, qua
          // chính lời từ chối ấy ở lần gọi REST kế tiếp.
          setConnection(ERROR);
          return;
        }

        setConnection(RECONNECTING);
        scheduleRetry(event.code === CLOSE_TOO_MANY ? BUSY_RETRY_MS : null);
      };
    };

    /**
     * Nối lại với quãng nghỉ tăng dần và một chút ngẫu nhiên.
     *
     * Ngẫu nhiên không phải để cho đẹp: máy chủ khởi động lại thì *mọi* trình
     * duyệt đang mở cùng mất kết nối trong một giây, và một quãng nghỉ cố định
     * sẽ khiến tất cả cùng quay lại đúng một lúc — một cơn bão nối lại đánh vào
     * đúng lúc máy chủ còn đang dựng.
     */
    const scheduleRetry = (floorMs) => {
      if (cancelled || retryTimer) return;
      const backoff = Math.min(RETRY_BASE_MS * 2 ** attempt, RETRY_MAX_MS);
      const delay = Math.max(floorMs ?? 0, backoff) * (0.7 + Math.random() * 0.6);
      attempt += 1;
      retryTimer = window.setTimeout(() => {
        retryTimer = null;
        connect();
      }, delay);
    };

    connect();

    return () => {
      cancelled = true;
      setConnection(DISCONNECTED);
      if (retryTimer) window.clearTimeout(retryTimer);
      const socket = socketRef.current;
      socketRef.current = null;
      socket?.close();
    };
  }, [userId]);

  const value = useMemo(
    () => ({ connection, live: connection === CONNECTED, limits, subscribe, send }),
    [connection, limits, subscribe, send],
  );

  return (
    <SupportSocketContext.Provider value={value}>{children}</SupportSocketContext.Provider>
  );
}

/* ------------------------------------------------------------------ */
/* Hằng số                                                             */
/* ------------------------------------------------------------------ */

/** Trần dự phòng, dùng cho tới khi khung tin mở màn nói ra con số thật. */
const DEFAULT_MAX_LENGTH = 2000;

const RETRY_BASE_MS = 1_000;
const RETRY_MAX_MS = 30_000;

/** Chạm trần kết nối của máy chủ: nghỉ hẳn một quãng thay vì quay lại ngay. */
const BUSY_RETRY_MS = 15_000;

/* Cùng những con số với `SupportCloseCodes` ở máy chủ. */
const CLOSE_ACCESS_REVOKED = 4002;
const CLOSE_TOO_MANY = 4003;

function parse(data) {
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}
