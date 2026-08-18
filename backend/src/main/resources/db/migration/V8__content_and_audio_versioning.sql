-- =====================================================================
-- V8 — Phiên bản nội dung chương, và bản audio thuộc về phiên bản nào.
--
-- <h3>Vấn đề</h3>
-- Người đọc mở chương 10, bấm "Nghe bằng AI". Trong lúc dựng, Admin sửa
-- nội dung chương ấy. Bản audio dựng xong vẫn được bật cờ READY và vẫn
-- được trả về như audio hiện tại của chương — kể cả sau khi tải lại
-- trang. Người đọc nghe một đằng, đọc một nẻo, và không có gì trên màn
-- hình nói cho họ biết điều đó.
--
-- <h3>Vì sao content_hash chưa đủ</h3>
-- Cột `content_hash` đã có từ V1 và đúng là dấu vân tay của nội dung lúc
-- dựng. Nhưng nó chỉ được đọc ở đúng một chỗ: lúc bấm nút, để quyết định
-- có dùng lại bản cũ hay không. Không đường phát nào hỏi tới nó, nên một
-- bản đã lỗi thời vẫn phát bình thường. Và nó không trả lời được câu
-- "chương này đang ở phiên bản nào" — câu mà cả trình duyệt lẫn luồng nền
-- đều cần hỏi.
--
-- Nên hai cột dưới đây có hai vai khác nhau, không thay thế nhau:
--
--   content_version  là phiên bản nghiệp vụ — thứ tự, so sánh được,
--                    là thứ frontend theo dõi và là source of truth
--   content_hash     là kiểm tra toàn vẹn — dùng để *chứng minh* một bản
--                    audio cũ có thật sự khớp nội dung hay không
--
-- Chính vai thứ hai làm cho lần migration này không phải đoán mò.
-- =====================================================================

-- ----- Phiên bản nội dung của chương -----
--
-- Mọi chương đang có đều bắt đầu ở phiên bản 1. Không chương nào đổi hành
-- vi vì lần migration này: bản audio nào đang khớp nội dung thì vẫn khớp
-- (xem phần chứng minh bên dưới).
alter table chapters
    add column content_version integer not null default 1;

-- ----- Chốt ghi đồng thời của hai Admin -----
--
-- Cột của Hibernate cho optimistic locking. Không có nó, hai lần lưu chạy
-- song song đều đọc content_version = 7 và đều ghi 8 — để lại *hai nội
-- dung khác nhau cùng mang nhãn v8*, tức là con số phiên bản không còn xác
-- định được nội dung nữa, và mọi thứ dựng trên nó đều sai theo.
--
-- Client không phải gửi gì thêm: Hibernate so cột này với giá trị nó đã
-- đọc lúc nạp entity, nên lần ghi thứ hai hỏng ngay ở tầng cơ sở dữ liệu.
-- Chốt này chỉ chặn hai giao dịch chồng nhau, không chặn được một form mở
-- từ hôm qua rồi mới bấm lưu — đó là câu hỏi khác, cần client gửi kèm
-- phiên bản kỳ vọng, và không thuộc phạm vi lần này.
alter table chapters
    add column version bigint not null default 0;

-- ----- Bản audio được dựng từ phiên bản nào -----
--
-- Để null được, và null có nghĩa: "không biết bản này đọc theo nội dung
-- nào". Bản như vậy không bao giờ được coi là audio hiện tại của chương.
alter table audio_files
    add column content_version integer;

-- ----- STALE -----
--
-- Trạng thái thứ tư, cho bản audio dựng xong nhưng nội dung chương đã đi
-- tiếp. Cố ý không dùng lại FAILED: FAILED nghĩa là "dựng hỏng, thử lại
-- đi", còn STALE nghĩa là "dựng thành công, chỉ là cho một phiên bản
-- khác" — và file của nó vẫn phát được, vẫn cần giữ lại một thời gian cho
-- người đang nghe dở (xem V8 phần cuối và AudioRetentionSweeper).
alter table audio_files
    modify column status enum ('FAILED','PROCESSING','READY','STALE') not null;

-- ----- Câu hỏi "chương này có audio dùng được không" -----
--
-- Sau lần này, mọi đường phát đều lọc theo (chapter_id, content_version,
-- status). Danh sách chương của một truyện dài hỏi đúng câu ấy cho hai
-- trăm chương một lúc, nên nó cần một chỉ mục thay vì quét bảng.
create index idx_audio_chapter_version on audio_files (chapter_id, content_version, status);

-- =====================================================================
-- Dữ liệu đang có: chứng minh trước, gán sau.
--
-- Đây là dữ liệu thật đang chạy, nên không có lệnh nào kiểu
-- `update audio_files set content_version = 1` cho tất cả. Mỗi nhóm dưới
-- đây được gán dựa trên một bằng chứng riêng.
-- =====================================================================

-- (1) Bản TTS còn khớp nội dung — CHỨNG MINH ĐƯỢC.
--
-- content_hash được ghi bằng SHA-256 trên chuỗi UTF-8 của nội dung lúc
-- dựng (xem TtsService.hashContent). SHA2(...,256) của MySQL trên cột
-- utf8mb4 cho ra đúng chuỗi hex thường 64 ký tự ấy. Hash trùng nghĩa là
-- nội dung chương lúc này *đúng từng byte* với nội dung đã đem đi đọc.
-- Không phải suy đoán từ createdAt hay updatedAt — là đối chiếu nội dung.
--
-- COALESCE vì content để null được, và phía Java coi null là chuỗi rỗng.
update audio_files a
    join chapters c on c.id = a.chapter_id
set a.content_version = c.content_version
where a.source = 'TTS'
  and a.status = 'READY'
  and a.content_hash is not null
  and a.content_hash = sha2(coalesce(c.content, ''), 256);

-- (2) Bản TTS không khớp, hoặc không có gì để đối chiếu — KHÔNG chứng
--     minh được, nên không được gán phiên bản nào.
--
-- Gồm hai loại: bản dựng trước khi có cột content_hash (hash null), và
-- bản mà nội dung chương đã đổi kể từ lúc dựng (hash lệch). Cả hai đều
-- rơi vào đúng định nghĩa của STALE. File vẫn còn — không xóa gì ở một
-- lần migration — và sẽ được dọn theo hạn lưu giữ như mọi bản STALE khác.
update audio_files a
    join chapters c on c.id = a.chapter_id
set a.status = 'STALE'
where a.source = 'TTS'
  and a.status = 'READY'
  and a.content_version is null;

-- (3) Bản Admin tự thu — gán phiên bản 1, và đây là một GIẢ ĐỊNH được
--     ghi rõ chứ không phải một chứng minh.
--
-- Bản thu không có content_hash để đối chiếu: không ai băm giọng người ra
-- so với chữ được. Chọn gán phiên bản hiện tại vì kể từ lần migration này
-- trở đi, không nội dung chương nào đổi được mà không tăng phiên bản —
-- nên "phiên bản 1" đúng bằng định nghĩa là *nội dung chương tại thời
-- điểm V8 chạy*, và bản thu đang nằm đó là bản Admin đặt cho chương ở
-- trạng thái ấy.
--
-- Rủi ro còn lại: nếu Admin từng sửa chương sau khi tải bản thu lên (thời
-- trước khi có phiên bản), bản thu ấy vốn đã lệch và lần này không phát
-- hiện ra được. Cách duy nhất để phát hiện là nghe lại từng bản — nên nó
-- được nêu ra ở đây thay vì bị âm thầm bỏ qua.
update audio_files a
    join chapters c on c.id = a.chapter_id
set a.content_version = c.content_version
where a.source = 'UPLOAD'
  and a.status = 'READY';

-- (4) Bản FAILED và PROCESSING: không gán gì.
--
-- FAILED không có file để phục vụ ai. PROCESSING của lần chạy trước sẽ
-- được StaleGenerationReconciler chuyển sang FAILED ngay ở lần khởi động
-- này — gán phiên bản cho chúng chỉ là ghi một con số rồi xóa đi.
