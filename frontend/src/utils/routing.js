/**
 * Nơi một người vừa đăng nhập được đưa tới.
 *
 * Quản trị viên vào thẳng bảng quản trị. Không phải để chặn họ khỏi trang đọc —
 * gõ địa chỉ nào vẫn mở được địa chỉ ấy — mà vì đó là chỗ họ mở trang để tới.
 * Trước đây họ hạ cánh xuống trang chủ và phải tự tìm đường qua menu tài khoản,
 * nghĩa là mỗi phiên làm việc bắt đầu bằng hai cú bấm không mang lại gì.
 *
 * Chỗ họ *đang định* tới thì được tôn trọng: bị bật khỏi một trang quản trị vì
 * hết phiên, đăng nhập lại là quay đúng về trang ấy chứ không về trang tổng quan.
 *
 * Một hàm dùng chung chứ không phải một dòng chép ở ba nơi: đăng nhập bằng mật
 * khẩu, đăng nhập bằng Google, và việc mở lại trang đăng nhập khi đã có phiên —
 * cả ba đều phải trả lời cùng một câu hỏi.
 *
 * @param user      người vừa đăng nhập, đúng như máy chủ trả về
 * @param requested trang họ định tới trước khi bị hỏi đăng nhập
 */
export function landingPathFor(user, requested = "/") {
  if (user?.role === "ADMIN" && !requested.startsWith("/admin")) {
    return "/admin";
  }
  return requested;
}

export default landingPathFor;
