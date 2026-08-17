package com.storytts.backend.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

/**
 * Nhập nội dung từ đĩa vào hệ thống — chạy như một việc riêng, không phải một
 * phần của lúc khởi động web.
 *
 * <h3>Vì sao là một profile riêng</h3>
 * Nhập nội dung là việc nặng, chạm mạng, và có thể hỏng vì một tệp JSON sai dấu
 * phẩy. Để nó trong đường khởi động bình thường nghĩa là một gói nội dung hỏng
 * làm cả trang web không lên được. Ở đây nó chỉ tồn tại khi được gọi đích danh,
 * và gọi xong thì tiến trình kết thúc:
 *
 * <pre>
 * java -jar app.jar --spring.profiles.active=import --app.import.dir=./content
 * </pre>
 *
 * <p>Mã thoát khác 0 khi có bất kỳ chương nào hỏng, để một lượt chạy trong CI
 * hay trong kịch bản triển khai không lặng lẽ đi qua một lượt nhập dở dang.
 *
 * <h3>Hình dạng thư mục</h3>
 * <pre>
 * content/
 * └── books/
 *     └── nguoi-dua-thu/
 *         ├── book.json
 *         ├── chapters/
 *         │   ├── 001.json
 *         │   └── 002.json
 *         └── audio/
 *             └── 001.mp3
 * </pre>
 */
@Component
@Profile("import")
@RequiredArgsConstructor
@Slf4j
public class ContentImportRunner implements ApplicationRunner {

    private final ContentImporter importer;
    private final ContentValidator validator;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    @Value("${app.import.dir:./content}")
    private String importDir;

    @Override
    public void run(ApplicationArguments args) {
        Path root = Paths.get(importDir).toAbsolutePath().normalize();
        Path booksDir = root.resolve("books");

        log.info("=== Nhập nội dung từ {} ===", root);

        if (!Files.isDirectory(booksDir)) {
            log.error("Không thấy thư mục {}. Xem content/README.md về hình dạng gói nội dung.", booksDir);
            exit(2);
            return;
        }

        ImportReport report = new ImportReport();
        for (Path bookDir : listDirectories(booksDir)) {
            importBook(bookDir, report);
        }

        log.info("=== Xong: {} ===", report.summary());
        if (report.hasFailures()) {
            log.error("{} chỗ hỏng:", report.getFailures().size());
            report.getFailures().forEach(failure -> log.error("  - {}", failure));
        }
        exit(report.hasFailures() ? 1 : 0);
    }

    /* ---------------------------------------------------------------- */

    private void importBook(Path bookDir, ImportReport report) {
        String name = bookDir.getFileName().toString();
        Long storyId;

        // Truyện hỏng thì bỏ qua cả thư mục ấy — không có id thì chương không
        // biết gắn vào đâu. Những truyện khác vẫn chạy tiếp.
        try {
            ContentPackage.Book book = objectMapper.readValue(
                    bookDir.resolve("book.json").toFile(), ContentPackage.Book.class);
            storyId = importer.upsertStory(book);
            report.bookImported();
            log.info("-> {} (truyện #{})", book.title(), storyId);
        } catch (IOException ex) {
            report.failed(name + "/book.json", "không đọc được: " + ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            report.failed(name + "/book.json", ex.getMessage());
            return;
        }

        Path chaptersDir = bookDir.resolve("chapters");
        if (!Files.isDirectory(chaptersDir)) {
            report.failed(name, "thiếu thư mục chapters/");
            return;
        }

        for (Path file : listJsonFiles(chaptersDir)) {
            importChapter(bookDir, file, storyId, report);
        }
    }

    /** Một chương hỏng chỉ làm hỏng chính nó — xem javadoc của {@link ImportReport}. */
    private void importChapter(Path bookDir, Path file, Long storyId, ImportReport report) {
        String where = bookDir.getFileName() + "/chapters/" + file.getFileName();
        try {
            ContentPackage.Chapter chapter =
                    objectMapper.readValue(file.toFile(), ContentPackage.Chapter.class);

            // Kiểm audio TRƯỚC khi ghi chương: một file audio thiếu hay sai băm
            // phải chặn được cả chương, chứ không để lại một chương im lặng mà
            // lượt chạy vẫn báo thành công.
            Path audio = null;
            if (chapter.audio() != null && !chapter.audio().isBlank()) {
                validator.validateChapter(chapter);
                audio = validator.resolveAudio(bookDir, chapter.audio());
                validator.verifyAudio(audio, chapter.audioSha256());
            }

            ContentImporter.ChapterOutcome outcome = importer.upsertChapter(storyId, chapter);
            if (outcome == ContentImporter.ChapterOutcome.CREATED) {
                report.chapterCreated();
            } else {
                report.chapterUpdated();
            }

            if (audio == null) {
                return;
            }
            // Nhập lại lần nữa thì không tải lên lần nữa. Muốn thay bản thu thì
            // xóa bản cũ trong khu quản trị rồi chạy lại.
            if (importer.hasUploadedAudio(storyId, chapter.chapterNumber())) {
                report.audioSkipped();
                return;
            }
            importer.attachAudio(storyId, chapter, audio);
            report.audioUploaded();

        } catch (IOException ex) {
            report.failed(where, "không đọc được: " + ex.getMessage());
        } catch (RuntimeException ex) {
            report.failed(where, ex.getMessage());
        }
    }

    /* ---------------------------------------------------------------- */

    private List<Path> listDirectories(Path parent) {
        try (var stream = Files.list(parent)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException ex) {
            log.error("Không đọc được {}: {}", parent, ex.getMessage());
            return List.of();
        }
    }

    /** Sắp theo tên để 001, 002, 010 vào đúng thứ tự người soạn mong đợi. */
    private List<Path> listJsonFiles(Path parent) {
        try (var stream = Files.list(parent)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            log.error("Không đọc được {}: {}", parent, ex.getMessage());
            return List.of();
        }
    }

    private void exit(int code) {
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
