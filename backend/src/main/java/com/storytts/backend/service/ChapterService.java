package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Story;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ chương truyện.
 * Mọi đường vào nội dung chương đều gọi {@link AccessControlService} trước.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final StoryRepository storyRepository;
    private final AudioFileRepository audioFileRepository;
    private final AccessControlService accessControlService;

    /**
     * Danh sách chương của một truyện.
     * Ai cũng xem được danh sách, nhưng chương không đủ quyền sẽ có {@code locked = true}
     * để frontend hiển thị icon 🔒 kèm mức yêu cầu.
     */
    @Transactional(readOnly = true)
    public List<ChapterSummaryDto> listByStory(Long storyId) {
        if (!storyRepository.existsById(storyId)) {
            throw ResourceNotFoundException.of("truyện", storyId);
        }
        List<Chapter> chapters = chapterRepository.findByStoryIdOrderByChapterNumberAsc(storyId);
        if (chapters.isEmpty()) {
            return List.of();
        }

        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();
        Set<Long> withAudio = new HashSet<>(audioFileRepository.findChapterIdsWithReadyAudio(chapterIds));

        return chapters.stream().map(chapter -> toSummary(chapter, storyId, withAudio.contains(chapter.getId()))).toList();
    }

    /**
     * Lấy nội dung đầy đủ của một chương.
     * Không đủ quyền → ném {@link com.storytts.backend.exception.ChapterLockedException} (HTTP 403)
     * và nội dung chương không bao giờ được đưa vào response.
     */
    @Transactional
    public ChapterDetailDto getDetail(Long chapterId) {
        Chapter chapter = findDetailEntity(chapterId);

        // >>> Chốt chặn quyền truy cập <<<
        accessControlService.requireAccess(chapter);

        chapterRepository.incrementViewCount(chapterId);

        Story story = chapter.getStory();
        Long previousId = chapterRepository
                .findPrevious(story.getId(), chapter.getChapterNumber())
                .map(Chapter::getId).orElse(null);
        Long nextId = chapterRepository
                .findNext(story.getId(), chapter.getChapterNumber())
                .map(Chapter::getId).orElse(null);

        boolean hasUpload = audioFileRepository
                .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.UPLOAD, AudioStatus.READY)
                .isPresent();
        boolean hasTts = audioFileRepository
                .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.TTS, AudioStatus.READY)
                .isPresent();

        return new ChapterDetailDto(
                chapter.getId(),
                story.getId(),
                story.getTitle(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                chapter.getViewCount() + 1,
                chapter.getCreatedAt(),
                previousId,
                nextId,
                hasUpload,
                hasTts);
    }

    // ==================== Phía Admin ====================

    @Transactional
    public ChapterSummaryDto create(Long storyId, ChapterRequest request) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ResourceNotFoundException.of("truyện", storyId));

        int number = request.chapterNumber() != null
                ? request.chapterNumber()
                : chapterRepository.findMaxChapterNumber(storyId) + 1;

        if (chapterRepository.existsByStoryIdAndChapterNumber(storyId, number)) {
            throw new BadRequestException("Truyện này đã có chương số %d.".formatted(number));
        }

        Chapter chapter = Chapter.builder()
                .story(story)
                .title(request.title().trim())
                .content(request.content())
                .chapterNumber(number)
                // Mức khóa chương do Admin quyết định.
                .accessLevel(request.accessLevel())
                .build();

        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin tạo chương {} của truyện {} với mức truy cập {}",
                number, storyId, request.accessLevel());
        return toSummary(saved, storyId, false);
    }

    @Transactional
    public ChapterSummaryDto update(Long chapterId, ChapterRequest request) {
        Chapter chapter = findDetailEntity(chapterId);
        Long storyId = chapter.getStory().getId();

        if (request.chapterNumber() != null
                && !request.chapterNumber().equals(chapter.getChapterNumber())) {
            if (chapterRepository.existsByStoryIdAndChapterNumber(storyId, request.chapterNumber())) {
                throw new BadRequestException(
                        "Truyện này đã có chương số %d.".formatted(request.chapterNumber()));
            }
            chapter.setChapterNumber(request.chapterNumber());
        }

        chapter.setTitle(request.title().trim());
        chapter.setContent(request.content());
        chapter.setAccessLevel(request.accessLevel());

        Chapter saved = chapterRepository.save(chapter);
        boolean hasAudio = audioFileRepository.existsByChapterIdAndStatus(chapterId, AudioStatus.READY);
        return toSummary(saved, storyId, hasAudio);
    }

    @Transactional
    public void delete(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> ResourceNotFoundException.of("chương", chapterId));
        chapterRepository.delete(chapter);
    }

    /** Đổi riêng mức khóa của một chương — thao tác nhanh trong trang quản trị. */
    @Transactional
    public ChapterSummaryDto changeAccessLevel(Long chapterId, AccessLevel accessLevel) {
        Chapter chapter = findDetailEntity(chapterId);
        chapter.setAccessLevel(accessLevel);
        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin đổi mức truy cập chương {} thành {}", chapterId, accessLevel);
        boolean hasAudio = audioFileRepository.existsByChapterIdAndStatus(chapterId, AudioStatus.READY);
        return toSummary(saved, chapter.getStory().getId(), hasAudio);
    }

    // ==================== Hàm hỗ trợ ====================

    @Transactional(readOnly = true)
    public Chapter findDetailEntity(Long chapterId) {
        return chapterRepository.findDetailById(chapterId)
                .orElseThrow(() -> ResourceNotFoundException.of("chương", chapterId));
    }

    private ChapterSummaryDto toSummary(Chapter chapter, Long storyId, boolean hasAudio) {
        boolean locked = !accessControlService.canAccess(chapter);
        return new ChapterSummaryDto(
                chapter.getId(),
                storyId,
                chapter.getTitle(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                locked,
                hasAudio,
                chapter.getViewCount(),
                chapter.getCreatedAt());
    }
}
