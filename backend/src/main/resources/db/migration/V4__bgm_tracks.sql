-- =====================================================================
-- V4 — Kho nhạc nền do quản trị viên tải lên.
--
-- Trước đây nhạc nền chỉ có hai lối: một tệp manifest.json nằm cạnh mã
-- nguồn frontend, và bản người nghe tự mở từ máy họ. Lối thứ nhất đòi phải
-- build lại cả trang web mỗi lần thêm một bài; lối thứ hai đòi người nghe
-- phải sẵn có nhạc trong máy. Nghĩa là trên một máy chủ đã chạy, ô "chọn
-- nhạc nền" luôn trống với người vừa mở trang lần đầu.
--
-- Bảng này là lối thứ ba, và là lối chính: quản trị viên tải nhạc lên như
-- tải audio chương, mọi người nghe thấy ngay ở lần tải trang sau.
--
-- Vì sao có cột `active` thay vì chỉ xóa: bản nhạc bị gỡ khỏi danh sách
-- chọn vẫn có thể đang được ai đó nghe dở, và một bản nhạc rút khỏi kho vì
-- lý do bản quyền thường được đưa lại sau. Tắt là một thao tác lùi được;
-- xóa thì file cũng đi theo.
--
-- Không có khóa ngoại nào trỏ vào bảng này: lựa chọn nhạc nền của người
-- nghe nằm trong localStorage của trình duyệt họ, không nằm ở máy chủ.
-- =====================================================================

create table bgm_tracks (
    id bigint not null auto_increment,
    title varchar(150) not null,
    credit varchar(255),
    file_path varchar(500) not null,
    content_type varchar(100),
    file_size bigint,
    duration_seconds integer,
    active bit not null,
    sort_order integer not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

-- Danh sách gửi cho người nghe luôn lọc theo `active` rồi xếp theo
-- `sort_order`; hai cột ấy đi cùng nhau nên chúng chung một chỉ mục.
create index idx_bgm_active_order on bgm_tracks (active, sort_order);
