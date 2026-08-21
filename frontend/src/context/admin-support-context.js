import { createContext, useContext } from "react";

/**
 * Trạng thái hộp thư hỗ trợ nhìn từ khu quản trị, dùng chung cho cả bảng điều
 * khiển.
 *
 * <h3>Vì sao là một context chứ không phải state của trang Hỗ trợ</h3>
 * Vì con số này phải hiện ra ở chỗ mà trang Hỗ trợ <b>không</b> tồn tại: cái
 * huy hiệu đỏ trên tab "Hỗ trợ" của thanh bên phải đúng khi quản trị viên đang
 * ở màn hình Thành viên, Bình luận, hay Tổng quan — tức là khi
 * `AdminSupportPage` chưa được dựng. Để con số trong trang ấy thì nó chỉ tồn
 * tại sau khi người ta đã bấm vào chỗ cần được nhắc.
 *
 * <p>Đặt ở `AdminLayout` là đặt đúng ranh giới: mọi màn hình quản trị nằm dưới
 * nó, và không màn hình nào của người đọc nằm dưới nó.
 *
 * <h3>Nó không giữ danh sách hội thoại nào</h3>
 * Chỉ mấy con số của `GET /api/admin/support/summary`. Danh sách hộp thư vẫn
 * thuộc về `AdminSupportPage`, vì chỉ nó cần — và một bản sao thứ hai của danh
 * sách ấy ở đây sẽ là một chỗ nữa để lệch với máy chủ.
 *
 * <h3>Máy chủ đếm, trình duyệt không cộng trừ</h3>
 * Không có phép `+1` khi tin tới hay `-1` khi đọc xong ở bất cứ đâu trong nhánh
 * này. Mỗi khung tin chỉ là một tín hiệu "đi hỏi lại đi"; con số luôn đến từ
 * `awaitingReply` của máy chủ. Đó là điều kiện để huy hiệu đúng khi có nhiều
 * quản trị viên: mốc "đã đọc" của phía hỗ trợ là một mốc <i>dùng chung</i>,
 * nên người này đọc thì huy hiệu của người kia cũng phải tụt — thứ mà một bộ
 * đếm cục bộ không bao giờ biết.
 */
export const AdminSupportContext = createContext(null);

/**
 * Số liệu hộp thư hỗ trợ, hoặc `null` khi không có nhà cung cấp nào ở trên.
 *
 * <p>Trả `null` chứ không ném: cái huy hiệu là thứ trang trí, và một màn hình
 * quản trị được dựng trong bài kiểm thử — hay một ngày nào đó ở ngoài
 * `AdminLayout` — không đáng vỡ vì thiếu nó.
 */
export function useAdminSupport() {
  return useContext(AdminSupportContext);
}
