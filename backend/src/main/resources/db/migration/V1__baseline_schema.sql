-- =====================================================================
-- V1 — Toàn bộ lược đồ ban đầu.
--
-- Sinh ra từ chính cấu hình JPA của ứng dụng (script action = create,
-- dialect MySQL), không viết tay, để nó khớp từng cột với các entity. Từ
-- đây trở đi `ddl-auto` là `validate`: Hibernate chỉ đối chiếu và báo lỗi
-- ngay lúc khởi động nếu lược đồ lệch với entity, chứ không tự sửa bảng
-- sau lưng nữa.
--
-- Cơ sở dữ liệu đã có sẵn từ thời `ddl-auto=update` sẽ được Flyway đánh
-- dấu là đã ở V1 (baseline-on-migrate) và bỏ qua tệp này.
-- =====================================================================

-- ----- Danh mục -----
create table authors (
    id bigint not null auto_increment,
    name varchar(150) not null,
    bio TEXT,
    primary key (id)
) engine=InnoDB;

create table genres (
    id bigint not null auto_increment,
    name varchar(100) not null,
    description varchar(500),
    primary key (id)
) engine=InnoDB;

-- ----- Người dùng -----
create table users (
    id bigint not null auto_increment,
    username varchar(50) not null,
    email varchar(150) not null,
    password_hash varchar(100) not null,
    google_id varchar(64),
    role enum ('ADMIN','MEMBER') not null,
    -- VIP do Admin cấp tay; vip_until là VIP mua theo gói. isVip() là hoặc một trong hai.
    is_vip bit not null,
    vip_until datetime(6),
    display_name varchar(100),
    avatar_url varchar(500),
    enabled bit not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- Tài khoản chưa tạo, đang chờ nhập mã trong email. Chỉ giữ băm của mã.
create table pending_registrations (
    id bigint not null auto_increment,
    username varchar(50) not null,
    email varchar(150) not null,
    password_hash varchar(100) not null,
    display_name varchar(100),
    code_hash varchar(64) not null,
    expires_at datetime(6) not null,
    attempts integer not null,
    last_sent_at datetime(6) not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table password_reset_tokens (
    id bigint not null auto_increment,
    user_id bigint not null,
    token_hash varchar(64) not null,
    expires_at datetime(6) not null,
    used_at datetime(6),
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- ----- Nội dung -----
create table stories (
    id bigint not null auto_increment,
    title varchar(255) not null,
    author_id bigint,
    genre_id bigint,
    cover_image varchar(500),
    description TEXT,
    status enum ('COMPLETED','ONGOING') not null,
    view_count bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

create table chapters (
    id bigint not null auto_increment,
    story_id bigint not null,
    title varchar(255) not null,
    content LONGTEXT,
    chapter_number integer not null,
    -- Mức khóa chương — cơ chế trọng tâm của đề tài.
    access_level enum ('MEMBER','PUBLIC','VIP') not null,
    view_count bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

create table audio_files (
    id bigint not null auto_increment,
    chapter_id bigint not null,
    file_path varchar(500),
    source enum ('TTS','UPLOAD') not null,
    status enum ('FAILED','PROCESSING','READY') not null,
    duration_seconds integer,
    file_size bigint,
    content_type varchar(100),
    -- (chương, giọng, tốc độ) là khóa cache; content_hash phát hiện bản đọc theo chữ cũ.
    voice varchar(80),
    speed integer,
    content_hash varchar(64),
    provider varchar(40),
    error_message varchar(1000),
    -- Người đọc đã bấm "Nghe bằng AI"; null với bản upload và bản do Admin dựng.
    requested_by bigint,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

-- ----- Tương tác -----
create table reading_progress (
    id bigint not null auto_increment,
    user_id bigint not null,
    chapter_id bigint not null,
    last_position integer not null,
    audio_position_seconds integer not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table favorites (
    id bigint not null auto_increment,
    user_id bigint not null,
    story_id bigint not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table ratings_comments (
    id bigint not null auto_increment,
    user_id bigint not null,
    story_id bigint not null,
    rating integer,
    comment TEXT,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table view_events (
    id bigint not null auto_increment,
    story_id bigint not null,
    chapter_id bigint not null,
    user_id bigint,
    type enum ('LISTEN','READ') not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- ----- Gói VIP và đơn thanh toán -----
create table vip_plans (
    id bigint not null auto_increment,
    name varchar(120) not null,
    months integer not null,
    price_vnd bigint not null,
    description varchar(300),
    active bit not null,
    sort_order integer not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table vip_orders (
    id bigint not null auto_increment,
    order_code bigint not null,
    user_id bigint not null,
    plan_id bigint,
    plan_name varchar(120) not null,
    months integer not null,
    amount_vnd bigint not null,
    status enum ('CANCELLED','EXPIRED','PAID','PENDING') not null,
    payment_link_id varchar(100),
    checkout_url varchar(500),
    created_at datetime(6) not null,
    paid_at datetime(6),
    vip_until_after datetime(6),
    primary key (id)
) engine=InnoDB;

-- ----- Ràng buộc unique -----
alter table authors add constraint uk_authors_name unique (name);
alter table genres add constraint uk_genres_name unique (name);
alter table users add constraint uk_users_username unique (username);
alter table users add constraint uk_users_email unique (email);
alter table users add constraint uk_users_google_id unique (google_id);
alter table pending_registrations add constraint uk_pending_reg_email unique (email);
alter table password_reset_tokens add constraint uk_prt_token_hash unique (token_hash);
alter table chapters add constraint uk_chapters_story_number unique (story_id, chapter_number);
alter table reading_progress add constraint uk_progress_user_chapter unique (user_id, chapter_id);
alter table favorites add constraint uk_favorites_user_story unique (user_id, story_id);
alter table vip_orders add constraint uk_vip_orders_order_code unique (order_code);

-- ----- Index -----
create index idx_audio_chapter on audio_files (chapter_id);
create index idx_audio_requested_by on audio_files (requested_by, created_at);
create index idx_chapters_story on chapters (story_id);
create index idx_rc_story on ratings_comments (story_id);
create index idx_progress_user_updated on reading_progress (user_id, updated_at);
create index idx_stories_title on stories (title);
create index idx_stories_genre on stories (genre_id);
create index idx_stories_created_at on stories (created_at);
create index idx_view_events_created on view_events (created_at);
create index idx_view_events_story on view_events (story_id);
create index idx_vip_orders_user on vip_orders (user_id);
create index idx_vip_orders_status on vip_orders (status);

-- ----- Khóa ngoại -----
-- Hai tên băm (FKk3ndxg5..., FK10odm8..., FKp9h3o0...) là tên Hibernate tự sinh
-- cho những liên kết không đặt @ForeignKey. Giữ đúng tên đó ở đây, nếu không
-- `ddl-auto=validate` trên một cơ sở dữ liệu tạo mới sẽ khác với một cơ sở dữ
-- liệu cũ từng do Hibernate dựng.
alter table audio_files add constraint fk_audio_chapter foreign key (chapter_id) references chapters (id);
alter table audio_files add constraint fk_audio_requested_by foreign key (requested_by) references users (id);
alter table chapters add constraint fk_chapters_story foreign key (story_id) references stories (id);
alter table favorites add constraint fk_favorites_story foreign key (story_id) references stories (id);
alter table favorites add constraint fk_favorites_user foreign key (user_id) references users (id);
alter table password_reset_tokens add constraint FKk3ndxg5xp6v7wd4gjyusp15gq foreign key (user_id) references users (id);
alter table ratings_comments add constraint fk_rc_story foreign key (story_id) references stories (id);
alter table ratings_comments add constraint fk_rc_user foreign key (user_id) references users (id);
alter table reading_progress add constraint fk_progress_chapter foreign key (chapter_id) references chapters (id);
alter table reading_progress add constraint fk_progress_user foreign key (user_id) references users (id);
alter table stories add constraint fk_stories_author foreign key (author_id) references authors (id);
alter table stories add constraint fk_stories_genre foreign key (genre_id) references genres (id);
alter table vip_orders add constraint FK10odm87kk72oeaav810elfv87 foreign key (plan_id) references vip_plans (id);
alter table vip_orders add constraint FKp9h3o0ha5poojpcxe86k20mbv foreign key (user_id) references users (id);
