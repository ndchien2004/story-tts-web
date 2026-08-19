-- =====================================================================
-- V10 — Đếm số lần đăng nhập sai của từng tài khoản.
--
-- <h3>Vì sao cần thêm hàng rào này khi đã có giới hạn theo IP</h3>
-- RateLimitFilter đếm theo địa chỉ mạng, và đó là thứ duy nhất biết được
-- khi chưa ai đăng nhập. Nhưng nó có một chỗ hở nói thẳng ra được: kẻ có
-- sẵn một dải địa chỉ chỉ cần đổi nguồn sau mỗi mười lần thử, và hàng rào
-- ấy không bao giờ đóng lại.
--
-- Hai cột dưới đây đếm theo thứ mà kẻ tấn công không đổi được: chính cái
-- tài khoản họ đang nhắm tới. Mười lần sai liên tiếp là nghỉ mười lăm
-- phút, bất kể chúng đến từ bao nhiêu địa chỉ khác nhau.
--
-- <h3>Cái giá, nói trước</h3>
-- Người ngoài khóa được tài khoản của người khác trong mười lăm phút bằng
-- cách cố tình gõ sai. Đó là đánh đổi cố hữu của mọi cơ chế khóa theo tài
-- khoản, và nó được chấp nhận vì hai lẽ: quãng khóa ngắn và tự mở, còn
-- người bị khóa vẫn đặt lại được mật khẩu qua email ngay lập tức — đường
-- ấy không đi qua hai cột này.
--
-- Cách duy nhất tránh hẳn đánh đổi này là đếm theo cặp (tài khoản, IP),
-- nhưng khi ấy hàng rào quay về đúng chỗ hở mà nó sinh ra để bịt.
-- =====================================================================

-- Đếm số lần sai LIÊN TIẾP, không phải tổng số lần sai: một lần đăng nhập
-- thành công đưa nó về 0. Người hay quên mật khẩu không tích dần tới mức
-- khóa qua nhiều tháng.
alter table users
    add column failed_login_attempts integer not null default 0;

-- Null nghĩa là không bị khóa. Mốc trong tương lai nghĩa là đang nghỉ.
--
-- Lưu mốc hết hạn chứ không lưu mốc bắt đầu: câu hỏi ở mỗi lần đăng nhập
-- là "còn bị khóa không", và một phép so với thời điểm hiện tại trả lời
-- thẳng được, không cần biết quãng nghỉ dài bao nhiêu. Đổi cấu hình quãng
-- nghỉ cũng không làm sai lệch những bản ghi đã có.
alter table users
    add column login_locked_until datetime(6);
