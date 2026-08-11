package com.storytts.backend.service;

import com.storytts.backend.domain.ViewEvent;
import com.storytts.backend.domain.ViewType;
import com.storytts.backend.repository.ViewEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi lại từng lượt đọc/nghe để dựng biểu đồ theo ngày (mục 4.7 của đề bài).
 *
 * <p>Việc ghi log <b>không bao giờ được làm hỏng thao tác chính</b>: người đọc mở một
 * chương thì việc của họ là đọc được chương đó, còn thống kê chỉ là chuyện phụ. Vì thế
 * mọi lỗi ở đây đều bị nuốt và chỉ ghi cảnh báo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViewEventService {

    private final ViewEventRepository viewEventRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public void record(Long storyId, Long chapterId, ViewType type) {
        try {
            viewEventRepository.save(ViewEvent.builder()
                    .storyId(storyId)
                    .chapterId(chapterId)
                    // Null với Khách chưa đăng nhập — lượt đọc của họ vẫn phải được đếm.
                    .userId(currentUserService.currentUserId().orElse(null))
                    .type(type)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Không ghi được lượt {} cho chương {}: {}", type, chapterId, ex.getMessage());
        }
    }
}
