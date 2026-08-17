-- =====================================================================
-- V7 — `vip_orders` trở thành `payment_orders`.
--
-- <h3>Vì sao đổi thay vì thêm một bảng `coin_orders` bên cạnh</h3>
-- Từ nay có hai thứ mua được bằng tiền thật: gói VIP và gói Xu. Cả hai đi
-- qua đúng một cổng thanh toán, và — điểm quyết định — đúng một endpoint
-- webhook. PayOS gọi về với một `orderCode` và không nói gì thêm.
--
-- Hai bảng đơn nghĩa là webhook phải tra hai nơi, và nghĩa là cơ chế chống
-- cộng tiền hai lần phải được viết hai lần. Task 1 vừa cho thấy cơ chế ấy
-- tinh vi tới mức nào: nó là một câu UPDATE có điều kiện đóng vai
-- compare-and-set, và viết đúng nó ở bản sao thứ nhất rồi viết sai ở bản
-- sao thứ hai là kết cục mặc định. Một bảng thì chỉ có một chỗ để viết
-- đúng.
--
-- Đơn đã có được giữ nguyên vẹn: RENAME giữ lại toàn bộ dòng, và mọi đơn
-- cũ nhận `kind = 'VIP_PLAN'` — đúng thứ chúng vốn là.
-- =====================================================================

rename table vip_orders to payment_orders;

-- `plan_name` giờ mang tên của bất cứ thứ gì được mua, không riêng gói VIP.
-- Vẫn là bản chụp tại thời điểm mua: gói đổi tên hay đổi giá về sau không
-- được phép làm đơn cũ kể lại một câu chuyện khác.
alter table payment_orders
    change column plan_name item_name varchar(120) not null;

-- Đơn nạp Xu không có số tháng nào.
alter table payment_orders
    modify column months integer null;

-- Mặc định chỉ để lấp cho những dòng đã có; bỏ ngay sau đó để không còn
-- đường nào chèn một đơn mà quên khai nó thuộc loại gì.
alter table payment_orders
    add column kind varchar(20) not null default 'VIP_PLAN';
alter table payment_orders
    alter column kind drop default;

-- ----- Phần riêng của đơn nạp Xu -----
alter table payment_orders
    add column coin_package_id bigint null;

-- Số Xu đã cộng, chép lại lúc thanh toán. Gói đổi giá về sau không làm
-- thay đổi con số người mua đã thực nhận.
alter table payment_orders
    add column coins_granted bigint null;

alter table payment_orders
    add constraint fk_payment_orders_coin_package
    foreign key (coin_package_id) references coin_packages (id);

-- ----- Đổi tên chỉ mục cho khớp bảng -----
-- Khóa ngoại do Hibernate tự đặt tên (FK10odm87..., FKp9h3o0...) được giữ
-- nguyên tên cũ: `ddl-auto=validate` không đối chiếu tên khóa ngoại, và
-- dựng lại chúng chỉ để đẹp tên là đổi một rủi ro thật lấy một lợi ích
-- thẩm mỹ.
alter table payment_orders rename index uk_vip_orders_order_code to uk_payment_orders_order_code;
alter table payment_orders rename index idx_vip_orders_user to idx_payment_orders_user;
alter table payment_orders rename index idx_vip_orders_status to idx_payment_orders_status;

-- Webhook tra đơn theo `order_code` (đã có chỉ mục unique). Chỉ mục này là
-- cho trang quản trị lọc đơn theo loại và trạng thái — "còn bao nhiêu đơn
-- nạp Xu đang treo" là câu hỏi không trả lời được bằng chỉ mục cũ.
create index idx_payment_orders_kind_status on payment_orders (kind, status);
