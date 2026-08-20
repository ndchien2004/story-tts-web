/**
 * Shared formatting for money and dates.
 *
 * Prices appear on the pricing page, the payment result and two admin tables;
 * a Vietnamese reader expects `49.000₫`, not `49,000 VND`, and expects it to
 * look the same in all four places.
 */

/** `49000` → `49.000₫`. */
export function formatVnd(amount) {
  return `${Number(amount ?? 0).toLocaleString("vi-VN")}₫`;
}

/**
 * `1200` → `1.200 Xu`.
 *
 * Dấu chấm phân nhóm giống tiền, vì Xu cũng được đọc theo nhóm nghìn. Đơn vị
 * viết thành chữ chứ không thành ký hiệu: Xu là đơn vị riêng của trang này,
 * không có ký hiệu nào người đọc sẵn nhận ra.
 */
export function formatCoins(amount) {
  return `${Number(amount ?? 0).toLocaleString("vi-VN")} Xu`;
}

/** Có dấu, cho sổ cái: `-50` → `−50 Xu`, `+120` → `+120 Xu`. */
export function formatCoinDelta(amount) {
  const value = Number(amount ?? 0);
  const sign = value > 0 ? "+" : value < 0 ? "−" : "";
  return `${sign}${Math.abs(value).toLocaleString("vi-VN")} Xu`;
}

/** Day precision — what a subscription expiry is actually measured in. */
export function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

/** Day and minute, for anything that records when something happened. */
export function formatDateTime(value) {
  if (!value) return "—";
  return new Date(value).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * "2 phút trước", "5 giờ trước", "12/08/2026".
 *
 * Thông báo được đọc theo thứ tự thời gian, và câu hỏi duy nhất người ta đặt ra
 * với cái mốc ấy là "mới hay cũ". Một dấu thời gian đầy đủ trả lời câu ấy chậm
 * hơn hẳn: mắt phải trừ hai con số trước khi biết được điều mình muốn biết.
 *
 * Chuyển sang ngày tháng đầy đủ từ mốc một tuần. Qua đó thì "9 ngày trước"
 * không còn nói được gì mà một ngày cụ thể chưa nói, và nó bắt đầu sai lệch:
 * không ai nghĩ theo đơn vị "27 ngày trước".
 */
export function relativeTime(value) {
  if (!value) return "";

  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return "";

  const seconds = Math.round((Date.now() - then) / 1000);

  // Đồng hồ của máy người dùng có thể chạy trước máy chủ vài giây. "Vừa xong"
  // là câu đúng cho cả hai phía của mốc 0; "trong -3 giây nữa" thì không.
  if (seconds < 60) return "Vừa xong";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} phút trước`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} giờ trước`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)} ngày trước`;

  return formatDate(value);
}
