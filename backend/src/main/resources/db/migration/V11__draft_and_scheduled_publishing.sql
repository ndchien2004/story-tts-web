-- =====================================================================
-- V11 — Nháp và hẹn giờ đăng, cho cả truyện lẫn chương.
--
-- <h3>Vấn đề</h3>
-- Không có chỗ nào để gõ dở một chương. Bấm Lưu là cả thế giới đọc được
-- ngay, kể cả bản mới viết được ba dòng. Và với một trang truyện, "ra
-- chương lúc 20h thứ Sáu" là nếp làm việc bình thường, hiện phải canh giờ
-- rồi bấm tay.
--
-- <h3>Một cột, ba trạng thái</h3>
--
--   null            → NHÁP. Chỉ quản trị viên thấy.
--   mốc ở tương lai → HẸN GIỜ. Chưa ai thấy, và sẽ tự hiện.
--   mốc đã qua      → ĐÃ ĐĂNG.
--
-- Cách khác là một cột trạng thái enum kèm một cột mốc thời gian riêng.
-- Bỏ nó vì hai cột ấy có thể mâu thuẫn nhau — trạng thái DRAFT kèm một mốc
-- đã qua thì tin cột nào? — và mỗi lần thêm một cách xuất bản mới lại phải
-- đồng bộ hai chỗ. Một cột thì không có trạng thái nào bất khả thi.
--
-- <h3>Không có job nào chạy lúc tới giờ, và đó là chủ ý</h3>
-- "Đến giờ thì tự đăng" được cài bằng một mệnh đề WHERE
-- (`published_at <= now()`) chứ không bằng một tác vụ định kỳ đi đổi
-- trạng thái. Ba lý do, xếp theo mức quan trọng:
--
--   1. Không có cửa sổ sai. Chương hẹn 20:00:00 xuất hiện đúng 20:00:00
--      với mọi người đọc, chứ không phải "trong vòng một phút sau đó, tuỳ
--      lúc job chạy".
--   2. Không có lần chạy nào bị bỏ lỡ. Máy chủ ở đây ngủ sau 15 phút vắng
--      khách (gói miễn phí của Render); một job hẹn giờ trên một tiến
--      trình đang ngủ đơn giản là không chạy, và chương hẹn 3 giờ sáng sẽ
--      nằm im tới khi có người đầu tiên ghé qua.
--   3. Không có trạng thái nào phải sửa lại khi đổi ý. Dời lịch là cập
--      nhật một cột; với cách kia thì còn phải hỏi "đã chạy chưa".
--
-- Cái giá: mọi truy vấn công khai phải mang thêm mệnh đề ấy. Đó là lý do
-- các chỉ mục dưới đây tồn tại.
-- =====================================================================

alter table stories
    add column published_at datetime(6);

alter table chapters
    add column published_at datetime(6);

-- ----- Dữ liệu đang có -----
--
-- Mọi truyện và chương đang chạy đều đang hiển thị công khai, nên chúng
-- được coi là đã đăng — và đăng từ lúc chúng được tạo, mốc gần đúng nhất
-- mà cơ sở dữ liệu còn giữ.
--
-- Không dùng NOW(): gán tất cả cùng một mốc "lúc migration chạy" sẽ làm
-- hỏng mọi thứ sắp theo ngày đăng, và ngày đăng là thứ người đọc dùng để
-- biết truyện nào mới.
update stories set published_at = created_at where published_at is null;
update chapters set published_at = created_at where published_at is null;

-- ----- Chỉ mục -----
--
-- Danh sách chương của một truyện dài hỏi "chương nào của truyện này đã
-- đăng" cho hàng trăm dòng một lúc, và giờ nó hỏi kèm mệnh đề thời gian.
-- Chỉ mục ghép đặt story_id trước vì đó là cột lọc bằng dấu bằng; cột so
-- sánh khoảng luôn đứng sau trong một chỉ mục ghép.
create index idx_chapters_story_published on chapters (story_id, published_at);
create index idx_stories_published on stories (published_at);
