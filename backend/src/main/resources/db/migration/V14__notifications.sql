-- =====================================================================
-- V14 — Hộp thư của người đọc.
--
-- <h3>Bảng này là nguồn sự thật, không phải luồng SSE</h3>
-- Trang đã có một đường đẩy tin xuống trình duyệt (V8 dựng phần phiên
-- bản nội dung, `ChapterEventStream` dựng phần vận chuyển). Nó tốt cho
-- việc nó làm — báo ngay cho người đang mở một chương — nhưng nó không
-- lưu gì cả: người offline lúc quản trị viên cấp VIP thì không bao giờ
-- biết. Bảng này bù đúng chỗ ấy, và đảo ngược vai trò: hàng ở đây là sự
-- thật, khung tin đẩy đi chỉ là bản sao gửi sớm.
--
-- <h3>Vì sao không có cột `is_read`</h3>
-- `read_at` trả lời được cả "đã đọc chưa" (is not null) lẫn "đọc lúc
-- nào". Thêm một cột boolean là có hai nguồn sự thật cho một câu hỏi, và
-- hai cột luôn phải bằng nhau là hai cột có thể lệch nhau. Cùng lập luận
-- đã dùng ở V13 khi từ chối cột `status` cho gift code.
--
-- <h3>Vì sao không có bảng outbox</h3>
-- Mẫu outbox tồn tại vì cơ sở dữ liệu và đường gửi có thể hỏng độc lập.
-- Ở đây bảng này *đã là* outbox: nó được ghi trong cùng giao dịch với
-- nghiệp vụ (hoàn Xu, cấp VIP, ghi nhận thanh toán), và bên nhận không
-- chờ ai gửi — trình duyệt tự hỏi lại hộp thư mỗi lần nối lại luồng. Một
-- khung tin mất trên đường không mất mát gì. Thêm `status`,
-- `attempt_count`, `available_at` cùng một tác vụ quét lại sẽ là bản sao
-- thứ hai của cùng dữ liệu, chạy trên một máy chủ ngủ sau 15 phút vắng
-- khách.
--
-- <h3>Múi giờ</h3>
-- `datetime(6)` ghi từ `java.time.Instant`, như mọi mốc thời gian khác
-- trong lược đồ này. Việc đổi sang giờ Việt Nam xảy ra đúng một lần, ở
-- trình duyệt, lúc hiển thị.
-- =====================================================================

create table notifications (
    id bigint not null auto_increment,

    user_id bigint not null,

    -- Chuyện gì đã xảy ra: VIP_GRANTED, CHAPTER_DELETED, PAYMENT…
    --
    -- varchar chứ không enum(...) của MySQL, như mọi enum khác của lược
    -- đồ này: thêm một loại thông báo mới là thêm một hằng trong Java,
    -- không phải một lệnh ALTER TABLE khóa bảng.
    type varchar(40) not null,

    -- INFO / SUCCESS / WARNING / IMPORTANT. Tách khỏi `type` vì hai câu
    -- hỏi khác nhau: loại nói *chuyện gì*, mức nói *có đáng dừng lại
    -- không*. Một tin chung có thể là "bảo trì lúc 2 giờ sáng" hoặc "tài
    -- khoản của bạn sắp bị khóa" — cùng loại, khác mức.
    priority varchar(20) not null,

    -- Văn bản thuần, cả hai. Không có chỗ nào trong giao diện dựng chúng
    -- thành HTML, nên nội dung do quản trị viên gõ không mở được đường
    -- chèn mã.
    title varchar(160) not null,
    message varchar(500) not null,

    -- Ý định, không phải URL: VIEW_REFUND_HISTORY, VIEW_STORY…
    --
    -- Đường dẫn của trang đọc là chuyện của trang đọc và sẽ đổi; đóng
    -- băng nó vào những hàng không bao giờ được sửa lại nghĩa là một lần
    -- đổi đường dẫn làm mọi thông báo cũ trỏ vào hư không. Việc đổi ý
    -- định thành đường dẫn xảy ra ở trình duyệt.
    action_type varchar(40),

    -- Thứ thông báo này nói về. Cặp (type, id) không có khóa ngoại, và
    -- đó là chủ ý kép: đích đến nằm ở bảng khác nhau tùy giá trị — cùng
    -- lối với wallet_transactions ở V5 — và phân nửa số thông báo dùng
    -- cặp này nói về những thứ *vừa bị xóa*. Một khóa ngoại có CASCADE
    -- sẽ xóa mất đúng lời báo "chương của bạn đã bị gỡ" ngay khi chương
    -- bị gỡ; một khóa ngoại không cascade sẽ chặn luôn lệnh xóa.
    related_entity_type varchar(30),
    related_entity_id bigint,

    -- Vài con số phụ dạng JSON phẳng: số Xu đã hoàn, số chương, hạn VIP.
    -- Đây là chỗ giữ cho lược đồ khỏi phải mọc thêm cột mỗi lần có một
    -- loại thông báo mới — không phải chỗ đổ nguyên một bản ghi vào.
    -- Trần 1000 ký tự là để điều đó không trôi dần.
    metadata varchar(1000),

    -- Định danh của *sự kiện nghiệp vụ* sinh ra thông báo này.
    --
    -- Dựng từ chính sự việc: `payment:314`, `chapter-deleted:41:7`. Nó
    -- là thứ khiến một webhook gọi lại, một lần thử lại của trình duyệt,
    -- hay một handler chạy hai lượt không sinh ra dòng thứ hai — xem
    -- ràng buộc UNIQUE bên dưới.
    event_id varchar(120) not null,

    -- Null nghĩa là chưa đọc.
    read_at datetime(6),

    created_at datetime(6) not null,

    primary key (id)
) engine=InnoDB;

-- Hộp thư đi theo tài khoản.
--
-- CASCADE ở đây, khác hẳn với wallet_transactions vốn cố ý *không* có
-- cascade: sổ cái tiền là chứng từ kế toán và phải sống lâu hơn tài
-- khoản, còn hộp thư thì không còn gì đáng giữ khi người nhận không còn.
alter table notifications
    add constraint fk_notifications_user foreign key (user_id)
    references users (id) on delete cascade;

-- Ràng buộc quan trọng nhất của lần migration này.
--
-- Nó biến "không tạo thông báo trùng" từ một phép kiểm trong Java — mà
-- hai luồng song song đều vượt qua được — thành một quy tắc cơ sở dữ
-- liệu tự giữ. Tầng service vẫn hỏi trước, nhưng chỉ để lần thứ hai của
-- cùng một sự kiện thành một lệnh rỗng thay vì một lỗi ràng buộc kéo
-- theo cả giao dịch nghiệp vụ.
--
-- Cần nói rõ giới hạn: nó chặn *thông báo* trùng, không chặn *nghiệp vụ*
-- chạy hai lần. Tiền không bị hoàn hai lần là nhờ `chapter_entitlements`
-- và câu UPDATE có điều kiện trên `payment_orders`, và điều đó phải đúng
-- kể cả khi bảng này trống.
alter table notifications
    add constraint uk_notifications_user_event unique (user_id, event_id);

-- ----- Chỉ mục -----
--
-- Cả bảng chỉ được đọc theo một chiều — của ai — nên hai chỉ mục dưới
-- đây phủ hết đường đọc và không câu nào phải quét bảng.
--
-- Chiều thứ nhất: một trang hộp thư, mới nhất trước.
create index idx_notifications_user_created on notifications (user_id, created_at);

-- Chiều thứ hai: đếm số chưa đọc. Con số này được hỏi ở mỗi lần mở trang
-- và mỗi lần nối lại luồng, nên nó phải rẻ. MySQL không có chỉ mục một
-- phần, nên `read_at` vào thẳng khóa: `WHERE user_id = ? AND read_at IS
-- NULL` vẫn là một lần dò chỉ mục.
create index idx_notifications_user_unread on notifications (user_id, read_at);

-- Không có tác vụ tự xóa thông báo cũ, và đó là chủ ý: dữ liệu này thuộc
-- về người đọc, không phải rác của hệ thống. Chỉ mục (user_id,
-- created_at) ở trên cũng là thứ khiến một lần dọn theo tuổi về sau —
-- nếu có nhu cầu thật — chỉ là một câu DELETE có điều kiện, không phải
-- một lần quét bảng.
