-- =====================================================================
-- V13 — Gift code: mã đổi Xu, và sổ ghi ai đã đổi mã nào.
--
-- <h3>Đây không phải một bảng cấu hình</h3>
-- Đổi một mã là tạo ra Xu trong ví của một người. Nó nằm cùng hạng với
-- việc nạp tiền chứ không cùng hạng với việc thêm một thể loại truyện,
-- nên lược đồ ở đây được viết theo đúng lối của `wallets` và
-- `chapter_entitlements`: mỗi quy tắc nghiệp vụ mà việc sai sẽ tốn Xu đều
-- có một ràng buộc của cơ sở dữ liệu đứng sau, chứ không chỉ có một phép
-- kiểm tra trong Java.
--
-- Ba quy tắc, ba cơ chế:
--
--   "một tài khoản đổi một mã đúng một lần"
--       → UNIQUE (gift_code_id, user_id)
--
--   "không bao giờ vượt quá max_uses"
--       → UPDATE ... WHERE used_count < max_uses  (câu lệnh vừa kiểm vừa
--         ghi, khóa dòng cho tới hết giao dịch — giống hệt phép trừ Xu ở
--         `wallets`), có CHECK đứng sau làm chốt chặn cuối
--
--   "SUMMER2026 và summer2026 là một mã"
--       → UNIQUE (code) trên giá trị đã chuẩn hóa thành chữ hoa
--
-- Không có cơ chế nào trong ba cái trên phụ thuộc vào việc hai request
-- chạy song song có nhìn thấy nhau hay không.
--
-- <h3>Vì sao không có cột `status`</h3>
-- SCHEDULED / ACTIVE / EXPIRED / DISABLED / EXHAUSTED đều suy ra được từ
-- năm cột dưới đây. Lưu thêm một cột trạng thái là có hai nguồn sự thật
-- cho cùng một câu hỏi — và ba trong năm giá trị ấy đổi *theo thời gian*,
-- tức là không có lệnh ghi nào để móc vào mà cập nhật. Giữ đồng bộ thì
-- phải có một tác vụ định kỳ, mà máy chủ này ngủ sau 15 phút vắng khách.
-- Cùng lập luận với `published_at` ở V11.
--
-- <h3>Múi giờ</h3>
-- `start_at` và `end_at` là datetime(6) như mọi mốc thời gian khác trong
-- lược đồ này, và được ghi từ `java.time.Instant`. Không có chỗ nào trong
-- backend diễn giải chúng theo giờ địa phương: so sánh "đã tới giờ chưa"
-- là một phép so sánh giữa hai Instant. Việc đổi sang giờ Việt Nam xảy ra
-- đúng một lần, ở trình duyệt, lúc hiển thị.
-- =====================================================================

create table gift_codes (
    id bigint not null auto_increment,

    -- Đã chuẩn hóa: chữ hoa, bỏ khoảng trắng hai đầu. Chỉ có một dạng
    -- được lưu và cũng chỉ có một dạng được tra, nên UNIQUE bên dưới thật
    -- sự chặn được mã trùng — chứ không phải chỉ chặn được trường hợp hai
    -- người tạo gõ y hệt nhau.
    code varchar(64) not null,

    -- Số Xu mỗi lượt đổi nhận được.
    coin_amount bigint not null,

    -- null = có hiệu lực ngay.
    start_at datetime(6),

    -- null = không hết hạn.
    end_at datetime(6),

    -- null = không giới hạn lượt.
    --
    -- Dùng null chứ không dùng một giá trị đánh dấu như -1: một con số âm
    -- nằm trong cột đếm lượt sẽ lọt vào mọi phép so sánh và mọi báo cáo,
    -- còn null thì bị SQL loại ra rõ ràng ở đúng chỗ cần loại.
    max_uses integer,

    -- Con số đếm sẵn, dư thừa so với việc đếm dòng ở bảng bên dưới, và cố
    -- ý dư thừa: một mã phát trong sự kiện nhận hàng nghìn request gần như
    -- cùng lúc, và đếm lại cả bảng ở mỗi lần là quét một tập đang lớn dần
    -- ngay trong đường nóng. Hai con số được ghi trong cùng một giao dịch
    -- nên chúng không lệch nhau được.
    used_count integer not null default 0,

    -- Công tắc của quản trị viên. Tắt là cách đúng để ngừng phát một mã đã
    -- có người đổi; xóa nó đi sẽ kéo theo cả lịch sử.
    enabled bit not null default 1,

    -- Ghi chú nội bộ; người đọc không bao giờ thấy.
    description varchar(300),

    -- Ai đã tạo. Cho null vì tài khoản quản trị có thể bị xóa về sau, và
    -- mã thì ở lại — xem ON DELETE SET NULL bên dưới.
    created_by bigint,

    created_at datetime(6) not null,
    updated_at datetime(6),

    primary key (id)
) engine=InnoDB;

alter table gift_codes
    add constraint uk_gift_codes_code unique (code);

alter table gift_codes
    add constraint fk_gift_codes_creator foreign key (created_by)
    references users (id) on delete set null;

-- ----- Chốt chặn -----
--
-- Cả bốn ràng buộc dưới đây lẽ ra không bao giờ chạm tới: tầng service đã
-- kiểm đủ, và câu UPDATE chiếm lượt đã mang sẵn điều kiện của nó. Chúng ở
-- đây phòng đúng cái ngày có người viết một đường ghi mới mà quên mất một
-- trong số đó — cùng vai trò với ck_wallets_balance_non_negative ở V5.
alter table gift_codes
    add constraint ck_gift_codes_amount check (coin_amount > 0);

alter table gift_codes
    add constraint ck_gift_codes_max_uses check (max_uses is null or max_uses > 0);

-- Trần lượt đổi, ở tầng cuối cùng. Nó cũng là thứ chặn quản trị viên hạ
-- max_uses xuống dưới số lượt đã phát ra — một trạng thái mà từ đó mọi
-- báo cáo "còn lại bao nhiêu" đều trả lời bằng số âm.
alter table gift_codes
    add constraint ck_gift_codes_used_within_max
    check (used_count >= 0 and (max_uses is null or used_count <= max_uses));

alter table gift_codes
    add constraint ck_gift_codes_window
    check (start_at is null or end_at is null or start_at < end_at);

-- ----- Chỉ mục -----
--
-- Đường đổi mã tra đúng một dòng theo `code`, và UNIQUE ở trên đã là chỉ
-- mục cho việc ấy. Hai chỉ mục dưới đây phục vụ bảng quản trị: lọc theo
-- tình trạng (một phép hỏi trên enabled cộng hai mốc thời gian) và sắp
-- theo ngày tạo, mới nhất trước.
create index idx_gift_codes_enabled_window on gift_codes (enabled, start_at, end_at);
create index idx_gift_codes_created on gift_codes (created_at);

-- =====================================================================
-- Sổ đổi mã.
--
-- Tách khỏi `wallet_transactions` dù mỗi lượt đổi sinh ra một dòng ở cả
-- hai nơi — cùng lập luận với `chapter_entitlements` ở V6. Hai bảng trả
-- lời hai câu hỏi khác nhau:
--
--   "người này đã nhận bao nhiêu Xu, khi nào, từ đâu" là câu hỏi kế toán
--   "người này đã đổi mã SUMMER2026 chưa"           là câu hỏi *ràng buộc*
--
-- Câu thứ hai phải trả lời được bằng một lần đọc chỉ mục, và quan trọng
-- hơn: nó phải trả lời được bởi chính cơ sở dữ liệu, dưới dạng một ràng
-- buộc từ chối dòng thứ hai. Không thể đặt ràng buộc ấy lên sổ cái ví, vì
-- sổ cái là một bảng chỉ ghi thêm mà mọi loại giao dịch cùng dùng chung.
-- =====================================================================

create table gift_code_redemptions (
    id bigint not null auto_increment,
    gift_code_id bigint not null,
    user_id bigint not null,

    -- Số Xu lượt này thật sự đã phát, chép lại tại thời điểm đổi. Quản trị
    -- viên sửa được mệnh giá của một mã đang chạy; số Xu đã vào ví một
    -- người thì không được đổi theo. Cùng lý do với
    -- chapter_entitlements.coins_spent.
    coin_amount bigint not null,

    -- Lúc đổi. Không có thêm cột `redeemed_at` riêng: nó sẽ luôn bằng cột
    -- này, và hai cột luôn bằng nhau là hai cột có thể lệch nhau.
    created_at datetime(6) not null,

    primary key (id)
) engine=InnoDB;

-- Ràng buộc quan trọng nhất của cả lần migration này.
--
-- Nó là thứ khiến bấm "Đổi mã" ba lần liên tiếp — hoặc mở hai tab cùng
-- gửi một lúc — không thể cộng Xu ba lần. Không phải nhờ mã nguồn kiểm
-- tra trước (mã nguồn có kiểm, nhưng hai request song song thì cả hai đều
-- thấy "chưa đổi"), mà nhờ cơ sở dữ liệu từ chối dòng thứ hai. Request
-- thua cuộc bị cuộn ngược nguyên vẹn, nên cả số Xu lẫn lượt đã chiếm đều
-- quay lại cùng.
alter table gift_code_redemptions
    add constraint uk_gift_redemption_code_user unique (gift_code_id, user_id);

-- Không có ON DELETE CASCADE, và đó là chủ ý.
--
-- Nó là thứ biến "không được xóa cứng một mã đã có người đổi" thành một
-- quy tắc mà cơ sở dữ liệu tự giữ: lệnh xóa sẽ bị từ chối chứ không lặng
-- lẽ cuốn theo cả lịch sử. Tầng service kiểm trước để trả về một câu dễ
-- hiểu ("hãy tắt mã thay vì xóa"), nhưng chốt chặn nằm ở đây.
alter table gift_code_redemptions
    add constraint fk_gift_redemption_code foreign key (gift_code_id)
    references gift_codes (id);

alter table gift_code_redemptions
    add constraint fk_gift_redemption_user foreign key (user_id)
    references users (id);

alter table gift_code_redemptions
    add constraint ck_gift_redemption_amount check (coin_amount > 0);

-- "Người này đã đổi những mã nào, gần nhất trước" — trang tài khoản, và
-- các cuộc tra cứu khi có người hỏi vì sao mình được cộng Xu.
--
-- Chiều ngược lại (danh sách người đã đổi một mã, cho bảng quản trị) đi
-- qua UNIQUE ở trên: gift_code_id đứng đầu ràng buộc ấy nên nó cũng là
-- chỉ mục cho phép lọc theo mã. Thêm một chỉ mục nữa cho cùng cột đầu là
-- trả phí ghi hai lần cho một đường đọc.
create index idx_gift_redemption_user on gift_code_redemptions (user_id, created_at);
