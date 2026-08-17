-- =====================================================================
-- V5 — Ví Xu: số dư, sổ cái, và bảng giá gói nạp.
--
-- Vì sao có hai bảng chứ không phải một cột `coins` trên `users`:
--
--   Một cột số dư trả lời được "còn bao nhiêu" nhưng không trả lời được
--   "vì sao còn bấy nhiêu". Khi người dùng báo mất Xu — mà chuyện ấy sẽ
--   xảy ra — không có cột nào để tra. Sổ cái là thứ tra được.
--
--   Ngược lại, chỉ có sổ cái mà không có cột số dư thì mỗi lần mua một
--   chương phải cộng lại toàn bộ lịch sử của người đó. Nên giữ cả hai:
--   `wallets.balance` là thứ quyết định có đủ tiền hay không,
--   `wallet_transactions` là thứ giải thích nó.
--
--   Hai thứ ấy luôn được ghi trong cùng một giao dịch, và mỗi dòng sổ cái
--   mang theo số dư trước/sau, nên nếu chúng có lệch nhau thì tìm ra chỗ
--   lệch là một câu truy vấn chứ không phải một cuộc điều tra.
-- =====================================================================

-- ----- Ví -----
-- Một người một ví. Tách khỏi `users` chứ không thêm cột, vì số dư bị ghi
-- bởi những câu UPDATE có điều kiện chạy đồng thời với nhau; để nó ở
-- `users` là mỗi lần mua một chương lại khóa đúng cái dòng mà mọi thứ
-- khác trong hệ thống đều đang đọc.
create table wallets (
    id bigint not null auto_increment,
    user_id bigint not null,
    -- Xu là số nguyên, không có nửa Xu. bigint vì gói nạp lớn nhất nhân
    -- với vài năm sử dụng vẫn phải nằm gọn trong kiểu này.
    balance bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

alter table wallets add constraint uk_wallets_user unique (user_id);
alter table wallets add constraint fk_wallets_user foreign key (user_id) references users (id);

-- Chốt chặn cuối cùng cho quy tắc "không bao giờ âm Xu". Mọi lệnh trừ đều
-- đã có điều kiện `balance >= :amount` trong mệnh đề WHERE, nên ràng buộc
-- này lẽ ra không bao giờ chạm tới. Nó ở đây phòng đúng cái ngày có người
-- viết một câu UPDATE mới mà quên mất điều kiện ấy.
alter table wallets add constraint ck_wallets_balance_non_negative check (balance >= 0);

-- ----- Sổ cái -----
create table wallet_transactions (
    id bigint not null auto_increment,
    user_id bigint not null,

    -- Vì sao dòng này tồn tại: nạp tiền, mua chương, hay admin chỉnh tay.
    type varchar(30) not null,

    -- Có dấu: âm là trừ, dương là cộng. Nhờ vậy bất biến của cả hệ thống
    -- viết được thành một dòng — SUM(amount) của một người phải bằng
    -- balance của người ấy — thay vì phải rẽ nhánh theo `type` mới biết
    -- nên cộng hay trừ.
    amount bigint not null,

    -- Số dư ngay trước và ngay sau dòng này. Dư thừa về mặt dữ liệu, và cố
    -- ý dư thừa: nó biến việc dò một khoản lệch thành việc tìm dòng đầu
    -- tiên có balance_before khác balance_after của dòng trước nó.
    balance_before bigint not null,
    balance_after bigint not null,

    -- Trỏ tới thứ đã gây ra dòng này: đơn thanh toán nào, chương nào.
    -- Không dùng khóa ngoại vì đích đến khác bảng tùy theo reference_type,
    -- và một dòng sổ cái phải sống sót qua việc chương bị xóa — lịch sử
    -- tiền bạc không được biến mất theo nội dung.
    reference_type varchar(30),
    reference_id bigint,

    -- Câu người dùng đọc được trong trang lịch sử giao dịch.
    description varchar(255),

    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

alter table wallet_transactions add constraint fk_wallet_tx_user foreign key (user_id) references users (id);

-- Trang "lịch sử giao dịch" luôn hỏi theo một người, mới nhất trước.
create index idx_wallet_tx_user_created on wallet_transactions (user_id, created_at);

-- Tra ngược từ một đơn thanh toán hoặc một chương về dòng sổ cái của nó —
-- đường đi của mọi cuộc điều tra "tiền này đi đâu".
create index idx_wallet_tx_reference on wallet_transactions (reference_type, reference_id);

-- ----- Bảng giá gói nạp -----
-- Cùng hình dạng với `vip_plans`, và cùng lý do: giá là dữ liệu, không
-- phải mã nguồn. Thêm gói "200.000đ" không được đòi hỏi một lần build lại.
create table coin_packages (
    id bigint not null auto_increment,
    name varchar(120) not null,
    price_vnd bigint not null,

    -- Số Xu cơ bản của gói.
    coins bigint not null,

    -- Xu tặng thêm, tách riêng để giao diện nói được "500 + 50 tặng" thay
    -- vì chỉ một con số 550 không giải thích được. Người mua nhận
    -- coins + bonus_coins.
    bonus_coins bigint not null default 0,

    description varchar(300),
    -- Gói tắt vẫn giữ nguyên trong lịch sử đơn, chỉ không bán nữa.
    active bit not null default 1,
    sort_order integer not null default 0,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create index idx_coin_packages_active_order on coin_packages (active, sort_order);
