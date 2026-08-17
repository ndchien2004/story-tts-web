package com.storytts.backend.service.importer;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Author;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Genre;
import com.storytts.backend.domain.Story;
import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AuthorRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.GenreRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ghi một gói nội dung vào cơ sở dữ liệu, mỗi chương là một giao dịch riêng.
 *
 * <h3>Vì sao mỗi chương một giao dịch</h3>
 * Gộp cả cuốn truyện vào một giao dịch nghe có vẻ gọn hơn, nhưng nó biến một
 * chương hỏng thành hai trăm chương không nhập được. Tách ra thì lượt chạy đi
 * hết tới cuối, và chạy lại sau khi sửa chỉ động tới đúng chương còn thiếu.
 *
 * <p>Điều đó đạt được bằng {@code @Transactional} thường, không cần
 * {@code REQUIRES_NEW}: {@link ContentImportRunner} gọi vào đây từ ngoài mọi
 * giao dịch, nên mỗi lời gọi vốn đã mở một giao dịch của riêng nó.
 * {@code REQUIRES_NEW} ở đây sẽ không thêm được gì mà lại tự cắt mình khỏi giao
 * dịch của bên gọi trong mọi bối cảnh khác — kể cả bối cảnh test, nơi nó commit
 * xuyên qua lượt cuộn ngược mà {@code @DataJpaTest} dựa vào.
 *
 * <h3>Nhập lại nhiều lần không nhân đôi thứ gì</h3>
 * Danh tính của một truyện là tên nó, của một chương là cặp (truyện, số chương)
 * — đúng ràng buộc duy nhất đã có sẵn trong lược đồ. Chạy lại cùng một gói thì
 * mọi thứ đã có được nhận ra và cập nhật, không có hàng nào sinh thêm.
 *
 * <h3>Những gì lượt nhập KHÔNG đụng tới</h3>
 * Với chương <b>đã tồn tại</b>, {@code accessLevel} và {@code coinPrice} được
 * giữ nguyên. Đổi giá một chương đã có người mua bằng Xu, hay mở khóa một chương
 * VIP, là quyết định nghiệp vụ — không phải hệ quả phụ của việc chạy lại một
 * lệnh nhập nội dung. Hai trường ấy chỉ có tác dụng lúc tạo mới.
 * Lượt xem cũng vậy: nó là dữ liệu thật, không phải thứ gói nội dung nói được.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentImporter {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;
    private final ContentValidator validator;

    /* ---------------------------------------------------------------- */
    /* Truyện                                                            */
    /* ---------------------------------------------------------------- */

    /** Tạo mới hoặc cập nhật phần mô tả của một truyện; trả về id của nó. */
    @Transactional
    public Long upsertStory(ContentPackage.Book book) {
        validator.validateBook(book);

        Author author = authorRepository.findByNameIgnoreCase(book.author().trim())
                .orElseGet(() -> authorRepository.save(
                        Author.builder().name(book.author().trim()).build()));

        Genre genre = genreRepository.findByNameIgnoreCase(book.genre().trim())
                .orElseGet(() -> genreRepository.save(
                        Genre.builder().name(book.genre().trim()).build()));

        StoryStatus status = book.status() == null || book.status().isBlank()
                ? StoryStatus.ONGOING
                : validator.parseEnum(StoryStatus.class, book.status(), "status");

        Story story = storyRepository.findFirstByTitle(book.title().trim())
                .orElseGet(() -> Story.builder().title(book.title().trim()).build());

        story.setAuthor(author);
        story.setGenre(genre);
        story.setStatus(status);
        if (book.description() != null) {
            story.setDescription(book.description());
        }
        if (book.coverImage() != null && !book.coverImage().isBlank()) {
            story.setCoverImage(book.coverImage().trim());
        }

        return storyRepository.save(story).getId();
    }

    /* ---------------------------------------------------------------- */
    /* Chương                                                            */
    /* ---------------------------------------------------------------- */

    /** Kết quả một chương, để bên gọi đếm mà không phải đoán từ ngoại lệ. */
    public enum ChapterOutcome {
        CREATED, UPDATED
    }

    @Transactional
    public ChapterOutcome upsertChapter(Long storyId, ContentPackage.Chapter source) {
        validator.validateChapter(source);

        Story story = storyRepository.getReferenceById(storyId);
        Chapter existing = chapterRepository
                .findByStoryIdAndChapterNumber(storyId, source.chapterNumber())
                .orElse(null);

        if (existing == null) {
            Chapter chapter = Chapter.builder()
                    .story(story)
                    .chapterNumber(source.chapterNumber())
                    .title(source.title().trim())
                    .content(source.content())
                    .accessLevel(source.accessLevel() == null || source.accessLevel().isBlank()
                            ? AccessLevel.PUBLIC
                            : validator.parseEnum(AccessLevel.class, source.accessLevel(), "accessLevel"))
                    .coinPrice(source.coinPrice() == null ? 0L : source.coinPrice())
                    .build();
            chapterRepository.save(chapter);
            return ChapterOutcome.CREATED;
        }

        // Chỉ nội dung. Xem javadoc của lớp về accessLevel/coinPrice/viewCount.
        existing.setTitle(source.title().trim());
        existing.setContent(source.content());
        chapterRepository.save(existing);
        return ChapterOutcome.UPDATED;
    }

    /* ---------------------------------------------------------------- */
    /* Audio                                                             */
    /* ---------------------------------------------------------------- */

    /** Chương này đã có bản thu sẵn chưa — dùng để bỏ qua ở lượt nhập lại. */
    @Transactional(readOnly = true)
    public boolean hasUploadedAudio(Long storyId, Integer chapterNumber) {
        return chapterRepository.findByStoryIdAndChapterNumber(storyId, chapterNumber)
                .flatMap(chapter -> audioFileRepository.findFirstByChapterIdAndSourceAndStatus(
                        chapter.getId(), AudioSource.UPLOAD, AudioStatus.READY))
                .isPresent();
    }

    /**
     * Đưa file audio của một chương vào nơi lưu trữ rồi ghi bản ghi cho nó.
     *
     * <p><b>Thứ tự có chủ ý, và ngược với trực giác.</b> File được đẩy lên trước,
     * bản ghi được tạo sau. Hỏng ở giữa thì để lại một file không ai trỏ tới —
     * tốn chỗ, và bị dọn ở khối catch. Làm ngược lại thì hỏng ở giữa để lại một
     * chương mang cờ READY trỏ vào hư không, tức đúng cái tình trạng mà cả đợt
     * thay đổi này sinh ra để chấm dứt.
     *
     * <p>Cờ READY chỉ được bật khi byte đã nằm ở nơi lưu trữ và băm đã khớp.
     */
    @Transactional
    public void attachAudio(Long storyId, ContentPackage.Chapter source, Path audioPath) {
        String extension = extensionOf(audioPath.getFileName().toString());

        String key;
        try (InputStream input = Files.newInputStream(audioPath)) {
            key = storageService.storeAudio(input, extension);
        } catch (IOException ex) {
            throw new ContentValidator.InvalidPackageException(
                    "Không đọc được " + audioPath.getFileName() + ": " + ex.getMessage());
        }

        try {
            Chapter chapter = chapterRepository
                    .findByStoryIdAndChapterNumber(storyId, source.chapterNumber())
                    .orElseThrow(() -> new IllegalStateException(
                            "Chương " + source.chapterNumber() + " biến mất giữa chừng"));

            audioFileRepository.save(AudioFile.builder()
                    .chapter(chapter)
                    .filePath(key)
                    .source(AudioSource.UPLOAD)
                    .status(AudioStatus.READY)
                    .contentType(contentTypeOf(extension))
                    .fileSize(sizeOf(audioPath))
                    .build());

            log.info("Đã gắn audio cho chương {} của truyện {}", source.chapterNumber(), storyId);
        } catch (RuntimeException ex) {
            storageService.deleteAudio(key);
            throw ex;
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : ".mp3";
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case ".wav" -> "audio/wav";
            case ".ogg", ".opus" -> "audio/ogg";
            case ".m4a", ".mp4" -> "audio/mp4";
            case ".aac" -> "audio/aac";
            default -> "audio/mpeg";
        };
    }
}
