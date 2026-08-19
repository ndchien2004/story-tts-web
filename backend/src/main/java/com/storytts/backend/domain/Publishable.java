package com.storytts.backend.domain;

import java.time.Instant;

/**
 * Thứ có thể còn là bản nháp, đang chờ tới giờ, hoặc đã đăng.
 *
 * <h3>Một cột, ba trạng thái</h3>
 * <pre>
 *   null            → NHÁP: chỉ quản trị viên thấy
 *   mốc ở tương lai → HẸN GIỜ: chưa ai thấy, và sẽ tự hiện
 *   mốc đã qua      → ĐÃ ĐĂNG
 * </pre>
 *
 * <p>Cách khác là một cột trạng thái enum kèm một cột mốc thời gian riêng. Bỏ
 * nó vì hai cột ấy mâu thuẫn nhau được — trạng thái NHÁP kèm một mốc đã qua thì
 * tin cột nào? — còn một cột thì không có trạng thái nào bất khả thi.
 *
 * <h3>"Đến giờ tự đăng" không cần một tác vụ định kỳ nào</h3>
 * Nó là một phép so sánh với thời điểm hiện tại, được hỏi ngay lúc có người
 * đọc. Nhờ vậy không có cửa sổ sai (chương hẹn 20:00:00 hiện đúng 20:00:00, chứ
 * không phải "trong vòng một phút sau đó"), và không có lần chạy nào bị bỏ lỡ —
 * điều đáng kể trên một máy chủ ngủ sau 15 phút vắng khách, nơi một job hẹn giờ
 * đơn giản là không chạy.
 */
public interface Publishable {

    /** Mốc xuất bản; null nghĩa là bản nháp. */
    Instant getPublishedAt();

    /** Người đọc thường có thấy được không. */
    default boolean isPublished() {
        Instant at = getPublishedAt();
        return at != null && !at.isAfter(Instant.now());
    }

    /** Đã đặt lịch nhưng chưa tới giờ. */
    default boolean isScheduled() {
        Instant at = getPublishedAt();
        return at != null && at.isAfter(Instant.now());
    }

    /** Chưa đặt lịch, cũng chưa đăng. */
    default boolean isDraft() {
        return getPublishedAt() == null;
    }

    /** Tên trạng thái để hiện ở khu quản trị. */
    default String publishState() {
        if (isDraft()) {
            return "DRAFT";
        }
        return isScheduled() ? "SCHEDULED" : "PUBLISHED";
    }
}
