/**
 * Đổi một thông báo thành những gì màn hình cần: một chỗ để đi tới, và một hình
 * để nhận ra nó.
 *
 * <h3>Vì sao việc này nằm ở trình duyệt</h3>
 * Máy chủ lưu <i>ý định</i> (`VIEW_REFUND_HISTORY`) chứ không lưu đường dẫn.
 * Đường dẫn là quyết định của giao diện và sẽ đổi; đóng băng nó vào những hàng
 * không bao giờ được sửa lại nghĩa là một lần đổi route làm mọi thông báo cũ
 * trỏ vào hư không. Bảng tra ở dưới là chỗ duy nhất biết cả hai bên, và nó nằm
 * đúng phía có quyền đổi route.
 *
 * <h3>Và vì sao đó cũng là hàng rào an toàn</h3>
 * Không có chuỗi nào từ cơ sở dữ liệu đi thẳng vào một `to` hay một `href`. Mọi
 * đường dẫn ở đây được ghép từ hằng trong tệp này cộng một con số đã qua
 * `Number()`. Một quản trị viên gõ `javascript:...` vào nội dung thông báo cũng
 * không có đường nào để chuỗi ấy trở thành đích đến của một liên kết.
 *
 * <h3>Đích có thể đã chết, và điều đó là bình thường</h3>
 * Phần lớn thông báo ở đây nói về những thứ *vừa bị xóa*. Máy chủ vì thế coi cặp
 * `relatedEntity` là một gợi ý chứ không phải một lời hứa, và trang đích tự lo
 * phần 404 — trang truyện và trang chương đều đã có màn hình cho việc đó. Chỗ
 * nào chắc chắn không còn đích (cả truyện bị gỡ) thì máy chủ không gắn thực thể
 * nào, nên ở đây cũng không mọc ra nút thứ hai.
 */

/** Đích của từng ý định. Hàm, vì vài đích cần một id. */
const ACTION_ROUTES = {
  // Sổ Xu nằm trong một tab của trang tài khoản. Nếu ngày mai nó thành trang
  // riêng thì đúng một dòng ở đây đổi, và mọi thông báo cũ vẫn trỏ đúng chỗ.
  VIEW_REFUND_HISTORY: () => "/tai-khoan?tab=wallet",
  VIEW_WALLET: () => "/tai-khoan?tab=wallet",
  VIEW_ORDERS: () => "/tai-khoan?tab=orders",
  VIEW_VIP: () => "/nang-cap",
  VIEW_STORY: (id) => (id ? `/truyen/${id}` : null),
  VIEW_CHAPTER: (id) => (id ? `/chuong/${id}` : null),
};

/** Nhãn của nút chính, theo ý định. */
const ACTION_LABELS = {
  VIEW_REFUND_HISTORY: "Xem lịch sử Xu",
  VIEW_WALLET: "Xem ví Xu",
  VIEW_ORDERS: "Xem đơn đã mua",
  VIEW_VIP: "Xem quyền lợi VIP",
  VIEW_STORY: "Xem truyện",
  VIEW_CHAPTER: "Mở chương",
};

/** Đích của thực thể liên quan — nguồn của nút phụ. */
const ENTITY_ROUTES = {
  STORY: (id) => (id ? `/truyen/${id}` : null),
  CHAPTER: (id) => (id ? `/chuong/${id}` : null),
  PAYMENT_ORDER: () => "/tai-khoan?tab=orders",
};

const ENTITY_LABELS = {
  STORY: "Về danh sách chương",
  CHAPTER: "Mở chương",
  PAYMENT_ORDER: "Xem đơn đã mua",
};

/**
 * Ký hiệu đứng đầu mỗi dòng.
 *
 * Emoji chứ không phải một bộ SVG mới: mỗi loại thông báo cần đúng một hình
 * nhỏ, và dựng thêm bảy biểu tượng vector cho việc ấy là trả giá cho thứ mà một
 * ký tự đã làm xong. Chúng cũng đọc được bởi trình đọc màn hình — nhưng ở đây
 * bị ẩn khỏi nó, vì tiêu đề ngay bên cạnh đã nói đủ.
 */
const TYPE_ICONS = {
  VIP_GRANTED: "🎉",
  CHAPTER_DELETED: "📕",
  CHAPTER_UPDATED: "📖",
  PAYMENT: "💳",
  REFUND: "↩️",
  SYSTEM: "⚙️",
  ANNOUNCEMENT: "📣",
};

/** Loại chưa có trong bảng vẫn phải hiện ra được — xem `iconFor`. */
const FALLBACK_ICON = "🔔";

/**
 * Nút chính của một thông báo, hoặc null nếu nó không dẫn đi đâu.
 *
 * @param notification một phần tử của hộp thư, đúng như máy chủ trả về
 * @returns {{to: string, label: string} | null}
 */
export function primaryAction(notification) {
  const resolve = ACTION_ROUTES[notification?.actionType];
  if (!resolve) return null;

  const to = resolve(numericId(notification.relatedEntityId));
  if (!to) return null;

  return { to, label: ACTION_LABELS[notification.actionType] ?? "Xem chi tiết" };
}

/**
 * Nút phụ, dựng từ thực thể liên quan.
 *
 * Bỏ qua khi nó trùng đích với nút chính: hai nút cạnh nhau cùng dẫn tới một
 * chỗ là một câu hỏi thừa đặt ra cho người đọc.
 */
export function secondaryAction(notification) {
  const resolve = ENTITY_ROUTES[notification?.relatedEntityType];
  if (!resolve) return null;

  const to = resolve(numericId(notification.relatedEntityId));
  if (!to) return null;

  const primary = primaryAction(notification);
  if (primary && primary.to === to) return null;

  return { to, label: ENTITY_LABELS[notification.relatedEntityType] ?? "Xem chi tiết" };
}

/**
 * Ký hiệu của một loại.
 *
 * Loại lạ — một phiên bản máy chủ mới hơn trình duyệt đang mở — rơi về cái
 * chuông chung thay vì hiện ra một ô trống. Đây chính là chỗ khiến việc thêm
 * loại mới ở backend không làm hỏng những tab đang mở.
 */
export function iconFor(type) {
  return TYPE_ICONS[type] ?? FALLBACK_ICON;
}

/** Chỉ nhận số; mọi thứ khác thành null. Xem ghi chú về an toàn ở đầu tệp. */
function numericId(value) {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : null;
}
