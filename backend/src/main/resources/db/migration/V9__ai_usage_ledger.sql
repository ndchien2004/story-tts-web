-- =====================================================================
-- V9 — Sổ đếm lượt dùng AI, tách khỏi những thứ AI tạo ra.
--
-- <h3>Lỗi được sửa ở đây</h3>
-- Hạn mức "Nghe bằng AI" trong ngày được tính bằng một phép đếm trên
-- chính bảng `audio_files`: bao nhiêu hàng mang tên người này, tạo từ 0
-- giờ sáng nay. Cùng lúc đó, `ReaderNarrationCleanup` xóa sạch những hàng
-- ấy mỗi lần người ta mở một phiên đăng nhập mới — vì bản audio người đọc
-- tự dựng chỉ sống trong một buổi đọc.
--
-- Hai việc đều đúng theo ý định của chúng, nhưng đứng cạnh nhau thì hạn
-- mức trở thành thứ tự nạp lại được: bấm hết ba lượt, đăng xuất, đăng
-- nhập, lại có ba lượt. Lặp không giới hạn. Trần chung của cả hệ thống
-- cũng tụt theo, vì nó đếm đúng tập hàng ấy.
--
-- <h3>Vì sao là một bảng riêng chứ không phải một cột</h3>
-- Gốc rễ là hai thứ có vòng đời khác hẳn nhau đang bị buộc vào một hàng:
--
--   bản audio  là TÀI SẢN — dựng ra, nghe, rồi dọn đi khi hết buổi
--   lượt dùng  là SỰ KIỆN — đã xảy ra thì không xóa được nữa
--
-- Xóa một tài sản là chuyện bình thường. Xóa một sự kiện đã xảy ra là
-- viết lại lịch sử, và đó chính là chỗ hỏng. Bảng này chỉ có đường ghi
-- thêm; không có nghiệp vụ nào xóa dòng của nó.
--
-- Cùng bảng phục vụ luôn trợ lý AI, thứ trước đây đếm bằng một HashMap
-- trong bộ nhớ — mất sạch mỗi lần máy chủ khởi động lại, mà gói miễn phí
-- của Render thì ngủ sau 15 phút vắng khách và tỉnh dậy là một tiến trình
-- mới. Hai hàng rào chi phí, một nguồn sự thật, và nguồn ấy nằm trên đĩa.
-- =====================================================================

create table ai_usage (
    id bigint not null auto_increment,

    -- Không cho null: một lượt không biết của ai thì không đếm lên ai
    -- được, và cả hai đường dùng AI đều đã bắt đăng nhập từ tầng URL.
    user_id bigint not null,

    -- TTS      = một bản audio mới do người đọc bấm dựng
    -- ASSISTANT = một câu hỏi gửi tới trợ lý
    kind enum ('TTS','ASSISTANT') not null,

    -- Để tra cứu về sau ("lượt này tiêu cho chương nào"), không tham gia
    -- vào phép đếm. Không đặt khóa ngoại: chương bị xóa thì lượt dùng vẫn
    -- đã xảy ra, và một sự kiện lịch sử không nên biến mất theo.
    chapter_id bigint,

    -- Bản audio mà lượt này đã trả tiền để dựng. Khóa ngoại có ON DELETE
    -- SET NULL, nên lúc bản audio bị dọn đi thì dòng sổ này ở lại — đúng
    -- điều mà cả lần migration này tồn tại để bảo đảm.
    audio_file_id bigint,

    -- Lượt được hoàn. Không xóa dòng, vì "đã hỏng nên trả lại lượt" là
    -- một sự kiện thứ hai chứ không phải bằng chứng rằng sự kiện thứ nhất
    -- chưa từng xảy ra. Phép đếm hạn mức bỏ qua những dòng có mốc này.
    refunded_at datetime(6),

    -- Lý do hoàn, để đọc log không phải đoán.
    refund_reason varchar(120),

    created_at datetime(6) not null,

    primary key (id)
) engine=InnoDB;

-- Câu truy vấn nóng của cả bảng: "người này đã dùng bao nhiêu lượt loại
-- này kể từ 0 giờ sáng nay". Chỉ mục đi theo đúng thứ tự các cột bị hỏi.
create index idx_ai_usage_user_kind_day on ai_usage (user_id, kind, created_at);

-- Trần chung của cả hệ thống: bỏ user_id ra khỏi đầu chỉ mục.
create index idx_ai_usage_kind_day on ai_usage (kind, created_at);

-- Đường tra ngược lúc hoàn lượt cho một bản dựng hỏng.
create index idx_ai_usage_audio on ai_usage (audio_file_id);

alter table ai_usage
    add constraint fk_ai_usage_user foreign key (user_id) references users (id);

alter table ai_usage
    add constraint fk_ai_usage_audio foreign key (audio_file_id)
        references audio_files (id) on delete set null;

-- =====================================================================
-- Dữ liệu đang có: chép lại những lượt còn đếm được.
--
-- Không chép thì ngày triển khai bản này, mọi người đang dùng dở hạn mức
-- đều được nạp đầy lại một lần — đúng cái lỗi vừa sửa, chỉ là xảy ra một
-- lần thay vì tùy ý.
--
-- Chỉ chép được những bản audio còn tồn tại, và đó là tất cả những gì
-- cách đếm cũ từng nhìn thấy — nên bảng mới bắt đầu với đúng thông tin mà
-- bảng cũ đang có, không nhiều hơn cũng không ít hơn.
--
-- Bản FAILED không chép: cách đếm cũ đã loại chúng ra ("dựng hỏng thì
-- hoàn lượt"), và cách đếm mới cũng loại, chỉ khác là bằng cột
-- refunded_at thay vì bằng trạng thái của bản audio.
-- =====================================================================
insert into ai_usage (user_id, kind, chapter_id, audio_file_id, created_at)
select a.requested_by, 'TTS', a.chapter_id, a.id, a.created_at
from audio_files a
where a.requested_by is not null
  and a.source = 'TTS'
  and a.status <> 'FAILED';
