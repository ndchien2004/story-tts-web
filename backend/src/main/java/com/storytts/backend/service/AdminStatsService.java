package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.domain.ViewType;
import com.storytts.backend.dto.admin.AdminChartsDto;
import com.storytts.backend.dto.admin.AdminStatsDto;
import com.storytts.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Số liệu tổng quan cho trang chủ của bảng quản trị. */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final AudioFileRepository audioFileRepository;
    private final UserRepository userRepository;
    private final RatingCommentRepository ratingCommentRepository;
    private final FavoriteRepository favoriteRepository;
    private final GenreRepository genreRepository;
    private final AuthorRepository authorRepository;
    private final ViewEventRepository viewEventRepository;

    /**
     * Dữ liệu cho ba biểu đồ, tính trong cửa sổ {@code days} ngày gần nhất.
     *
     * <p>Các ngày không có lượt nào vẫn phải xuất hiện với giá trị 0. Nếu bỏ trống, biểu
     * đồ đường sẽ nối thẳng từ ngày có dữ liệu này sang ngày có dữ liệu kia và trông như
     * thể quãng giữa vẫn đều đặn có người đọc.
     */
    @Transactional(readOnly = true)
    public AdminChartsDto charts(int days) {
        int window = Math.min(Math.max(days, 1), 90);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(window - 1L);

        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Object[] row : viewEventRepository.findTimestampsSince(from.atStartOfDay(zone).toInstant())) {
            LocalDate day = ((Instant) row[0]).atZone(zone).toLocalDate();
            long[] counters = byDay.computeIfAbsent(day, key -> new long[2]);
            if (row[1] == ViewType.READ) {
                counters[0]++;
            } else {
                counters[1]++;
            }
        }

        List<AdminChartsDto.DailyPoint> perDay = new ArrayList<>(window);
        for (int offset = 0; offset < window; offset++) {
            LocalDate day = from.plusDays(offset);
            long[] counters = byDay.getOrDefault(day, new long[2]);
            perDay.add(new AdminChartsDto.DailyPoint(day.toString(), counters[0], counters[1]));
        }

        List<AdminChartsDto.StoryViews> topStories = storyRepository
                .findTopByViewCount(PageRequest.of(0, 8)).stream()
                .map(story -> new AdminChartsDto.StoryViews(
                        story.getId(), story.getTitle(), story.getViewCount()))
                .toList();

        List<AdminChartsDto.AccessLevelSlice> accessLevels = List.of(
                new AdminChartsDto.AccessLevelSlice("PUBLIC", "Công khai",
                        chapterRepository.countByAccessLevel(AccessLevel.PUBLIC)),
                new AdminChartsDto.AccessLevelSlice("MEMBER", "Yêu cầu đăng nhập",
                        chapterRepository.countByAccessLevel(AccessLevel.MEMBER)),
                new AdminChartsDto.AccessLevelSlice("VIP", "VIP",
                        chapterRepository.countByAccessLevel(AccessLevel.VIP)));

        return new AdminChartsDto(perDay, topStories, accessLevels);
    }

    @Transactional(readOnly = true)
    public AdminStatsDto overview() {
        long stories = storyRepository.count();

        return new AdminStatsDto(
                stories,
                storyRepository.countByStatus(StoryStatus.ONGOING),
                storyRepository.countByStatus(StoryStatus.COMPLETED),

                chapterRepository.count(),
                chapterRepository.countByAccessLevel(AccessLevel.PUBLIC),
                chapterRepository.countByAccessLevel(AccessLevel.MEMBER),
                chapterRepository.countByAccessLevel(AccessLevel.VIP),

                audioFileRepository.countChaptersWithReadyAudio(),
                audioFileRepository.countBySource(AudioSource.TTS),
                audioFileRepository.countBySource(AudioSource.UPLOAD),
                audioFileRepository.countByStatus(AudioStatus.PROCESSING),
                audioFileRepository.countByStatus(AudioStatus.FAILED),

                userRepository.count(),
                userRepository.countVipUsers(),
                userRepository.countByEnabledFalse(),
                userRepository.countByRole(Role.ADMIN),

                ratingCommentRepository.count(),
                favoriteRepository.count(),
                genreRepository.count(),
                authorRepository.count());
    }
}
