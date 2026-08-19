package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.ReadingProgress;
import com.storytts.backend.domain.Story;
import com.storytts.backend.dto.chapter.ChapterAccessDto;
import com.storytts.backend.dto.chapter.ChapterDetailDto;
import com.storytts.backend.dto.chapter.ChapterRequest;
import com.storytts.backend.dto.chapter.ChapterSummaryDto;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.ReadingProgressRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.service.realtime.ChapterContentUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Nghiệp vụ chương truyện.
 * Mọi đường vào nội dung chương đều gọi {@link ChapterAccessService} trước.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final StoryRepository storyRepository;
    private final AudioFileRepository audioFileRepository;
    private final ChapterAccessService chapterAccessService;

    // Đọc thẳng repository chứ không qua ReadingProgressService: lớp đó đã phụ
    // thuộc vào ChapterService, gọi ngược lại sẽ thành vòng phụ thuộc bean.
    private final ReadingProgressRepository progressRepository;
    private final CurrentUserService currentUserService;
    private final PublicationService publicationService;
    private final ApplicationEventPublisher eventPublisher;

    /** Số chương mặc định mỗi trang — đủ rộng cho gần hết truyện, đủ hẹp cho truyện dài. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** Trần cứng: một truyện 1.200 chương không được phép về trong một lần gọi. */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Một trang chương của một truyện.
     *
     * <p>Ai cũng xem được danh sách, nhưng chương không đủ quyền sẽ có
     * {@code locked = true} để frontend hiển thị icon 🔒 kèm mức yêu cầu. Chương
     * chưa đăng thì không có mặt ở đây — với người đọc thường, nó chưa tồn tại.
     *
     * @param keyword tìm trong tiêu đề, hoặc một con số để nhảy tới chương ấy
     * @param asc     thứ tự chương; false là mới nhất trước
     */
    @Transactional(readOnly = true)
    public PageResponse<ChapterSummaryDto> listByStory(Long storyId, String keyword,
                                                       boolean asc, int page, int size) {
        if (!storyRepository.existsById(storyId)) {
            throw ResourceNotFoundException.of("truyện", storyId);
        }

        // "Chương 47" và "47" đều phải dẫn tới chương 47. Tách con số ra thành
        // một điều kiện riêng thay vì ép kiểu cột số sang chuỗi trong SQL: phép
        // ép ấy khác nhau giữa các hệ cơ sở dữ liệu và không dùng được chỉ mục.
        String trimmed = keyword == null ? "" : keyword.trim();
        Integer number = trimmed.matches("\\d{1,9}") ? Integer.valueOf(trimmed) : null;

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, "chapterNumber"));

        Page<Chapter> result = chapterRepository.findVisibleByStory(
                storyId, publicationService.canSeeUnpublished(), Instant.now(),
                number != null ? null : trimmed, number, pageable);

        if (result.isEmpty()) {
            return PageResponse.from(result, chapter -> toSummary(chapter, storyId, false, false));
        }

        List<Long> chapterIds = result.getContent().stream().map(Chapter::getId).toList();
        Set<Long> withAudio = new HashSet<>(audioFileRepository.findChapterIdsWithReadyAudio(chapterIds));

        // Một câu hỏi quyền cho cả trang. Hỏi từng dòng thì một trang trăm chương
        // là một trăm câu truy vấn cho một lần mở trang.
        Set<Long> owned = chapterAccessService.ownedAmong(chapterIds);

        return PageResponse.from(result, chapter -> toSummary(chapter, storyId,
                withAudio.contains(chapter.getId()), owned.contains(chapter.getId())));
    }

    /**
     * Quyền đọc một chương của người đang gọi, kèm giá và số dư.
     *
     * <p>Trang đọc hỏi cái này <i>trước</i> khi thử tải nội dung, để dựng đúng màn
     * hình ngay từ đầu thay vì gọi vào đường đọc rồi đọc mã lỗi trả về.
     */
    @Transactional(readOnly = true)
    public ChapterAccessDto accessInfo(Long chapterId) {
        Chapter chapter = findDetailEntity(chapterId);
        return ChapterAccessDto.of(
                chapterId,
                chapterAccessService.decide(chapter),
                chapter.getCoinPrice(),
                chapterAccessService.balanceOfCaller());
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
        chapterAccessService.requireAccess(chapter);

        Story story = chapter.getStory();

        // Mở chương ra chưa phải là đã đọc nó. Lượt đọc được tính khi người đọc
        // đọc hết chương và chuyển sang chương sau — xem
        // ReadingProgressService.markCompleted. Nhờ vậy F5 không còn cộng lượt,
        // và mỗi người chỉ được tính một lượt cho mỗi chương.
        boolean seesDrafts = publicationService.canSeeUnpublished();
        Instant now = Instant.now();

        Long previousId = chapterRepository
                .findPrevious(story.getId(), chapter.getChapterNumber(), seesDrafts, now)
                .map(Chapter::getId).orElse(null);
        Long nextId = chapterRepository
                .findNext(story.getId(), chapter.getChapterNumber(), seesDrafts, now)
                .map(Chapter::getId).orElse(null);

        Long viewerId = currentUserService.currentUserId().orElse(null);

        // Một câu truy vấn cho cả hai cờ, thay vì hai. Và câu ấy hỏi đúng thứ
        // trang đọc cần biết: bản audio *của phiên bản nội dung đang trả về ngay
        // dưới đây*. Hai lời gọi cũ chỉ hỏi "có bản READY nào không" — nên một
        // chương vừa được sửa vẫn hứa hẹn có audio, rồi trang đọc mở ra và không
        // tìm thấy gì để phát.
        List<AudioFile> current = audioFileRepository.findCurrentForChapter(chapterId, viewerId);
        boolean hasUpload = current.stream().anyMatch(
                audio -> audio.getSource() == AudioSource.UPLOAD && audio.getStatus() == AudioStatus.READY);
        boolean hasTts = current.stream().anyMatch(
                audio -> audio.getSource() == AudioSource.TTS && audio.getStatus() == AudioStatus.READY);

        // Khách không sinh câu truy vấn nào — id rỗng là dừng ngay tại đây.
        Integer audioPosition = viewerId == null
                ? null
                : progressRepository.findByUserIdAndChapterId(viewerId, chapterId)
                        .map(ReadingProgress::getAudioPositionSeconds)
                        .orElse(null);

        return new ChapterDetailDto(
                chapter.getId(),
                story.getId(),
                story.getTitle(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getContentVersion(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                chapter.getViewCount(),
                chapter.getCreatedAt(),
                previousId,
                nextId,
                hasUpload,
                hasTts,
                audioPosition,
                chapter.getCoinPrice(),
                chapter.publishState(),
                chapter.getPublishedAt());
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
                // Không gửi gì về việc xuất bản thì chương lên ngay, đúng hành
                // vi trước khi có bản nháp — một lời gọi API cũ không được phép
                // lặng lẽ đổi nghĩa.
                .publishedAt(request.resolvePublishedAt(null))
                .build();

        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin tạo chương {} của truyện {} với mức truy cập {}",
                number, storyId, request.accessLevel());
        return toSummary(saved, storyId, false);
    }

    /**
     * Admin lưu một chương.
     *
     * <h3>Ba việc phải xảy ra cùng nhau, hoặc không việc nào cả</h3>
     * Khi nội dung đổi: nội dung mới được ghi, phiên bản tăng, và mọi bản audio
     * đọc theo nội dung cũ bị đánh dấu lỗi thời. Cả ba nằm trong một giao dịch,
     * và đó là điều kiện để bất biến của cả tính năng đứng vững — hai trạng thái
     * lửng lơ dưới đây đều là trạng thái hỏng:
     *
     * <pre>
     *   nội dung mới + phiên bản cũ  → audio cũ trông như vẫn còn hợp lệ
     *   nội dung cũ  + phiên bản mới → audio còn tốt bị vứt đi vô cớ
     * </pre>
     *
     * <p>Giao dịch này vẫn ngắn: ba câu lệnh, không lời gọi mạng nào. Việc nặng
     * (dựng lại audio) không xảy ra ở đây và cũng không được phép xảy ra ở đây.
     *
     * <h3>Chỉ nội dung mới làm phiên bản tăng</h3>
     * Sửa tiêu đề hay đổi mức khóa thì những chữ đem đi đọc không đổi, nên bản
     * audio đang có vẫn đọc đúng chương. Tăng phiên bản trong trường hợp ấy chỉ
     * có tác dụng vứt một bản audio còn tốt và bắt hệ thống trả tiền dựng lại nó.
     */
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

        requirePriceableLevel(request.accessLevel(), chapter.getCoinPrice());

        // So sánh trước khi ghi đè — sau đó thì không còn gì để so nữa.
        boolean contentChanged = !Objects.equals(chapter.getContent(), request.content());

        chapter.setTitle(request.title().trim());
        chapter.setContent(request.content());
        chapter.setAccessLevel(request.accessLevel());
        chapter.setPublishedAt(request.resolvePublishedAt(chapter.getPublishedAt()));
        if (contentChanged) {
            chapter.setContentVersion(chapter.getContentVersion() + 1);
        }

        Chapter saved = chapterRepository.save(chapter);

        if (contentChanged) {
            int superseded = audioFileRepository.markSupersededStale(
                    chapterId, saved.getContentVersion(), Instant.now());
            log.info("Chương {} lên phiên bản {}; {} bản audio thành lỗi thời",
                    chapterId, saved.getContentVersion(), superseded);

            // Người nhận đăng ký ở AFTER_COMMIT, nên phát ở đây là an toàn: giao
            // dịch này hỏng thì không lời báo nào đi ra. Xem ChapterEventStream.
            eventPublisher.publishEvent(
                    new ChapterContentUpdated(chapterId, storyId, saved.getContentVersion()));
        }

        boolean hasAudio = audioFileRepository.hasCurrentReadyAudio(chapterId);
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
        requirePriceableLevel(accessLevel, chapter.getCoinPrice());
        chapter.setAccessLevel(accessLevel);
        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin đổi mức truy cập chương {} thành {}", chapterId, accessLevel);
        boolean hasAudio = audioFileRepository.hasCurrentReadyAudio(chapterId);
        return toSummary(saved, chapter.getStory().getId(), hasAudio);
    }

    /**
     * Đổi mức khóa cho nhiều chương cùng lúc.
     *
     * <p>Một giao dịch duy nhất: đặt mức khóa cho nửa danh sách rồi hỏng giữa chừng sẽ
     * để lại một truyện có chương khóa lẫn lộn, mà nhìn vào không đoán được là cố ý
     * hay do lỗi.
     *
     * @return số chương đã đổi
     */
    @Transactional
    public int changeAccessLevelBulk(List<Long> chapterIds, AccessLevel accessLevel) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            throw new BadRequestException("Chưa chọn chương nào.");
        }

        List<Chapter> chapters = chapterRepository.findAllById(chapterIds);
        if (chapters.size() != chapterIds.size()) {
            throw new ResourceNotFoundException("Có chương trong danh sách không còn tồn tại.");
        }

        chapters.forEach(chapter -> {
            requirePriceableLevel(accessLevel, chapter.getCoinPrice());
            chapter.setAccessLevel(accessLevel);
        });
        chapterRepository.saveAll(chapters);

        log.info("Admin đổi mức truy cập {} chương thành {}", chapters.size(), accessLevel);
        return chapters.size();
    }

    /**
     * Đặt giá Xu cho một chương. 0 nghĩa là ngừng bán lẻ.
     *
     * <p>Chương công khai không được phép có giá: {@code PUBLIC} nghĩa là ai cũng
     * đọc được, kể cả khách chưa đăng nhập, nên một cái giá gắn lên đó là hai câu
     * mâu thuẫn nhau trên cùng một chương. Chặn ở đây thay vì tự chọn một cách
     * diễn giải — cách nào cũng sẽ làm ai đó ngạc nhiên.
     */
    @Transactional
    public ChapterSummaryDto changeCoinPrice(Long chapterId, long coinPrice) {
        Chapter chapter = findDetailEntity(chapterId);
        requirePriceableLevel(chapter.getAccessLevel(), coinPrice);

        chapter.setCoinPrice(coinPrice);
        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin đặt giá chương {} thành {} Xu", chapterId, coinPrice);

        boolean hasAudio = audioFileRepository.hasCurrentReadyAudio(chapterId);
        return toSummary(saved, chapter.getStory().getId(), hasAudio);
    }

    /**
     * Đặt cùng một giá cho nhiều chương.
     *
     * <p>Một giao dịch duy nhất, cùng lý do với {@link #changeAccessLevelBulk}:
     * đặt giá cho nửa danh sách rồi hỏng giữa chừng để lại một truyện có giá lẫn
     * lộn mà nhìn vào không đoán được là cố ý hay do lỗi.
     *
     * @return số chương đã đổi
     */
    @Transactional
    public int changeCoinPriceBulk(List<Long> chapterIds, long coinPrice) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            throw new BadRequestException("Chưa chọn chương nào.");
        }

        List<Chapter> chapters = chapterRepository.findAllById(chapterIds);
        if (chapters.size() != chapterIds.size()) {
            throw new ResourceNotFoundException("Có chương trong danh sách không còn tồn tại.");
        }

        chapters.forEach(chapter -> {
            requirePriceableLevel(chapter.getAccessLevel(), coinPrice);
            chapter.setCoinPrice(coinPrice);
        });
        chapterRepository.saveAll(chapters);

        log.info("Admin đặt giá {} chương thành {} Xu", chapters.size(), coinPrice);
        return chapters.size();
    }

    private void requirePriceableLevel(AccessLevel level, long coinPrice) {
        if (coinPrice > 0 && level == AccessLevel.PUBLIC) {
            throw new BadRequestException(
                    "Chương công khai không đặt giá Xu được. Hãy đổi mức khóa sang "
                            + "“Yêu cầu đăng nhập” hoặc “VIP” trước khi đặt giá.");
        }
    }

    // ==================== Hàm hỗ trợ ====================

    /**
     * Nạp một chương kèm truyện của nó, sau khi đã xét chương ấy có tồn tại với
     * người đang gọi hay không.
     *
     * <p>Chỗ này là cửa vào chung của mọi đường chạm tới một chương cụ thể — đọc
     * chữ, dựng audio, hỏi trợ lý, sửa ở khu quản trị. Nên phép kiểm "đã đăng
     * chưa" đặt ở đây phủ được cả bốn mà không phải nhớ thêm ở đâu; nó lặp lại
     * một lần nữa trong {@code ChapterAccessService.requireAccess} vì đường phát
     * audio đi vào từ id của bản audio chứ không qua đây.
     */
    @Transactional(readOnly = true)
    public Chapter findDetailEntity(Long chapterId) {
        Chapter chapter = chapterRepository.findDetailById(chapterId)
                .orElseThrow(() -> ResourceNotFoundException.of("chương", chapterId));
        publicationService.requireChapterVisible(chapter);
        return chapter;
    }

    /** Dòng danh sách cho chương chưa cần biết tới quyền đã mua (chương mới tạo). */
    private ChapterSummaryDto toSummary(Chapter chapter, Long storyId, boolean hasAudio) {
        return toSummary(chapter, storyId, hasAudio, false);
    }

    private ChapterSummaryDto toSummary(Chapter chapter, Long storyId,
                                        boolean hasAudio, boolean owned) {
        ChapterAccessDecision decision = chapterAccessService.decide(chapter, owned);
        return new ChapterSummaryDto(
                chapter.getId(),
                storyId,
                chapter.getTitle(),
                chapter.getChapterNumber(),
                chapter.getAccessLevel().name(),
                chapter.getAccessLevel().getLabel(),
                !decision.allowed(),
                decision.purchasable(),
                chapter.getCoinPrice(),
                hasAudio,
                chapter.getViewCount(),
                chapter.getCreatedAt(),
                chapter.publishState(),
                chapter.getPublishedAt());
    }

    /**
     * Đổi riêng tình trạng xuất bản của một chương.
     *
     * <p>Tách khỏi {@link #update}: gỡ một chương xuống hay dời lịch đăng không
     * nên bắt người ta gửi lại toàn bộ nội dung chương — và với một chương dài
     * thì việc gửi lại ấy còn có nguy cơ ghi đè bằng một bản đã cũ trong form.
     */
    @Transactional
    public ChapterSummaryDto changePublication(Long chapterId, boolean draft, Instant publishedAt) {
        Chapter chapter = findDetailEntity(chapterId);
        chapter.setPublishedAt(
                draft ? null : (publishedAt != null ? publishedAt : Instant.now()));

        Chapter saved = chapterRepository.save(chapter);
        log.info("Admin đặt chương {} sang trạng thái {} (mốc {})",
                chapterId, saved.publishState(), saved.getPublishedAt());

        boolean hasAudio = audioFileRepository.hasCurrentReadyAudio(chapterId);
        return toSummary(saved, chapter.getStory().getId(), hasAudio);
    }
}
