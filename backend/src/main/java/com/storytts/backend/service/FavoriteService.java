package com.storytts.backend.service;

import com.storytts.backend.domain.Favorite;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.interaction.FavoriteStatusDto;
import com.storytts.backend.dto.story.StorySummaryDto;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.FavoriteRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đánh dấu truyện yêu thích (mục 4.6 của đề bài). */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final FavoriteRepository favoriteRepository;
    private final ChapterRepository chapterRepository;
    // Repository chứ không phải StoryService: trang chi tiết truyện gọi ngược lại
    // service này để lấy trạng thái yêu thích, hai bên phụ thuộc nhau sẽ thành vòng.
    private final StoryRepository storyRepository;
    private final CurrentUserService currentUserService;

    /**
     * Trạng thái yêu thích của truyện với người đang gọi API.
     * Khách chưa đăng nhập vẫn xem được số lượt, chỉ là {@code favorite = false}.
     */
    @Transactional(readOnly = true)
    public FavoriteStatusDto status(Long storyId) {
        boolean favorite = currentUserService.currentUserId()
                .map(userId -> favoriteRepository.existsByUserIdAndStoryId(userId, storyId))
                .orElse(false);
        return new FavoriteStatusDto(favorite, favoriteRepository.countByStoryId(storyId));
    }

    /**
     * Bật/tắt yêu thích.
     * <p>
     * Một thao tác duy nhất thay vì hai endpoint thêm/xóa: nút trên giao diện cũng là
     * một nút bật tắt, nên hai bên khớp nhau và không có trạng thái trung gian nào để lệch.
     */
    @Transactional
    public FavoriteStatusDto toggle(Long storyId) {
        User user = currentUserService.requireCurrentUser();
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        boolean nowFavorite = favoriteRepository
                .findByUserIdAndStoryId(user.getId(), storyId)
                .map(existing -> {
                    favoriteRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    favoriteRepository.save(Favorite.builder().user(user).story(story).build());
                    return true;
                });

        return new FavoriteStatusDto(nowFavorite, favoriteRepository.countByStoryId(storyId));
    }

    /** Danh sách truyện đã đánh dấu, mới nhất trước. */
    @Transactional(readOnly = true)
    public PageResponse<StorySummaryDto> listMine(int page, int size) {
        Long userId = currentUserService.requireCurrentUser().getId();

        Page<Favorite> result = favoriteRepository.findByUser(
                userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));

        return PageResponse.from(result, favorite -> {
            Story story = favorite.getStory();
            return StorySummaryDto.from(story, chapterRepository.countByStoryId(story.getId()));
        });
    }
}
