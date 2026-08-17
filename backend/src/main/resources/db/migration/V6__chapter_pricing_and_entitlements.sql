-- =====================================================================
-- V6 — Giá Xu của chương, và quyền đã mở của người đọc.
--
-- <h3>Vì sao không thêm một giá trị vào access_level</h3>
-- Cách hiển nhiên là thêm `COIN` và `HYBRID` vào enum `access_level`.
-- Nhưng hai thứ ấy không cùng một loại câu hỏi:
--
--   access_level trả lời "ai được phép nhìn tới chương này"
--   coin_price   trả lời "mở nó ra tốn bao nhiêu"
--
-- Trộn chúng vào một cột thì mỗi lần thêm một cách bán là số giá trị enum
-- lại nhân lên: VIP, COIN, VIP_HOẶC_COIN, MEMBER_VÀ_COIN... Để rời nhau
-- thì bốn trạng thái đề bài cần đến rơi ra một cách tự nhiên từ hai cột:
--
--   PUBLIC + giá 0   → đọc tự do
--   MEMBER + giá 0   → cần đăng nhập
--   VIP    + giá 0   → chỉ VIP (đúng hành vi cũ, không đổi)
--   VIP    + giá 50  → VIP đọc miễn phí, người thường trả 50 Xu  ← HYBRID
--
-- Và quan trọng nhất: mọi chương đang có đều nhận giá mặc định 0, nên
-- không chương nào đổi hành vi vì lần migration này. Đường tính tiền chỉ
-- mở ra ở đúng những chương mà quản trị viên chủ động đặt giá.
-- =====================================================================

alter table chapters
    add column coin_price bigint not null default 0;

-- ----- Quyền đã mở -----
-- Tách khỏi sổ cái ví, dù mua chương luôn sinh ra một dòng ở cả hai nơi.
-- Lý do: hai bảng trả lời hai câu hỏi khác nhau và bị hỏi ở hai nhịp khác
-- nhau. "Người này đã trả bao nhiêu Xu, khi nào" là câu hỏi kế toán, hỏi
-- vài lần một ngày. "Người này có được mở chương 412 không" là câu hỏi
-- quyền, hỏi ở mọi lần tải trang, và phải trả lời được bằng một lần đọc
-- chỉ mục chứ không phải bằng việc duyệt lịch sử tiền bạc.
--
-- Tách ra còn để quyền có thể đến từ nơi khác ngoài tiền: `source` cho
-- phép quản trị viên mở một chương cho ai đó mà không phải giả vờ có một
-- giao dịch Xu chưa từng xảy ra.
create table chapter_entitlements (
    id bigint not null auto_increment,
    user_id bigint not null,
    chapter_id bigint not null,

    -- COIN_PURCHASE hay ADMIN_GRANT. Còn chỗ cho combo/trọn bộ về sau:
    -- chúng chỉ là những cách khác để sinh ra cùng loại dòng này.
    source varchar(30) not null,

    -- Số Xu đã trả, chép lại tại thời điểm mua. Giá chương đổi về sau
    -- không được phép làm sai lệch điều đã xảy ra rồi. 0 với quyền do
    -- quản trị viên cấp.
    coins_spent bigint not null default 0,

    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

alter table chapter_entitlements
    add constraint fk_entitlement_user foreign key (user_id) references users (id);

-- ON DELETE CASCADE ở đây, và cố ý chỉ ở đây.
--
-- Xóa một chương thì quyền đọc chương ấy không còn nghĩa gì, nên nó đi theo.
-- Nhưng dòng sổ cái ghi lần trả Xu cho chương ấy thì <b>ở lại</b>: bảng
-- `wallet_transactions` không có khóa ngoại nào trỏ tới `chapters`, đúng vì lý
-- do này. Nội dung có thể bị gỡ; lịch sử tiền bạc thì không.
--
-- Để cơ sở dữ liệu tự dọn thay vì gọi xóa trong mã nguồn, vì truyện bị xóa sẽ
-- kéo theo chương qua cascade của JPA — mà đường ấy không đi qua repository nào
-- của phần Xu, nên một lệnh xóa viết trong ChapterService sẽ bị bỏ qua đúng lúc
-- cần tới nó nhất.
alter table chapter_entitlements
    add constraint fk_entitlement_chapter foreign key (chapter_id)
    references chapters (id) on delete cascade;

-- Đây là ràng buộc quan trọng nhất của cả lần migration này.
--
-- Nó là thứ khiến việc bấm "Mở khóa" ba lần liên tiếp — hoặc trình duyệt
-- tự gửi lại request — không thể trừ Xu ba lần. Không phải nhờ mã nguồn
-- kiểm tra trước (mã nguồn có kiểm, nhưng hai request chạy song song thì
-- cả hai đều thấy "chưa mua"), mà nhờ cơ sở dữ liệu từ chối dòng thứ hai.
-- Request thua cuộc bị cuộn ngược nguyên vẹn, nên Xu của nó cũng quay lại.
alter table chapter_entitlements
    add constraint uk_entitlement_user_chapter unique (user_id, chapter_id);

-- Trang chi tiết truyện hỏi "trong các chương này, tôi đã mở những chương
-- nào" — một câu cho cả danh sách, đi bằng chỉ mục của ràng buộc unique ở
-- trên. Chỉ mục riêng theo chapter_id là để trả lời câu ngược lại ("chương
-- này có bao nhiêu người mua"), thứ trang thống kê của quản trị viên cần.
create index idx_entitlement_chapter on chapter_entitlements (chapter_id);
