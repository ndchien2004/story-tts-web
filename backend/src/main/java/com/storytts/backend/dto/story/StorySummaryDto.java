package com.storytts.backend.dto.story;

import com.storytts.backend.domain.Story;
import com.storytts.backend.dto.catalog.AuthorDto;
import com.storytts.backend.dto.catalog.GenreDto;

import java.time.Instant;

/** Dữ liệu hiển thị trong danh sách truyện (không kèm nội dung chương). */
public record StorySummaryDto(
        Long id,
        String title,
        AuthorDto author,
        GenreDto genre,
        String coverImage,
        String description,
        String status,
        String statusLabel,
        long viewCount,
        long chapterCount,
        Instant createdAt,

        /** {@code DRAFT} / {@code SCHEDULED} / {@code PUBLISHED} — xem {@code Publishable}. */
        String publishState,

        /** Giờ đăng; null là bản nháp, mốc tương lai là đang chờ tới giờ. */
        Instant publishedAt
) {

    public static StorySummaryDto from(Story story, long chapterCount) {
        return new StorySummaryDto(
                story.getId(),
                story.getTitle(),
                AuthorDto.from(story.getAuthor()),
                GenreDto.from(story.getGenre()),
                story.getCoverImage(),
                story.getDescription(),
                story.getStatus().name(),
                story.getStatus().getLabel(),
                story.getViewCount(),
                chapterCount,
                story.getCreatedAt(),
                story.publishState(),
                story.getPublishedAt());
    }
}
