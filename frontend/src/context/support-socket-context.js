import { createContext, useContext } from "react";

/**
 * Một kết nối WebSocket hỗ trợ, dùng chung cho cả tab.
 *
 * <h3>Vì sao phải dùng chung, và vì sao đây là một sửa lỗi chứ không phải một
 * tối ưu</h3>
 * Bản đầu để kết nối nằm bên trong `useSupportThread`, tức là nó chỉ tồn tại
 * khi có một khung hội thoại đang mở. Hậu quả cụ thể, và là lỗi người dùng gặp
 * phải: quản trị viên ngồi nhìn danh sách hộp thư mà **chưa mở hội thoại nào**
 * thì không có kết nối nào cả — tin mới của người đọc không có đường nào để
 * tới, và danh sách chỉ đổi sau nhịp làm mới 45 giây hoặc sau khi bấm F5.
 *
 * Kể cả khi đã mở một hội thoại, tin của *hội thoại khác* vẫn không đẩy được
 * dòng của nó lên đầu danh sách: khung chat lọc bỏ mọi khung tin không thuộc
 * luồng nó đang vẽ.
 *
 * <p>Tách kết nối ra khỏi khung chat sửa cả hai, và mở đường cho một thứ thứ
 * ba: cái bong bóng ở góc màn hình phải biết số chưa đọc *khi hộp thoại đang
 * đóng* — tức là khi không có khung chat nào tồn tại.
 *
 * <h3>Một kết nối, nhiều người nghe</h3>
 * <pre>
 *   SupportWidget      → huy hiệu chưa đọc, và khung chat khi mở ra
 *   AdminSupportPage   → danh sách hộp thư, mọi luồng
 *   useSupportThread   → luồng đang mở
 * </pre>
 *
 * Ba bên, một socket. Mỗi bên tự mở một kết nối sẽ đụng trần
 * `SUPPORT_MAX_SESSIONS_PER_USER` chỉ với vài tab, và ba lần nhân đôi mọi khung
 * tin máy chủ phải gửi đi.
 */
export const SupportSocketContext = createContext(null);

/**
 * Kết nối dùng chung, hoặc `null` khi không có nhà cung cấp nào ở trên.
 *
 * <p>Trả `null` chứ không ném: cái bong bóng và trang quản trị đều nằm sau lớp
 * đăng nhập, nhưng `useSupportThread` cũng được dùng trong những cây thành phần
 * mà nhà cung cấp chưa chắc có mặt — và ở đó nó phải lùi về REST chứ không phải
 * làm hỏng cả trang.
 */
export function useSupportSocket() {
  return useContext(SupportSocketContext);
}

/* ------------------------------------------------------------------ */
/* Trạng thái đường truyền                                             */
/* ------------------------------------------------------------------ */

/*
 * Ở đây chứ không ở tệp nhà cung cấp, vì cả hai bên đều cần chúng và tệp này
 * không có JSX: nhập nó không kéo theo cả một thành phần React chỉ để lấy năm
 * chuỗi hằng.
 */
export const DISCONNECTED = "DISCONNECTED";
export const CONNECTING = "CONNECTING";
export const CONNECTED = "CONNECTED";
export const RECONNECTING = "RECONNECTING";
export const ERROR = "ERROR";

/**
 * Máy chủ này không có đường thời gian thực — và sẽ không có nó trong phiên
 * làm việc này.
 *
 * <h3>Vì sao đây là một trạng thái riêng chứ không phải một lần `ERROR` nữa</h3>
 * Vì nó đòi một hành vi khác hẳn: <b>ngừng thử lại</b>. `RECONNECTING` nghĩa là
 * "đường đứt, sẽ thông lại"; cái này nghĩa là "không có đường ấy ở đây", và thử
 * lại chỉ dẫn tới đúng lời từ chối cũ, mãi mãi.
 *
 * <p>Nó có thật, không phải phòng xa. Bản backend triển khai trên Render từng
 * cũ hơn bản frontend trên Vercel, nên `POST /api/support/ws-ticket` trả 404 ở
 * đó. Trình duyệt xin vé, hỏng, hẹn giờ, xin lại — mỗi ba mươi giây một lần,
 * trên mọi tab đang mở, và không một chữ nào hiện ra ở đâu để nói vì sao khung
 * chat im lặng. Một vòng lặp không bao giờ thành công mà cũng không bao giờ
 * kêu lên là thứ khó tìm nhất trong tất cả.
 */
export const UNAVAILABLE = "UNAVAILABLE";
