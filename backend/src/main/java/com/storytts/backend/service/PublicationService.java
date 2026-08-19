package com.storytts.backend.service;

import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Publishable;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <h2>Ai được thấy thứ chưa đăng</h2>
 *
 * Câu trả lời ngắn: chỉ quản trị viên. Lớp này tồn tại để câu ấy được viết ra
 * đúng một lần, thay vì lặp lại ở mỗi truy vấn và mỗi service.
 *
 * <h3>404 chứ không phải 403</h3>
 * Một chương chưa đăng trả về "không tìm thấy", không phải "bạn không có
 * quyền". Khác biệt không phải chuyện lịch sự: 403 là một lời xác nhận rằng
 * chương ấy <i>có tồn tại</i>, và với một bản nháp thì chính sự tồn tại của nó
 * mới là thứ chưa được công bố. Dò số hiệu chương để biết tuần sau ra chương gì
 * là việc làm được nếu hai câu trả lời khác nhau.
 *
 * <p>Cùng lý lẽ ấy đã dùng ở {@code AudioService.requireOwnership}, chỗ bản
 * audio của người khác cũng trả về 404.
 *
 * <h3>Chương của một truyện chưa đăng cũng chưa đăng</h3>
 * Kể cả khi chính chương ấy đã tới giờ. Một chương lẻ không mở được nếu trang
 * truyện dẫn tới nó còn là bản nháp, và để hở chỗ này thì việc giấu cả truyện
 * chỉ còn là giấu cái mục lục.
 */
@Service
@RequiredArgsConstructor
public class PublicationService {

    private final CurrentUserService currentUserService;

    /** Quản trị viên thấy mọi thứ, kể cả bản nháp và bản đang chờ tới giờ. */
    public boolean canSeeUnpublished() {
        return currentUserService.currentPrincipal()
                .map(AppUserPrincipal::isAdmin)
                .orElse(false);
    }

    /** Người đang gọi có thấy được thứ này không. */
    public boolean isVisible(Publishable item) {
        return item.isPublished() || canSeeUnpublished();
    }

    /**
     * Chặn cứng: chưa đăng thì coi như không tồn tại.
     *
     * @param kind tên loại để dựng thông báo, ví dụ {@code "chương"}
     */
    public void requireVisible(Publishable item, String kind, Long id) {
        if (!isVisible(item)) {
            throw ResourceNotFoundException.of(kind, id);
        }
    }

    /**
     * Một chương, xét cả truyện chứa nó.
     *
     * <p>Gọi được ở những chỗ đã nạp chương kèm truyện — mọi đường vào nội dung
     * chương đều dùng {@code findDetailById}, thứ có sẵn {@code JOIN FETCH} sang
     * truyện, nên phép kiểm này không thêm câu truy vấn nào.
     */
    public void requireChapterVisible(Chapter chapter) {
        if (canSeeUnpublished()) {
            return;
        }
        if (!chapter.isPublished()
                || chapter.getStory() == null
                || !chapter.getStory().isPublished()) {
            throw ResourceNotFoundException.of("chương", chapter.getId());
        }
    }
}
