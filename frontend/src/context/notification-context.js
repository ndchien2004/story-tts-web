import { createContext, useContext } from "react";

/**
 * Notification context object and its hook.
 *
 * Kept apart from the provider component so the module exports only plain
 * values, which keeps React Fast Refresh working for the provider file — the
 * same split `auth-context` uses.
 */
export const NotificationContext = createContext(null);

/**
 * Hộp thư của người đang đăng nhập.
 *
 * Trả về hình dạng rỗng — chứ không ném — khi không có provider ở trên, để một
 * màn hình nào đó dựng ngoài cây provider (bài kiểm, một trang lỗi) không hỏng
 * vì một cái chuông. Nó không xảy ra ở đường chạy thật: `App` bọc mọi route.
 */
export function useNotifications() {
  return useContext(NotificationContext) ?? EMPTY;
}

const EMPTY = {
  unread: 0,
  latest: [],
  loading: false,
  error: null,
  live: false,
  markRead: async () => {},
  markAllRead: async () => {},
  refresh: async () => {},
};
