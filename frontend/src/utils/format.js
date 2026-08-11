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
