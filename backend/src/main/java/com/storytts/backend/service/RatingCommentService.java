package com.storytts.backend.service;

import com.storytts.backend.domain.RatingComment;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.admin.AdminCommentDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.interaction.CommentDto;
import com.storytts.backend.dto.interaction.CommentRequest;
import com.storytts.backend.dto.interaction.RatingSummaryDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.RatingCommentRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đánh giá sao và bình luận theo truyện (mục 4.6 của đề bài). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCommentService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RatingCommentRepository ratingCommentRepository;
    // Repository chứ không phải StoryService, vì trang chi tiết truyện gọi ngược lại
    // service này để lấy điểm trung bình — hai bên phụ thuộc nhau sẽ thành vòng.
    private final StoryRepository storyRepository;
    private final CurrentUserService currentUserService;

    /**
     * Bình luận của một truyện, mới nhất trước.
     * Khách vẫn đọc được; cờ {@code mine} chỉ bật khi có người đăng nhập.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentDto> list(Long storyId, int page, int size) {
        Long currentUserId = currentUserService.currentUserId().orElse(null);

        Page<RatingComment> result = ratingCommentRepository.findByStory(
                storyId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));

        return PageResponse.from(result, entity -> CommentDto.from(entity, currentUserId));
    }

    /**
     * Mọi bình luận của mọi truyện — dành cho màn hình kiểm duyệt.
     *
     * <p>Không có cờ {@code mine}: ở đây Admin xóa được bình luận của bất kỳ ai, nên
     * việc bình luận đó có phải của mình hay không chẳng thay đổi điều gì.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminCommentDto> listAll(String keyword, Long storyId, int page, int size) {
        Page<RatingComment> result = ratingCommentRepository.search(
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                storyId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));

        return PageResponse.from(result, AdminCommentDto::from);
    }

    @Transactional(readOnly = true)
    public RatingSummaryDto summary(Long storyId) {
        Double average = ratingCommentRepository.averageRating(storyId);
        if (average == null) {
            return RatingSummaryDto.empty();
        }
        // Làm tròn một chữ số để hiển thị, tránh 4.333333 rơi thẳng ra giao diện.
        double rounded = Math.round(average * 10) / 10.0;
        return new RatingSummaryDto(rounded, ratingCommentRepository.countRatings(storyId));
    }

    /**
     * Gửi đánh giá và/hoặc bình luận.
     *
     * <p>Mỗi lần gửi là một bản ghi riêng, giữ nguyên thứ tự thời gian của cuộc trò chuyện
     * dưới truyện; điểm trung bình vì thế tính trên mọi lượt chấm chứ không chỉ lượt cuối.
     */
    @Transactional
    public CommentDto create(Long storyId, CommentRequest request) {
        User user = currentUserService.requireCurrentUser();
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        String comment = request.comment() == null ? null : request.comment().trim();
        boolean hasComment = comment != null && !comment.isBlank();

        if (request.rating() == null && !hasComment) {
            throw new BadRequestException("Hãy chấm sao hoặc viết bình luận trước khi gửi.");
        }

        RatingComment saved = ratingCommentRepository.save(RatingComment.builder()
                .user(user)
                .story(story)
                .rating(request.rating())
                .comment(hasComment ? comment : null)
                .build());

        return CommentDto.from(saved, user.getId());
    }

    /** Xóa bình luận: chỉ tác giả của nó, hoặc Admin khi cần kiểm duyệt. */
    @Transactional
    public void delete(Long commentId) {
        RatingComment entity = ratingCommentRepository.findById(commentId)
                .orElseThrow(() -> ResourceNotFoundException.of("bình luận", commentId));

        AppUserPrincipal principal = currentUserService.currentPrincipal()
                .orElseThrow(() -> new ResourceNotFoundException("Chưa đăng nhập."));

        boolean owner = entity.getUser().getId().equals(principal.getId());
        if (!owner && !principal.isAdmin()) {
            throw new AccessDeniedException("Bạn chỉ xóa được bình luận của chính mình.");
        }

        log.info("Xóa bình luận id={} bởi user id={}", commentId, principal.getId());
        ratingCommentRepository.delete(entity);
    }
}
