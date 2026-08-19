-- =====================================================================
-- V12 — Xóa được một chương đã có người đọc.
--
-- <h3>Lỗi được sửa</h3>
-- Admin bấm "Xóa chương" trên một chương đã có người đọc thì nhận về
-- "Đã có lỗi xảy ra ở máy chủ". Không phải lỗi ngẫu nhiên: khóa ngoại từ
-- `reading_progress` tới `chapters` không khai báo hành vi xóa nào, nên
-- MySQL mặc định là RESTRICT — còn một dòng tiến độ đọc là chương không
-- xóa được.
--
-- Cùng một hình dạng ấy còn nằm ở hai chỗ nữa, và cả hai đều chặn việc
-- xóa cả một truyện:
--
--   favorites.story_id        → truyện có người bấm yêu thích: không xóa được
--   ratings_comments.story_id → truyện có một bình luận: không xóa được
--
-- Ba lỗi này không lộ ra trên dữ liệu mẫu vì dữ liệu mẫu không có ai đọc
-- dở, và chúng chỉ xuất hiện đúng vào lúc trang web đã có người dùng thật.
--
-- <h3>Vì sao sửa ở tầng khóa ngoại chứ không ở tầng service</h3>
-- Cách kia — xóa tay các bảng phụ trước rồi mới xóa chương — cũng chạy,
-- nhưng nó đặt một bất biến vào trí nhớ của người viết đường xóa tiếp
-- theo. Đã có sẵn hai đường xóa (xóa chương, xóa truyện) và đường thứ hai
-- đi vòng qua đường thứ nhất bằng cascade của JPA, nên chỉ cần một chỗ
-- quên là lỗi quay lại y như cũ. Quy tắc đặt trong lược đồ thì không quên
-- được.
--
-- `chapter_entitlements` đã có ON DELETE CASCADE từ V6 — lần này chỉ là
-- đưa ba khóa ngoại còn lại về cùng một nếp.
--
-- <h3>Vì sao là CASCADE chứ không phải SET NULL</h3>
-- Cả ba dòng đều mất nghĩa khi thứ chúng trỏ tới không còn: một tiến độ
-- đọc của chương không tồn tại, một lượt yêu thích truyện không tồn tại,
-- một bình luận về truyện không tồn tại. Giữ chúng lại với cột null chỉ
-- tạo ra những hàng không ai truy vấn được và không ai dọn.
--
-- Cố ý KHÔNG đụng tới hai bảng lịch sử:
--
--   view_events  vốn không có khóa ngoại nào. Lượt đọc đã xảy ra thật, và
--                biểu đồ thống kê theo ngày không được phép đổi số vì hôm
--                nay có người xóa một chương. Bảng xếp hạng đã tự bỏ qua
--                những id không còn tra ra truyện.
--   ai_usage     cũng vậy, và còn thêm một lý do: nó là sổ chi phí. Một
--                lượt đã tiêu tiền thì không được biến mất khỏi sổ chỉ vì
--                chương của nó bị xóa.
-- =====================================================================

alter table reading_progress drop foreign key fk_progress_chapter;
alter table reading_progress
    add constraint fk_progress_chapter foreign key (chapter_id)
        references chapters (id) on delete cascade;

alter table favorites drop foreign key fk_favorites_story;
alter table favorites
    add constraint fk_favorites_story foreign key (story_id)
        references stories (id) on delete cascade;

alter table ratings_comments drop foreign key fk_rc_story;
alter table ratings_comments
    add constraint fk_rc_story foreign key (story_id)
        references stories (id) on delete cascade;
