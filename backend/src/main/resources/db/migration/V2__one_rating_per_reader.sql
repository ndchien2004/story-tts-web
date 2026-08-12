-- =====================================================================
-- V2 — Dọn phần điểm đánh giá bị trùng.
--
-- Bảng `ratings_comments` giữ cả bình luận lẫn điểm trên cùng một dòng, và
-- trước đây mỗi lần gửi là một dòng mới — nên một người chấm sao mười lần
-- thì cả mười lượt đều vào điểm trung bình. Từ nay
-- `RatingCommentService.create` sửa điểm cũ thay vì cộng thêm một lượt,
-- nhưng dữ liệu đã lệch từ trước thì phải dọn ở đây, không thì điểm của
-- những truyện cũ vẫn sai.
--
-- Cách dọn: giữ lượt chấm MỚI NHẤT của mỗi người cho mỗi truyện, các lượt
-- cũ hơn bị bỏ điểm. Bình luận trong những dòng đó không bị xóa — chúng
-- vẫn là một phần của cuộc trò chuyện dưới truyện.
--
-- Không đặt được ràng buộc unique cho việc này: "một người một điểm" chỉ áp
-- cho dòng có điểm, mà MySQL không có unique index theo điều kiện. Quy tắc
-- vì thế nằm ở tầng service, và có test ghim lại.
-- =====================================================================

update ratings_comments rc
join (
    select user_id, story_id, max(id) as keep_id
    from ratings_comments
    where rating is not null
    group by user_id, story_id
) latest
  on latest.user_id = rc.user_id
 and latest.story_id = rc.story_id
set rc.rating = null
where rc.rating is not null
  and rc.id <> latest.keep_id;

-- Dòng nào giờ trống cả điểm lẫn bình luận thì không còn là gì cả.
delete from ratings_comments
where rating is null
  and (comment is null or comment = '');
