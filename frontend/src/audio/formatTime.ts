/**
 * Giây thành đồng hồ: `m:ss`, hoặc `h:mm:ss` khi đã qua một giờ.
 *
 * Ở đây chứ không trong một khối giao diện nào, vì trang đọc giờ có hai thanh
 * tua — giọng đọc và nhạc nền — và hai thanh nằm cạnh nhau mà viết giờ theo hai
 * kiểu là thứ mắt nhận ra ngay dù không chỉ được ra là sai ở đâu.
 *
 * Số không hợp lệ trả về `--:--`: chưa biết độ dài thì nói là chưa biết, chứ
 * không nói là 0:00 — người đọc sẽ tưởng bản nhạc rỗng.
 */
export function formatTime(value: number): string {
  if (!Number.isFinite(value) || value < 0) return "--:--";

  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (n: number) => String(n).padStart(2, "0");

  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}

export default formatTime;
