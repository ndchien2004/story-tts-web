-- =====================================================================
-- V3 — Mốc thời gian từng chữ của bản đọc.
--
-- Trang đọc tô sáng theo giọng đọc thì phải biết chữ nào rơi vào giây nào.
-- ElevenLabs trả về điều đó ngay trong lần tổng hợp (đường
-- /with-timestamps, cùng một khoản tiền), nên chỗ duy nhất còn thiếu là
-- một chỗ để cất.
--
-- Vì sao là bảng riêng chứ không phải một cột trên audio_files: một chương
-- hai mươi nghìn ký tự ra chừng ba trăm KB JSON, mà Hibernate nạp mọi cột
-- thường của một hàng ngay khi nạp hàng ấy. Để chung thì mỗi lần liệt kê
-- bản audio của một chương — việc xảy ra mỗi lần mở chương — là một lần
-- kéo cả mảng số ấy từ cơ sở dữ liệu về rồi vứt đi. Tách ra thì nó chỉ
-- được đọc bởi đúng cái endpoint hỏi tới nó.
--
-- Cột đếm chữ thì vẫn nằm lại bên audio_files, vì đó là thứ danh sách track
-- cần biết ("bản này tô sáng được không") và nó chỉ là một số nguyên.
--
-- ON DELETE CASCADE chứ không dọn bằng tay: bản đọc mà tách khỏi file audio
-- sinh ra nó thì không còn nghĩa gì, và có tới ba đường xóa audio trong mã
-- nguồn (admin xóa, cache hỏng bị dựng lại, dọn bản tạm cuối phiên). Giao
-- cho cơ sở dữ liệu là cách duy nhất không phải nhớ cả ba.
-- =====================================================================

alter table audio_files add column transcript_words integer;

create table audio_transcripts (
    audio_id bigint not null,
    words_json LONGTEXT not null,
    created_at datetime(6) not null,
    primary key (audio_id)
) engine=InnoDB;

alter table audio_transcripts
    add constraint fk_transcript_audio
    foreign key (audio_id) references audio_files (id)
    on delete cascade;
