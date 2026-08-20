-- =====================================================================
-- V15 — Hộp thư hỗ trợ: người đọc nhắn cho quản trị viên, và ngược lại.
--
-- <h3>Hai bảng này là nguồn sự thật, WebSocket chỉ là đường giao</h3>
-- Cùng lập luận đã viết ở V14 cho `notifications`, và lần này nó còn
-- quan trọng hơn: ở đó khung tin bị mất chỉ làm cái chuông cập nhật
-- muộn, còn ở đây nó sẽ là một câu người ta *đã gõ* mà không ai nhận
-- được. Nên thứ tự bắt buộc là ghi trước, commit, rồi mới đẩy đi — và
-- bên nhận không bao giờ chờ ai đẩy: mỗi lần nối lại, trình duyệt tự
-- hỏi lại phần nó chưa thấy bằng REST.
--
-- <h3>Vì sao không có bảng outbox</h3>
-- Y như V14: `support_messages` *đã là* outbox. Nó được ghi trong cùng
-- giao dịch với việc cập nhật cuộc trò chuyện, và người nhận đồng bộ lại
-- từ chính nó. Thêm `status`, `attempt_count`, `available_at` cùng một
-- tác vụ quét lại sẽ là bản sao thứ hai của cùng dữ liệu, chạy trên một
-- máy chủ ngủ sau mười lăm phút vắng khách — nó giải quyết một vấn đề mà
-- thiết kế này không có.
--
-- <h3>Thứ tự tin nhắn nằm ở `id`, không nằm ở đồng hồ</h3>
-- `id` là bigint auto_increment do InnoDB cấp, nên nó tăng đơn điệu
-- trong phạm vi một máy chủ và không bao giờ do trình duyệt quyết định.
-- Mọi câu truy vấn lịch sử đều `ORDER BY id`, và con trỏ phân trang cũng
-- là `id` — không phải `created_at`, vốn có thể trùng nhau tới từng
-- micro giây và vốn là thứ mà một đồng hồ máy chủ nhảy ngược sẽ làm
-- hỏng.
--
-- <h3>Múi giờ</h3>
-- `datetime(6)` ghi từ `java.time.Instant`, như mọi mốc thời gian khác
-- trong lược đồ này. Việc đổi sang giờ Việt Nam xảy ra đúng một lần, ở
-- trình duyệt, lúc hiển thị.
-- =====================================================================

create table support_conversations (
    id bigint not null auto_increment,

    -- Chủ cuộc trò chuyện: người đọc. Quản trị viên *không* có hàng ở
    -- đây — họ là "phía hỗ trợ", một phía chung, không phải một người.
    user_id bigint not null,

    -- OPEN / CLOSED / BLOCKED.
    --
    -- Ba giá trị, không nhiều hơn. ARCHIVED bị bỏ vì không có màn hình
    -- nào cần nó và không có quy tắc nghiệp vụ nào đổi theo nó; thêm một
    -- trạng thái không ai đọc là thêm một nhánh mà mọi phép kiểm về sau
    -- phải nhớ tới.
    --
    --   OPEN    — đang trao đổi.
    --   CLOSED  — quản trị viên coi là đã xong. Không phải ngõ cụt: một
    --             tin mới của bất kỳ bên nào cũng mở lại, trong cùng
    --             giao dịch ghi tin ấy. Đó là cách một phiếu hỗ trợ vận
    --             hành, và nó khiến cuộc đua "đóng lúc người ta đang gõ"
    --             có một kết cục xác định thay vì một câu bị nuốt mất.
    --   BLOCKED — quản trị viên chặn. Người đọc vẫn xem được lịch sử
    --             nhưng không gửi được nữa. Đây mới là công cụ chặn spam;
    --             CLOSED không phải.
    status varchar(20) not null,

    -- Bản sao vài trường của tin nhắn cuối, để danh sách hộp thư quản
    -- trị không phải hỏi thêm một câu cho mỗi dòng.
    --
    -- Chúng được ghi trong *cùng* giao dịch với tin nhắn sinh ra chúng,
    -- nên không có khoảnh khắc nào bảng này nói khác bảng kia. Đây là
    -- bộ nhớ đệm có chủ đích, không phải nguồn sự thật thứ hai: mất
    -- chúng đi thì dựng lại được bằng một câu truy vấn.
    last_message_id bigint,
    last_message_at datetime(6),
    last_message_preview varchar(200),
    last_message_sender_role varchar(20),

    -- Trạng thái đã đọc của hai phía, dưới dạng một *mốc đơn điệu* chứ
    -- không phải một bộ đếm.
    --
    -- Đây là khác biệt then chốt. Bộ đếm phải cộng khi có tin mới và trừ
    -- khi có người đọc, và hai lệnh ấy chạy song song thì con số trôi đi
    -- không đường về. Một mốc thì chỉ có một lệnh — "đẩy lên tới id
    -- này" — và số chưa đọc luôn là một phép đếm dẫn xuất. Hệ quả đúng
    -- theo yêu cầu: tin A, tin B, một lần đọc, rồi tin C — C không bao
    -- giờ bị coi là đã đọc, vì id của nó lớn hơn mốc.
    --
    -- Mốc chỉ được phép tăng (`WHERE ... < :id`), nên hai tab bấm lệch
    -- nhịp không kéo nó lùi lại được.
    --
    -- Phía quản trị là *một* mốc dùng chung cho mọi quản trị viên, và đó
    -- là chủ ý: hộp thư hỗ trợ là hàng đợi của cả đội, không phải hộp
    -- thư riêng của từng người. Một người đã trả lời thì việc ấy đã xong
    -- với cả đội.
    user_last_read_message_id bigint not null default 0,
    admin_last_read_message_id bigint not null default 0,

    -- Ai đóng, lúc nào. Null khi chưa từng đóng hoặc đã mở lại.
    closed_at datetime(6),
    closed_by bigint,

    created_at datetime(6) not null,
    updated_at datetime(6) not null,

    primary key (id)
) engine=InnoDB;

-- Đúng một cuộc trò chuyện cho một người, vĩnh viễn.
--
-- Ràng buộc này *là* phần chống đua của việc tạo. Hai request cùng bấm
-- "gửi hỗ trợ" ở hai tab sẽ có một bên thua ở tầng cơ sở dữ liệu, và bên
-- thua chỉ việc đọc lại hàng đã có — không có phép kiểm nào trong Java
-- mà hai luồng song song cùng vượt qua được.
--
-- Cố ý là UNIQUE(user_id) chứ không phải "một cuộc *đang mở* cho một
-- người": phương án kia cần một chỉ mục một phần mà MySQL không có, và
-- nó sinh ra một luồng hội thoại đứt đoạn thành nhiều mảnh mà người hỗ
-- trợ phải tự ghép lại. Ở đây luồng là liên tục, còn `status` nói nó
-- đang ở giai đoạn nào.
alter table support_conversations
    add constraint uk_support_conversations_user unique (user_id);

-- Hộp thư đi theo tài khoản, như `notifications` ở V14: xóa người đọc
-- thì không còn gì đáng giữ ở đây.
alter table support_conversations
    add constraint fk_support_conversations_user foreign key (user_id)
    references users (id) on delete cascade;

-- Không cascade: đây là *ai đã đóng*, một dấu vết kiểm toán. Ứng dụng
-- không có đường xóa tài khoản nào (quản trị viên chỉ khóa hoặc hạ
-- quyền), nên nhánh này gần như không bao giờ chạy — SET NULL để nếu
-- ngày ấy tới thì mất một cái tên chứ không mất cả hàng.
alter table support_conversations
    add constraint fk_support_conversations_closed_by foreign key (closed_by)
    references users (id) on delete set null;

-- Danh sách hộp thư của quản trị viên: lọc theo trạng thái, xếp theo
-- hoạt động mới nhất. Đây là câu chạy nhiều nhất của bảng này và là câu
-- duy nhất không đi qua khóa chính.
create index idx_support_conversations_status_activity
    on support_conversations (status, last_message_at);

-- Xếp theo hoạt động mới nhất *không* lọc trạng thái — tab "Tất cả".
create index idx_support_conversations_activity
    on support_conversations (last_message_at);


create table support_messages (
    id bigint not null auto_increment,

    conversation_id bigint not null,

    -- Người gửi, luôn có. Tin hệ thống ("đã đóng cuộc trò chuyện") mang
    -- id của chính quản trị viên đã gây ra nó, nên không có hàng nào
    -- không truy được về một người thật.
    sender_id bigint not null,

    -- USER / ADMIN, chốt lại *tại thời điểm gửi*.
    --
    -- Lưu chứ không suy ra từ `users.role` lúc đọc: một quản trị viên
    -- bị hạ quyền về sau không được phép làm những câu họ đã trả lời
    -- biến thành câu của người đọc. Lịch sử là lịch sử.
    --
    -- Và nó *không bao giờ* đến từ trình duyệt — máy chủ tự điền từ
    -- người đã xác thực. Xem SupportMessageService.
    sender_role varchar(20) not null,

    -- TEXT / SYSTEM.
    --
    -- Cột thứ hai chứ không phải một giá trị thứ ba của `sender_role`,
    -- và sự khác nhau ấy có chủ đích: hai cột trả lời hai câu khác nhau
    -- — *ai* nói, và nói *kiểu gì*. Nhét SYSTEM vào cả hai sẽ là hai cột
    -- luôn phải bằng nhau, tức là hai cột có thể lệch nhau; cùng lập
    -- luận đã dùng ở V14 khi từ chối `is_read` và ở V13 khi từ chối
    -- `status`.
    --
    -- Lời máy chủ tự nói ("đã đóng cuộc trò chuyện") vì thế vẫn mang
    -- sender_role = ADMIN của chính người đã bấm nút — truy được về một
    -- người thật — nhưng không tính vào số chưa đọc và không đi qua lớp
    -- kiểm tần suất.
    message_type varchar(20) not null,

    -- Văn bản thuần. Không có chỗ nào trong giao diện dựng nó thành
    -- HTML, nên nội dung người dùng gõ không mở được đường chèn mã —
    -- cùng chính sách với `notifications.title/message` ở V14.
    --
    -- Trần 4000 ký tự là trần *của lược đồ*; trần thật mà máy chủ áp
    -- nằm ở cấu hình (`app.support.max-message-length`, mặc định 2000)
    -- và luôn nhỏ hơn hoặc bằng con số này. Hai tầng vì chúng trả lời
    -- hai câu khác nhau: một cái là chính sách sản phẩm có thể siết lại
    -- bất cứ lúc nào, một cái là chốt chặn cuối không cho một lỗi ở tầng
    -- trên ghi được một hàng quá khổ.
    content varchar(4000) not null,

    -- Định danh do trình duyệt sinh ra cho *một lần bấm gửi*.
    --
    -- Đây là chỗ chống trùng của cả tính năng, và nó phải nằm ở tầng cơ
    -- sở dữ liệu: đường mạng đứt sau khi máy chủ đã ghi xong nhưng trước
    -- khi lời báo nhận về tới nơi là một chuyện *sẽ* xảy ra, và lần thử
    -- lại của trình duyệt mang đúng chuỗi này. Xem ràng buộc UNIQUE bên
    -- dưới.
    client_message_id varchar(64) not null,

    created_at datetime(6) not null,

    primary key (id)
) engine=InnoDB;

-- Tin nhắn thuộc về cuộc trò chuyện: xóa người đọc → xóa cuộc trò
-- chuyện → xóa tin. Một dây cascade, cùng chính sách với V14.
alter table support_messages
    add constraint fk_support_messages_conversation foreign key (conversation_id)
    references support_conversations (id) on delete cascade;

-- Cố ý *không* cascade và cũng không SET NULL: cột này phải NOT NULL để
-- ràng buộc UNIQUE bên dưới luôn có hiệu lực (MySQL coi mỗi NULL là một
-- giá trị khác nhau, nên một cột nullable sẽ để lọt đúng những hàng cần
-- chặn nhất). Ứng dụng không xóa tài khoản, nên RESTRICT ở đây không
-- chặn đường nào đang có. Ngày nào thêm đường xóa tài khoản thì đây là
-- một trong những chỗ phải quyết định lại, và nó sẽ báo bằng một lỗi
-- khóa ngoại chứ không im lặng.
alter table support_messages
    add constraint fk_support_messages_sender foreign key (sender_id)
    references users (id);

-- Ràng buộc quan trọng nhất của lần migration này.
--
-- Nó biến "một lần bấm gửi = một tin nhắn" từ một phép kiểm trong Java —
-- mà hai request song song đều vượt qua được — thành một quy tắc cơ sở
-- dữ liệu tự giữ. Tầng service vẫn hỏi trước, nhưng chỉ để lần thử lại
-- thứ hai trả về đúng tin đã ghi (ACK "DUPLICATE") thay vì một lỗi.
--
-- Có `sender_id` trong khóa chứ không chỉ (conversation_id,
-- client_message_id): hai bên của một cuộc trò chuyện sinh định danh độc
-- lập nhau, nên một lần trùng ngẫu nhiên giữa hai bên không được phép
-- nuốt mất câu trả lời của quản trị viên.
alter table support_messages
    add constraint uk_support_messages_client unique (conversation_id, sender_id, client_message_id);

-- ----- Chỉ mục -----
--
-- Hai chiều đọc, hai chỉ mục, không thừa cái nào.
--
-- Chiều thứ nhất: lịch sử một cuộc trò chuyện, phân trang bằng con trỏ
-- `id` (`id < :before` để cuộn lên, `id > :after` để lấy phần bỏ lỡ sau
-- khi nối lại). Cả hai đều là một lần dò rồi quét theo thứ tự chỉ mục.
create index idx_support_messages_conversation on support_messages (conversation_id, id);

-- Chiều thứ hai: đếm số chưa đọc của một phía — "bao nhiêu tin TEXT của
-- bên kia có id lớn hơn mốc của tôi". Con số này được hỏi ở mỗi lần mở
-- hộp thư và mỗi dòng trong danh sách quản trị, nên nó phải là một lần
-- dò chỉ mục chứ không phải một lần quét rồi lọc.
--
-- `message_type` không nằm trong khóa: nó chỉ có hai giá trị và gần như
-- mọi hàng đều là TEXT, nên nó không chia nhỏ được gì mà chỉ làm chỉ mục
-- dày thêm. Nó ở lại dưới dạng một điều kiện lọc trên số hàng đã hẹp.
create index idx_support_messages_unread on support_messages (conversation_id, sender_role, id);

-- Không có tác vụ tự xóa tin nhắn cũ, và đó là chủ ý — cùng lập luận với
-- V14. Dữ liệu này thuộc về người đọc và về hồ sơ hỗ trợ, không phải rác
-- của hệ thống. Cũng không có cột `deleted_at`: sửa và xóa tin nhắn
-- không nằm trong phạm vi tính năng, nên một cột không ai ghi vào chỉ là
-- một lời hứa suông trong lược đồ.
