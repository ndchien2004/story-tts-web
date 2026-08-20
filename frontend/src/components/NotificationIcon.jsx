/**
 * Hình nhỏ đứng đầu mỗi dòng thông báo.
 *
 * <h3>Vì sao là nét vẽ chứ không phải emoji</h3>
 * Bản đầu dùng emoji — 🎉, 📕, 💳 — và chúng sai ở đây vì hai lẽ. Thứ nhất là
 * màu: cả trang này được dựng trên một quy tắc duy nhất, "chỉ lỗi mới có màu",
 * nên bảy ký tự nhiều màu trong hộp thư là bảy chỗ hút mắt trước cả tiêu đề của
 * chính dòng chúng đứng. Thứ hai là chúng không phải của mình: emoji do hệ điều
 * hành vẽ, nên cùng một thông báo có ba dáng khác nhau trên Windows, iOS và
 * Android — không nét nào khớp với bộ biểu tượng còn lại của trang.
 *
 * <p>Nét vẽ thì lấy màu từ `currentColor`, nên nó nhạt đi cùng phần chữ phụ và
 * đậm lên khi cả dòng đậm lên. Cùng ngữ pháp hình với biểu tượng ở menu tài
 * khoản và ở thanh bên quản trị: viewBox 24, nét 2, đầu nét bo tròn.
 *
 * <h3>Loại lạ vẫn vẽ ra được</h3>
 * Một phiên bản máy chủ mới hơn tab đang mở có thể gửi về một loại chưa có ở
 * đây. Nó rơi về cái chuông chung thay vì để lại một ô trống — cùng cách xử lý
 * với bảng tra đường dẫn ở `notificationRoutes.js`.
 */

const iconProps = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

/** Vương miện — cùng hình với dấu hiệu VIP ở menu tài khoản. */
const CrownIcon = () => (
  <svg {...iconProps}>
    <path d="M4 17.5h16M4.5 7.2l3.6 3.1L12 5.5l3.9 4.8 3.6-3.1-1.4 8.3H5.9z" />
  </svg>
);

/** Quyển sách bị gạch chéo: nội dung không còn ở đó nữa. */
const BookRemovedIcon = () => (
  <svg {...iconProps}>
    <path d="M5 5.2A1.7 1.7 0 0 1 6.7 3.5H19v14H6.7A1.7 1.7 0 0 0 5 19.2z" />
    <path d="M6.2 4.6l11.6 13.8" />
  </svg>
);

/** Mũi tên xoay vòng: cùng nội dung ấy, ở một bản mới. */
const RefreshIcon = () => (
  <svg {...iconProps}>
    <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" />
    <path d="M19.5 4.6V9h-4.4" />
  </svg>
);

/** Thẻ thanh toán. */
const CardIcon = () => (
  <svg {...iconProps}>
    <rect x="3" y="5.5" width="18" height="13" rx="2.2" />
    <path d="M3 10h18M6.5 14.5h3" />
  </svg>
);

/** Đồng Xu — cùng hình với mục ví ở menu tài khoản. */
const CoinIcon = () => (
  <svg {...iconProps}>
    <circle cx="12" cy="12" r="8" />
    <path d="M12 8.2v7.6M9.9 10.1h3.2a1.9 1.9 0 0 1 0 3.8H9.9" />
  </svg>
);

/** Cần gạt: máy chủ đang nói về chính nó. */
const SlidersIcon = () => (
  <svg {...iconProps}>
    <path d="M4 7.5h9M18.5 7.5H20M4 16.5h1.5M11 16.5h9" />
    <circle cx="15.5" cy="7.5" r="2.3" />
    <circle cx="8" cy="16.5" r="2.3" />
  </svg>
);

/** Loa phóng thanh: một câu nói với nhiều người cùng lúc. */
const MegaphoneIcon = () => (
  <svg {...iconProps}>
    <path d="M4 10v4a1.6 1.6 0 0 0 1.6 1.6h2.1l6.3 3.9V4.5L7.7 8.4H5.6A1.6 1.6 0 0 0 4 10z" />
    <path d="M17.6 9.4a3.8 3.8 0 0 1 0 5.2" />
  </svg>
);

/** Cái chuông — dùng cho loại chưa có trong bảng. Xem ghi chú ở đầu tệp. */
const BellIcon = () => (
  <svg {...iconProps}>
    <path d="M17.8 11.2a5.8 5.8 0 1 0-11.6 0c0 4.1-1.7 5.4-1.7 5.4h15s-1.7-1.3-1.7-5.4" />
    <path d="M9.9 16.9a2 2 0 0 0 3.9 0" />
  </svg>
);

const BY_TYPE = {
  VIP_GRANTED: CrownIcon,
  CHAPTER_DELETED: BookRemovedIcon,
  CHAPTER_UPDATED: RefreshIcon,
  PAYMENT: CardIcon,
  REFUND: CoinIcon,
  SYSTEM: SlidersIcon,
  ANNOUNCEMENT: MegaphoneIcon,
};

/**
 * @param type một giá trị của `NotificationType` phía máy chủ
 */
export default function NotificationIcon({ type }) {
  const Glyph = BY_TYPE[type] ?? BellIcon;
  return <Glyph />;
}
